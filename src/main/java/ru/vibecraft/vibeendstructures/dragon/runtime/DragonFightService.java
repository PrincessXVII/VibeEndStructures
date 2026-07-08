package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionSnapshot;
import ru.vibecraft.vibeendstructures.dragon.contribution.DragonContributionTracker;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonFightService {

    private static final int DRAGON_MISSING_CLEAR_SECONDS = 90;
    private static final int MAX_CHUNK_LOAD_RADIUS = 10;

    private final VibeEndStructuresPlugin plugin;
    private final DragonSpawner spawner;
    private final DragonContributionTracker contributionTracker;
    private final Set<String> pendingSpawns = ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, DragonSpawnRitual> pendingRituals = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Location> lastKnownDragonLocations = new ConcurrentHashMap<>();
    private final Map<String, Integer> arenaMissingDragonSeconds = new ConcurrentHashMap<>();
    private DragonPhaseController phaseController;

    public DragonFightService(VibeEndStructuresPlugin plugin, DragonSpawner spawner, DragonContributionTracker contributionTracker) {
        this.plugin = plugin;
        this.spawner = spawner;
        this.contributionTracker = contributionTracker;
    }

    public void setPhaseController(DragonPhaseController phaseController) {
        this.phaseController = phaseController;
    }

    public DragonSpawnResult spawn(String arenaId, String dragonTypeId, boolean force) {
        return spawn(arenaId, dragonTypeId, force, true, false);
    }

    public DragonSpawnResult spawnScheduled(String arenaId, String dragonTypeId, boolean force) {
        return spawn(arenaId, dragonTypeId, force, true, true);
    }

    public DragonSpawnResult spawnFromEgg(String arenaId, String dragonTypeId) {
        return spawn(arenaId, dragonTypeId, false, false, false);
    }

    private DragonSpawnResult spawn(String arenaId, String dragonTypeId, boolean force, boolean eggDropEligible, boolean scheduledSpawn) {
        DragonArena arena = plugin.getDragonConfig().getArena(arenaId);
        if (arena == null) {
            return DragonSpawnResult.failure("Арена не найдена: " + arenaId);
        }
        if (!arena.enabled() && !force) {
            return DragonSpawnResult.failure("Арена отключена: " + arena.id());
        }

        arena = normalizeArenaState(arena);
        if (pendingSpawns.contains(arena.id()) || arena.state() == DragonArena.ArenaState.SPAWNING) {
            return DragonSpawnResult.failure("На арене уже идёт ритуал спавна: " + arena.id());
        }
        if (arena.state() == DragonArena.ArenaState.ACTIVE) {
            return DragonSpawnResult.failure("На арене уже активен дракон: " + arena.id());
        }
        if (arena.isOnCooldown() && !force) {
            long seconds = Math.max(1, (arena.cooldownUntil() - System.currentTimeMillis()) / 1000);
            return DragonSpawnResult.failure("Арена на cooldown ещё " + seconds + " сек.");
        }

        String effectiveType = dragonTypeId == null || dragonTypeId.isBlank() ? arena.dragonTypeId() : dragonTypeId;
        DragonDefinition definition = plugin.getDragonConfig().getDragon(effectiveType);
        if (definition == null) {
            return DragonSpawnResult.failure("Тип дракона не найден: " + effectiveType);
        }
        if (!definition.enabled() && !force) {
            return DragonSpawnResult.failure("Тип дракона отключён: " + definition.id());
        }

        World world = resolveDragonWorld().orElse(null);
        if (world == null) {
            return DragonSpawnResult.failure("Не найден мир для спавна дракона. Проверь config.yml worlds.");
        }

        DragonArena spawningArena = arena.withSpawning();
        pendingSpawns.add(arena.id());
        plugin.getDragonConfig().updateArena(spawningArena);
        DragonSpawnRitual ritual = spawner.spawnWithRitual(world, spawningArena, definition, eggDropEligible, scheduledSpawn, dragon -> finishSpawn(spawningArena, definition, dragon));
        pendingRituals.put(arena.id(), ritual);

        if (plugin.getDragonConfig().getGeneralConfig().announceSpawn()) {
            Bukkit.broadcastMessage(color(plugin.getDragonConfig().getGeneralConfig().spawnMessage()
                    .replace("%dragon%", definition.displayName())
                    .replace("%arena%", arena.name())));
        }

        return DragonSpawnResult.success("Ритуал спавна дракона " + definition.id() + " запущен на арене " + arena.id(), null);
    }

    public DragonSpawnResult despawn(String arenaId) {
        DragonArena arena = plugin.getDragonConfig().getArena(arenaId);
        if (arena == null) {
            return DragonSpawnResult.failure("Арена не найдена: " + arenaId);
        }

        boolean removedSomething = cancelPendingRitual(arena.id());
        if (arena.activeDragonUuid() != null) {
            EnderDragon dragon = findDragon(arena.activeDragonUuid(), arena).orElse(null);
            if (dragon != null) {
                dragon.remove();
                removedSomething = true;
            }
            if (phaseController != null) {
                phaseController.untrack(arena.activeDragonUuid());
            }
        }

        contributionTracker.clearActiveFight(arena.id());
        cleanupArenaEntities(arena);
        if (arena.activeDragonUuid() != null) {
            forgetDragonLocation(arena.activeDragonUuid());
        }
        arenaMissingDragonSeconds.remove(arena.id());
        plugin.getDragonConfig().updateArena(arena.withClearedCooldown());
        return removedSomething
                ? DragonSpawnResult.success("Дракон/ритуал на арене " + arena.id() + " удалён.", null)
                : DragonSpawnResult.success("Арена " + arena.id() + " очищена, активного дракона не было.", null);
    }

    public ContributionSnapshot completeFight(String arenaId, boolean cooldown) {
        DragonArena arena = plugin.getDragonConfig().getArena(arenaId);
        if (arena == null) {
            return ContributionSnapshot.empty(arenaId, "");
        }
        // Capture contribution BEFORE cleanup/state updates so rewards cannot race
        // with checkActiveArenas() seeing a "missing" dead dragon entity.
        ContributionSnapshot snapshot = contributionTracker.finishFight(arenaId);
        pendingSpawns.remove(arenaId);
        pendingRituals.remove(arenaId);
        if (phaseController != null && arena.activeDragonUuid() != null) {
            phaseController.untrack(arena.activeDragonUuid());
        }
        if (arena.activeDragonUuid() != null) {
            forgetDragonLocation(arena.activeDragonUuid());
        }
        arenaMissingDragonSeconds.remove(arenaId);
        cleanupArenaEntities(arena);
        resolveDragonWorld().ifPresent(spawner::suppressVanillaBattle);
        plugin.getDragonConfig().updateArena(cooldown ? arena.withCooldown() : arena.withClearedCooldown());
        return snapshot;
    }

    public void checkActiveArenas() {
        for (DragonArena arena : plugin.getDragonConfig().getArenas().values()) {
            if (arena.state() == DragonArena.ArenaState.COOLDOWN && !arena.isOnCooldown()) {
                plugin.getDragonConfig().updateArena(arena.withClearedCooldown());
                continue;
            }
            if (arena.state() == DragonArena.ArenaState.SPAWNING) {
                if (!pendingSpawns.contains(arena.id())) {
                    cancelPendingRitual(arena.id());
                    contributionTracker.clearActiveFight(arena.id());
                    plugin.getDragonConfig().updateArena(arena.withClearedCooldown());
                }
                continue;
            }
            if (arena.state() != DragonArena.ArenaState.ACTIVE) {
                continue;
            }
            if (arena.activeDragonUuid() == null) {
                contributionTracker.clearActiveFight(arena.id());
                plugin.getDragonConfig().updateArena(arena.withClearedCooldown());
                continue;
            }
            Optional<EnderDragon> dragon = findDragon(arena.activeDragonUuid(), arena);
            if (dragon.isPresent()) {
                arenaMissingDragonSeconds.remove(arena.id());
                continue;
            }
            int missingSeconds = arenaMissingDragonSeconds.merge(arena.id(), 1, Integer::sum);
            if (missingSeconds < DRAGON_MISSING_CLEAR_SECONDS) {
                continue;
            }
            plugin.getLogger().info("Arena " + arena.id() + ": active dragon unavailable for "
                    + missingSeconds + "s — clearing fight state.");
            arenaMissingDragonSeconds.remove(arena.id());
            forgetDragonLocation(arena.activeDragonUuid());
            contributionTracker.clearActiveFight(arena.id());
            plugin.getDragonConfig().updateArena(arena.withClearedCooldown());
            if (phaseController != null) {
                phaseController.untrack(arena.activeDragonUuid());
            }
        }
        recoverOrphanedDragons();
    }

    public void rememberDragonLocation(EnderDragon dragon) {
        if (dragon == null || !dragon.isValid()) {
            return;
        }
        lastKnownDragonLocations.put(dragon.getUniqueId(), dragon.getLocation().clone());
    }

    public void forgetDragonLocation(UUID dragonUuid) {
        if (dragonUuid != null) {
            lastKnownDragonLocations.remove(dragonUuid);
        }
    }

    public Optional<EnderDragon> findDragon(UUID uuid) {
        return findDragon(uuid, null);
    }

    public Optional<EnderDragon> findDragon(UUID uuid, DragonArena arena) {
        Optional<EnderDragon> loaded = findLoadedDragon(uuid);
        if (loaded.isPresent()) {
            return loaded;
        }
        World world = resolveDragonWorld().orElse(null);
        if (world == null) {
            return Optional.empty();
        }

        Location hint = lastKnownDragonLocations.get(uuid);
        if (hint != null && hint.getWorld() != null) {
            loadChunksAround(world, hint.getBlockX(), hint.getBlockZ(), 3);
            loaded = findLoadedDragon(uuid);
            if (loaded.isPresent()) {
                return loaded;
            }
        }

        if (arena != null) {
            loadChunksAround(world, arena.centerX(), arena.centerZ(), chunkRadiusForArena(arena));
            loaded = findLoadedDragon(uuid);
            if (loaded.isPresent()) {
                return loaded;
            }
            return findDragonByArena(world, arena.id());
        }
        return Optional.empty();
    }

    private Optional<EnderDragon> findLoadedDragon(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof EnderDragon dragon && dragon.isValid() && !dragon.isDead()) {
            return Optional.of(dragon);
        }
        return Optional.empty();
    }

    private Optional<EnderDragon> findDragonByArena(World world, String arenaId) {
        String tag = "vibedragon:arena:" + arenaId;
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            if (!dragon.isValid() || dragon.isDead()) {
                continue;
            }
            if (dragon.getScoreboardTags().contains(tag)) {
                return Optional.of(dragon);
            }
        }
        return Optional.empty();
    }

    private void recoverOrphanedDragons() {
        World world = resolveDragonWorld().orElse(null);
        if (world == null || phaseController == null) {
            return;
        }
        for (DragonArena arena : plugin.getDragonConfig().getArenas().values()) {
            if (!arena.enabled() || arena.state() == DragonArena.ArenaState.SPAWNING) {
                continue;
            }
            if (arena.state() == DragonArena.ArenaState.ACTIVE) {
                continue;
            }
            if (arena.isOnCooldown()) {
                continue;
            }
            Optional<EnderDragon> dragon = findDragonByArena(world, arena.id());
            if (dragon.isEmpty()) {
                loadChunksAround(world, arena.centerX(), arena.centerZ(), chunkRadiusForArena(arena));
                dragon = findDragonByArena(world, arena.id());
            }
            if (dragon.isEmpty()) {
                continue;
            }
            EnderDragon found = dragon.get();
            plugin.getLogger().info("Recovered orphaned dragon for arena " + arena.id()
                    + " at " + formatLocation(found.getLocation()));
            DragonDefinition definition = plugin.getDragonConfig().getDragon(arena.dragonTypeId());
            if (definition == null) {
                continue;
            }
            DragonArena activeArena = arena.withActiveDragon(found.getUniqueId());
            plugin.getDragonConfig().updateArena(activeArena);
            contributionTracker.startFight(activeArena, definition);
            phaseController.track(found, activeArena, definition);
            rememberDragonLocation(found);
            arenaMissingDragonSeconds.remove(arena.id());
        }
    }

    private int chunkRadiusForArena(DragonArena arena) {
        return Math.min(MAX_CHUNK_LOAD_RADIUS, Math.max(2, (arena.radius() + 64) / 16));
    }

    private void loadChunksAround(World world, int blockX, int blockZ, int chunkRadius) {
        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;
        int radius = Math.max(1, Math.min(MAX_CHUNK_LOAD_RADIUS, chunkRadius));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                if (!chunk.isLoaded()) {
                    chunk.load(true);
                }
            }
        }
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + " "
                + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private DragonArena normalizeArenaState(DragonArena arena) {
        if (arena.state() == DragonArena.ArenaState.SPAWNING && !pendingSpawns.contains(arena.id())) {
            DragonArena cleared = arena.withClearedCooldown();
            cancelPendingRitual(arena.id());
            contributionTracker.clearActiveFight(arena.id());
            plugin.getDragonConfig().updateArena(cleared);
            return cleared;
        }
        if (arena.state() == DragonArena.ArenaState.COOLDOWN && !arena.isOnCooldown()) {
            DragonArena cleared = arena.withClearedCooldown();
            plugin.getDragonConfig().updateArena(cleared);
            return cleared;
        }
        if (arena.state() == DragonArena.ArenaState.ACTIVE && arena.activeDragonUuid() != null) {
            return arena;
        }
        if (arena.state() == DragonArena.ArenaState.ACTIVE && arena.activeDragonUuid() == null) {
            DragonArena cleared = arena.withClearedCooldown();
            contributionTracker.clearActiveFight(arena.id());
            plugin.getDragonConfig().updateArena(cleared);
            return cleared;
        }
        return arena;
    }

    private void finishSpawn(DragonArena arena, DragonDefinition definition, EnderDragon dragon) {
        if (!pendingSpawns.remove(arena.id())) {
            dragon.remove();
            cleanupArenaEntities(arena);
            return;
        }
        pendingRituals.remove(arena.id());
        spawner.suppressVanillaBattle(dragon.getWorld());
        DragonArena activeArena = arena.withActiveDragon(dragon.getUniqueId());
        plugin.getDragonConfig().updateArena(activeArena);
        contributionTracker.startFight(activeArena, definition);
        plugin.getDragonScheduleService().markFirstDragonSpawned();
        if (phaseController != null) {
            phaseController.track(dragon, activeArena, definition);
        }
        rememberDragonLocation(dragon);
        arenaMissingDragonSeconds.remove(arena.id());
    }

    private void cleanupArenaEntities(DragonArena arena) {
        World world = resolveDragonWorld().orElse(null);
        if (world == null) {
            return;
        }
        spawner.suppressVanillaBattle(world);
        Location center = new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
        double range = arena.radius() + 32.0;
        for (Entity entity : world.getNearbyEntities(center, range, range, range)) {
            // Never wipe the live boss itself — arena tag is also on the dragon, and
            // call sites include finishSpawn / completeFight where the dragon must remain
            // until it naturally dies (or is despawned explicitly).
            if (entity instanceof EnderDragon) {
                continue;
            }
            if (entity.getScoreboardTags().contains("vibedragon:minion")
                    || entity.getScoreboardTags().contains("vibedragon:meteor")
                    || entity.getScoreboardTags().contains("vibedragon:ritual")
                    || entity.getScoreboardTags().contains("vibedragon:ritual:" + arena.id())) {
                entity.remove();
            }
        }
    }

    private boolean cancelPendingRitual(String arenaId) {
        pendingSpawns.remove(arenaId);
        DragonSpawnRitual ritual = pendingRituals.remove(arenaId);
        if (ritual == null) {
            return false;
        }
        ritual.cancel();
        return true;
    }

    public Optional<World> resolveDragonWorld() {
        for (String worldName : plugin.getPluginConfig().getWorlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                return Optional.of(world);
            }
        }
        return Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.THE_END)
                .min(Comparator.comparing(World::getName));
    }

    public boolean hasOngoingDragonEncounter() {
        if (!pendingSpawns.isEmpty()) {
            return true;
        }
        for (DragonArena arena : plugin.getDragonConfig().getArenas().values()) {
            if (!arena.enabled()) {
                continue;
            }
            if (arena.state() == DragonArena.ArenaState.SPAWNING) {
                return true;
            }
            if (arena.state() == DragonArena.ArenaState.ACTIVE && arena.activeDragonUuid() != null) {
                if (findDragon(arena.activeDragonUuid(), arena).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String color(String message) {
        return message.replace('&', '§');
    }
}
