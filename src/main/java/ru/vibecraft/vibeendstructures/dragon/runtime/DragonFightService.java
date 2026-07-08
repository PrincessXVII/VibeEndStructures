package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Bukkit;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonFightService {

    private final VibeEndStructuresPlugin plugin;
    private final DragonSpawner spawner;
    private final DragonContributionTracker contributionTracker;
    private final Set<String> pendingSpawns = ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, DragonSpawnRitual> pendingRituals = new java.util.concurrent.ConcurrentHashMap<>();
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
        return spawn(arenaId, dragonTypeId, force, true);
    }

    public DragonSpawnResult spawnFromEgg(String arenaId, String dragonTypeId) {
        return spawn(arenaId, dragonTypeId, false, false);
    }

    private DragonSpawnResult spawn(String arenaId, String dragonTypeId, boolean force, boolean eggDropEligible) {
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
        DragonSpawnRitual ritual = spawner.spawnWithRitual(world, spawningArena, definition, eggDropEligible, dragon -> finishSpawn(spawningArena, definition, dragon));
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
            EnderDragon dragon = findDragon(arena.activeDragonUuid()).orElse(null);
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
        pendingSpawns.remove(arenaId);
        pendingRituals.remove(arenaId);
        if (phaseController != null && arena.activeDragonUuid() != null) {
            phaseController.untrack(arena.activeDragonUuid());
        }
        ContributionSnapshot snapshot = contributionTracker.finishFight(arenaId);
        cleanupArenaEntities(arena);
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
            if (arena.activeDragonUuid() == null || findDragon(arena.activeDragonUuid()).isEmpty()) {
                contributionTracker.clearActiveFight(arena.id());
                plugin.getDragonConfig().updateArena(arena.withClearedCooldown());
                if (arena.activeDragonUuid() != null && phaseController != null) {
                    phaseController.untrack(arena.activeDragonUuid());
                }
            }
        }
    }

    public Optional<EnderDragon> findDragon(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof EnderDragon dragon && dragon.isValid() && !dragon.isDead()) {
            return Optional.of(dragon);
        }
        return Optional.empty();
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
        if (arena.state() == DragonArena.ArenaState.ACTIVE
                && (arena.activeDragonUuid() == null || findDragon(arena.activeDragonUuid()).isEmpty())) {
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
        DragonArena activeArena = arena.withActiveDragon(dragon.getUniqueId());
        plugin.getDragonConfig().updateArena(activeArena);
        contributionTracker.startFight(activeArena, definition);
        if (phaseController != null) {
            phaseController.track(dragon, activeArena, definition);
        }
    }

    private void cleanupArenaEntities(DragonArena arena) {
        World world = resolveDragonWorld().orElse(null);
        if (world == null) {
            return;
        }
        Location center = new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
        double range = arena.radius() + 32.0;
        for (Entity entity : world.getNearbyEntities(center, range, range, range)) {
            if (entity.getScoreboardTags().contains("vibedragon:minion")
                    || entity.getScoreboardTags().contains("vibedragon:arena:" + arena.id())
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

    private String color(String message) {
        return message.replace('&', '§');
    }
}
