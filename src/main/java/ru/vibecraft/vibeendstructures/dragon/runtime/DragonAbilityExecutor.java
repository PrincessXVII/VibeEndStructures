package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.DragonAbility;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonAbilityExecutor {

    private static final long PRESSURE_WINDOW_MILLIS = 12_000L;

    private final VibeEndStructuresPlugin plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<HitRecord>> recentHits = new ConcurrentHashMap<>();

    public DragonAbilityExecutor(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick(EnderDragon dragon, DragonArena arena, DragonDefinition definition, List<DragonAbility> abilities) {
        if (abilities.isEmpty()) {
            return;
        }
        List<DragonAbility> effectiveAbilities = abilities;
        double healthPercent = Math.max(0.0, Math.min(1.0, dragon.getHealth() / definition.health()));
        if (healthPercent <= 0.3
                && (definition.type() == DragonType.FIRE || definition.type() == DragonType.ICE)
                && !abilities.contains(DragonAbility.METEOR_STRIKE)) {
            effectiveAbilities = new ArrayList<>(abilities);
            effectiveAbilities.add(DragonAbility.METEOR_STRIKE);
        }
        long now = System.currentTimeMillis();
        trimOldHits(dragon.getUniqueId(), now);
        for (DragonAbility ability : effectiveAbilities) {
            String key = dragon.getUniqueId() + ":" + ability.name();
            long readyAt = cooldowns.getOrDefault(key, 0L);
            if (readyAt > now) {
                continue;
            }
            execute(dragon, arena, definition, ability);
            cooldowns.put(key, now + cooldownFor(ability));
            return;
        }
    }

    public void clear(UUID dragonUuid) {
        String prefix = dragonUuid + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        recentHits.remove(dragonUuid);
    }

    public void recordHit(UUID dragonUuid, UUID attackerUuid, double damage) {
        if (dragonUuid == null || attackerUuid == null || damage <= 0) {
            return;
        }
        Deque<HitRecord> queue = recentHits.computeIfAbsent(dragonUuid, ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        queue.addLast(new HitRecord(now, attackerUuid, damage));
        trimOldHits(dragonUuid, now);
    }

    private void execute(EnderDragon dragon, DragonArena arena, DragonDefinition definition, DragonAbility ability) {
        switch (ability) {
            case CHARGE -> charge(dragon, arena);
            case DRAGON_BREATH -> breath(dragon, arena, Particle.DRAGON_BREATH, 5.0, null);
            case FIRE_BREATH -> breath(dragon, arena, Particle.FLAME, 6.0, player ->
                    player.setFireTicks(Math.max(player.getFireTicks(), 80)));
            case ICE_BREATH -> breath(dragon, arena, Particle.SNOWFLAKE, 4.0, player ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, true, true)));
            case METEOR_STRIKE -> meteorStrike(dragon, arena, definition);
            case SUMMON_ENDERMITE -> summonEndermites(dragon);
            case FROST_NOVA -> frostNova(dragon, arena);
            case PHASE_SHIFT -> phaseShift(dragon, arena);
            case LAVA_POOL -> lavaPool(dragon, arena);
            case FLARE_BURST -> flareBurst(dragon, arena);
            case IGNITE_AREA -> igniteArea(dragon, arena);
            case BLIZZARD -> blizzard(dragon, arena);
            case ICE_SPIKES -> iceSpikes(dragon, arena);
            case FREEZE_AREA -> freezeArea(dragon, arena);
        }
    }

    private void charge(EnderDragon dragon, DragonArena arena) {
        Player target = nearestPlayer(dragon, arena.radius()).orElse(null);
        if (target == null) {
            return;
        }
        Vector direction = target.getLocation().toVector().subtract(dragon.getLocation().toVector());
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        dragon.setVelocity(direction.normalize().multiply(1.8).setY(0.15));
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3.0f, 0.7f);
    }

    private void breath(EnderDragon dragon, DragonArena arena, Particle particle, double damage, PlayerEffect effect) {
        World world = dragon.getWorld();
        Location origin = dragon.getLocation().add(0, 2.0, 0);
        List<Player> targets = nearbyPlayers(dragon, arena.radius()).stream()
                .filter(player -> player.getLocation().distanceSquared(origin) <= 35 * 35)
                .toList();
        DragonParticles.spawn(plugin, world, particle, origin, 80, 5.0, 2.0, 5.0, 0.02);
        world.playSound(origin, Sound.ENTITY_ENDER_DRAGON_SHOOT, SoundCategory.HOSTILE, 2.0f, 1.0f);
        for (Player player : targets) {
            player.damage(damage, dragon);
            if (effect != null) {
                effect.apply(player);
            }
        }
    }

    private void summonEndermites(EnderDragon dragon) {
        World world = dragon.getWorld();
        Location base = dragon.getLocation();
        for (int i = 0; i < 3; i++) {
            double angle = (Math.PI * 2 / 3) * i;
            Location spawn = base.clone().add(Math.cos(angle) * 3.0, -1.0, Math.sin(angle) * 3.0);
            world.spawn(spawn, Endermite.class, mite -> {
                mite.setPersistent(false);
                mite.setRemoveWhenFarAway(true);
                mite.addScoreboardTag("vibedragon:minion");
            });
        }
        DragonParticles.spawn(plugin, world, Particle.PORTAL, base, 80, 3.0, 1.0, 3.0, 0.2);
    }

    private void frostNova(EnderDragon dragon, DragonArena arena) {
        Location origin = dragon.getLocation();
        DragonParticles.spawn(plugin, dragon.getWorld(), Particle.SNOWFLAKE, origin, 100, 10.0, 2.5, 10.0, 0.03);
        dragon.getWorld().playSound(origin, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
        for (Player player : nearbyPlayers(dragon, Math.min(arena.radius(), 18))) {
            player.damage(5.0, dragon);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 2, true, true));
        }
    }

    private void phaseShift(EnderDragon dragon, DragonArena arena) {
        Location origin = dragon.getLocation();
        Location destination = randomArenaLocation(dragon.getWorld(), arena, origin, 14.0, 28.0);
        if (destination == null) {
            return;
        }
        DragonParticles.spawn(plugin, origin.getWorld(), Particle.PORTAL, origin.clone().add(0, 2.0, 0), 60, 2.5, 2.0, 2.5, 0.4);
        DragonParticles.spawn(plugin, origin.getWorld(), Particle.END_ROD, origin.clone().add(0, 2.5, 0), 24, 1.5, 1.5, 1.5, 0.05);
        origin.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 2.5f, 0.5f);
        dragon.teleport(destination);
        dragon.setVelocity(new Vector(0, 0.2, 0));
        DragonParticles.spawn(plugin, destination.getWorld(), Particle.PORTAL, destination.clone().add(0, 2.0, 0), 60, 2.5, 2.0, 2.5, 0.4);
        DragonParticles.spawn(plugin, destination.getWorld(), Particle.DRAGON_BREATH, destination.clone().add(0, 2.0, 0), 40, 3.0, 1.5, 3.0, 0.03);
        destination.getWorld().playSound(destination, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 2.0f, 0.7f);
    }

    private void lavaPool(EnderDragon dragon, DragonArena arena) {
        Location center = targetLocation(dragon, arena);
        World world = dragon.getWorld();
        List<BlockSwap> swaps = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) {
                    continue;
                }
                Block floor = world.getHighestBlockAt(center.getBlockX() + dx, center.getBlockZ() + dz);
                Material type = floor.getType();
                if (type.isAir() || type == Material.BEDROCK || type == Material.END_PORTAL) {
                    continue;
                }
                swaps.add(new BlockSwap(floor, floor.getBlockData().clone()));
                floor.setType(Material.MAGMA_BLOCK, false);
            }
        }
        DragonParticles.spawn(plugin, world, Particle.LAVA, center, 20, 2.5, 0.5, 2.5, 0.0);
        world.playSound(center, Sound.BLOCK_LAVA_POP, SoundCategory.HOSTILE, 2.0f, 0.6f);
        damageNearby(center, dragon, 6.0, 5.0, player -> player.setFireTicks(Math.max(player.getFireTicks(), 60)));
        scheduleBlockRevert(swaps, 20L * 7);
    }

    private void flareBurst(EnderDragon dragon, DragonArena arena) {
        Location origin = dragon.getLocation().add(0, 2.0, 0);
        World world = dragon.getWorld();
        DragonParticles.spawn(plugin, world, Particle.FLAME, origin, 120, 8.0, 3.0, 8.0, 0.06);
        DragonParticles.spawn(plugin, world, Particle.SMOKE, origin, 40, 6.0, 2.0, 6.0, 0.04);
        world.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.5f, 0.5f);
        damageNearby(origin, dragon, 10.0, 7.0, player -> player.setFireTicks(Math.max(player.getFireTicks(), 100)));
    }

    private void igniteArea(EnderDragon dragon, DragonArena arena) {
        Location center = targetLocation(dragon, arena);
        World world = dragon.getWorld();
        List<Block> fireBlocks = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block floor = world.getHighestBlockAt(center.getBlockX() + dx, center.getBlockZ() + dz);
                Block above = floor.getRelative(0, 1, 0);
                if (above.getType().isAir()) {
                    above.setType(Material.FIRE, false);
                    fireBlocks.add(above);
                }
            }
        }
        DragonParticles.spawn(plugin, world, Particle.FLAME, center, 50, 3.0, 1.0, 3.0, 0.04);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 2.0f, 0.8f);
        damageNearby(center, dragon, 7.0, 3.0, player -> player.setFireTicks(Math.max(player.getFireTicks(), 80)));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Block fire : fireBlocks) {
                if (fire.getType() == Material.FIRE) {
                    fire.setType(Material.AIR, false);
                }
            }
        }, 20L * 7);
    }

    private void blizzard(EnderDragon dragon, DragonArena arena) {
        Location origin = dragon.getLocation();
        World world = dragon.getWorld();
        DragonParticles.spawn(plugin, world, Particle.SNOWFLAKE, origin, 160, 14.0, 4.0, 14.0, 0.04);
        DragonParticles.spawn(plugin, world, Particle.CLOUD, origin, 40, 10.0, 2.0, 10.0, 0.02);
        world.playSound(origin, Sound.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 2.0f, 0.7f);
        for (Player player : nearbyPlayers(dragon, Math.min(arena.radius(), 20))) {
            player.damage(4.0, dragon);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, true));
        }
    }

    private void iceSpikes(EnderDragon dragon, DragonArena arena) {
        Location center = targetLocation(dragon, arena);
        World world = dragon.getWorld();
        List<BlockSwap> swaps = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2 / 6) * i + Math.random() * 0.4;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * (3.0 + Math.random() * 2.0));
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * (3.0 + Math.random() * 2.0));
            Block floor = world.getHighestBlockAt(x, z);
            Block spike = floor.getRelative(0, 1, 0);
            if (!spike.getType().isAir() || floor.getType().isAir() || floor.getType() == Material.BEDROCK) {
                continue;
            }
            swaps.add(new BlockSwap(spike, spike.getBlockData().clone()));
            spike.setType(Material.PACKED_ICE, false);
            if (Math.random() < 0.45 && spike.getRelative(0, 1, 0).getType().isAir()) {
                Block top = spike.getRelative(0, 1, 0);
                swaps.add(new BlockSwap(top, top.getBlockData().clone()));
                top.setType(Material.PACKED_ICE, false);
            }
        }
        DragonParticles.spawn(plugin, world, Particle.SNOWFLAKE, center, 60, 5.0, 2.0, 5.0, 0.03);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 1.2f);
        damageNearby(center, dragon, 6.0, 6.0, player ->
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, true, true)));
        scheduleBlockRevert(swaps, 20L * 6);
    }

    private void freezeArea(EnderDragon dragon, DragonArena arena) {
        Location center = targetLocation(dragon, arena);
        World world = dragon.getWorld();
        List<BlockSwap> swaps = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block floor = world.getHighestBlockAt(center.getBlockX() + dx, center.getBlockZ() + dz);
                Material original = floor.getType();
                if (original.isAir() || original == Material.BEDROCK || original == Material.END_PORTAL || original == Material.ICE) {
                    continue;
                }
                swaps.add(new BlockSwap(floor, floor.getBlockData().clone()));
                floor.setType(Material.SNOW_BLOCK, false);
            }
        }
        DragonParticles.spawn(plugin, world, Particle.SNOWFLAKE, center, 80, 4.0, 1.5, 4.0, 0.03);
        world.playSound(center, Sound.BLOCK_POWDER_SNOW_STEP, SoundCategory.HOSTILE, 2.0f, 0.6f);
        damageNearby(center, dragon, 7.0, 4.0, player -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 5, 2, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 4, 0, true, true));
        });
        scheduleBlockRevert(swaps, 20L * 7);
    }

    private Location targetLocation(EnderDragon dragon, DragonArena arena) {
        return nearestPlayer(dragon, arena.radius())
                .map(Player::getLocation)
                .orElse(dragon.getLocation());
    }

    private Location randomArenaLocation(World world, DragonArena arena, Location origin, double minDistance, double maxDistance) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = Math.random() * Math.PI * 2.0;
            double distance = minDistance + Math.random() * (maxDistance - minDistance);
            int x = arena.centerX() + (int) Math.round(Math.cos(angle) * distance);
            int z = arena.centerZ() + (int) Math.round(Math.sin(angle) * distance);
            Block floor = world.getHighestBlockAt(x, z);
            if (floor.getType().isAir() || floor.getType() == Material.BEDROCK) {
                continue;
            }
            Location candidate = floor.getLocation().add(0.5, 3.0, 0.5);
            if (candidate.distanceSquared(origin) < minDistance * minDistance) {
                continue;
            }
            return candidate;
        }
        return origin.clone().add(0, 2.0, 0);
    }

    private void damageNearby(Location center, EnderDragon dragon, double radius, double damage, PlayerEffect effect) {
        double radiusSquared = radius * radius;
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            player.damage(damage, dragon);
            if (effect != null) {
                effect.apply(player);
            }
        }
    }

    private void scheduleBlockRevert(List<BlockSwap> swaps, long delayTicks) {
        if (swaps.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (BlockSwap swap : swaps) {
                if (swap.block().getWorld() != null) {
                    swap.block().setBlockData(swap.original(), false);
                }
            }
        }, delayTicks);
    }

    private void meteorStrike(EnderDragon dragon, DragonArena arena, DragonDefinition definition) {
        if (definition.type() != DragonType.FIRE && definition.type() != DragonType.ICE) {
            return;
        }
        double healthPercent = Math.max(0.0, Math.min(1.0, dragon.getHealth() / definition.health()));
        if (healthPercent > 0.3) {
            return;
        }

        int meteorCount = meteorCount(dragon.getUniqueId());
        for (int i = 0; i < meteorCount; i++) {
            Location target = randomMeteorTarget(dragon.getWorld(), arena);
            if (target == null) {
                continue;
            }
            launchMeteor(dragon, target, definition.type(), i * 6L);
        }
    }

    private int meteorCount(UUID dragonUuid) {
        long now = System.currentTimeMillis();
        trimOldHits(dragonUuid, now);
        Deque<HitRecord> queue = recentHits.get(dragonUuid);
        if (queue == null || queue.isEmpty()) {
            return 2;
        }
        int hits = queue.size();
        double damage = 0.0;
        Map<UUID, Integer> unique = new HashMap<>();
        for (HitRecord hit : queue) {
            damage += hit.damage();
            unique.put(hit.attackerUuid(), 1);
        }
        double pressure = (hits / 18.0) + (unique.size() / 6.0) + (damage / 220.0);
        int scaled = 2 + (int) Math.round(Math.min(1.5, pressure) * 3.0);
        return Math.max(2, Math.min(6, scaled));
    }

    private void launchMeteor(EnderDragon dragon, Location target, DragonType type, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!dragon.isValid() || dragon.isDead()) {
                return;
            }
            World world = dragon.getWorld();
            MeteorSite site = resolveMeteorSite(world, target);
            Location surfaceCenter = site.cubeCenterAtSurface();
            Location buryCenter = site.cubeCenterAtY(Math.max(world.getMinHeight() + 1.5, 1.5));
            Location start = meteorStart(surfaceCenter, dragon.getLocation());
            Material block = type == DragonType.FIRE ? Material.MAGMA_BLOCK : Material.ICE;
            BlockDisplay visual = DragonMeteorDisplay.spawn(world, start, block);
            if (visual == null) {
                applyMeteorImpact(world, site.surfaceImpact(), dragon, type);
                return;
            }

            int approachSteps = 48;
            int burySteps = Math.max(28, (int) Math.ceil((surfaceCenter.getY() - buryCenter.getY()) / 0.45));

            final boolean[] impacted = {false};
            final boolean[] cleaned = {false};
            final int[] tick = {0};
            final BukkitTask[] taskRef = new BukkitTask[1];

            Runnable cleanup = () -> {
                if (cleaned[0]) {
                    return;
                }
                cleaned[0] = true;
                if (taskRef[0] != null) {
                    taskRef[0].cancel();
                }
                DragonMeteorDisplay.remove(visual);
            };

            taskRef[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                tick[0]++;
                if (tick[0] <= approachSteps) {
                    double progress = tick[0] / (double) approachSteps;
                    Location current = lerpLocation(start, surfaceCenter, easeInCubic(progress));
                    DragonMeteorDisplay.move(visual, current);
                    if (tick[0] % 3 == 0) {
                        spawnMeteorTrail(world, current, type);
                    }
                    return;
                }

                if (!impacted[0]) {
                    impacted[0] = true;
                    DragonMeteorDisplay.move(visual, surfaceCenter.clone());
                    applyMeteorImpact(world, site.surfaceImpact(), dragon, type);
                }

                int buryTick = tick[0] - approachSteps;
                if (buryTick <= burySteps) {
                    double progress = buryTick / (double) burySteps;
                    Location current = lerpLocation(surfaceCenter, buryCenter, easeInQuad(progress));
                    DragonMeteorDisplay.move(visual, current);
                    if (buryTick % 3 == 0) {
                        spawnMeteorTrail(world, current, type);
                    }
                    if (buryTick == burySteps) {
                        cleanup.run();
                    }
                    return;
                }

                cleanup.run();
            }, 0L, 1L);

            long safetyTicks = approachSteps + burySteps + 20L;
            plugin.getServer().getScheduler().runTaskLater(plugin, cleanup, safetyTicks);
        }, delayTicks);
    }

    private void spawnMeteorTrail(World world, Location current, DragonType type) {
        Particle trail = type == DragonType.FIRE ? Particle.FLAME : Particle.SNOWFLAKE;
        DragonParticles.spawn(plugin, world, trail, current.clone(), 8, 0.8, 0.8, 0.8, 0.02);
        if (type == DragonType.FIRE && current.getBlockY() % 2 == 0) {
            DragonParticles.spawn(plugin, world, Particle.SMOKE, current.clone(), 4, 0.4, 0.4, 0.4, 0.01);
        }
    }

    private MeteorSite resolveMeteorSite(World world, Location target) {
        int blockX = target.getBlockX();
        int blockZ = target.getBlockZ();
        Block floor = world.getHighestBlockAt(blockX, blockZ);
        return new MeteorSite(world, blockX, blockZ, floor);
    }

    private Location meteorStart(Location surfaceCenter, Location dragonLocation) {
        Vector away = surfaceCenter.toVector().subtract(dragonLocation.toVector());
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 1);
        }
        Vector horizontal = away.clone().setY(0);
        if (horizontal.lengthSquared() < 0.01) {
            horizontal = new Vector(1, 0, 0);
        }
        horizontal.normalize().multiply(24.0);
        Vector side = new Vector(-horizontal.getZ(), 0, horizontal.getX()).normalize().multiply(16.0);
        return surfaceCenter.clone().add(horizontal).add(side).add(0, 30.0, 0);
    }

    private void applyMeteorImpact(World world, Location target, EnderDragon dragon, DragonType type) {
        DragonParticles.spawn(plugin, world, Particle.EXPLOSION_EMITTER, target, 1);
        DragonParticles.spawn(plugin, world, Particle.END_ROD, target, 60, 2.0, 1.0, 2.0, 0.07);
        world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.8f, 0.8f);

        if (type == DragonType.FIRE) {
            applyFireImpact(world, target, dragon);
            return;
        }
        applyIceImpact(world, target, dragon);
    }

    private void applyFireImpact(World world, Location target, EnderDragon dragon) {
        List<Block> fireBlocks = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block floor = world.getHighestBlockAt(target.getBlockX() + dx, target.getBlockZ() + dz);
                Block above = floor.getLocation().add(0, 1, 0).getBlock();
                if (above.getType().isAir()) {
                    above.setType(Material.FIRE, false);
                    fireBlocks.add(above);
                }
            }
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(target) <= 8 * 8) {
                player.setFireTicks(Math.max(player.getFireTicks(), 120));
                player.damage(4.0, dragon);
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Block fire : fireBlocks) {
                if (fire.getType() == Material.FIRE) {
                    fire.setType(Material.AIR, false);
                }
            }
        }, 20L * 7);
    }

    private void applyIceImpact(World world, Location target, EnderDragon dragon) {
        List<BlockSwap> swaps = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block floor = world.getHighestBlockAt(target.getBlockX() + dx, target.getBlockZ() + dz);
                Material original = floor.getType();
                if (!original.isAir() && original != Material.BEDROCK && original != Material.END_PORTAL) {
                    swaps.add(new BlockSwap(floor, floor.getBlockData().clone()));
                    floor.setType(Material.ICE, false);
                }
            }
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(target) <= 8 * 8) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 7, 2, true, true));
                player.damage(3.0, dragon);
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (BlockSwap swap : swaps) {
                swap.block().setBlockData(swap.original(), false);
            }
        }, 20L * 7);
    }

    private Location randomMeteorTarget(World world, DragonArena arena) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = Math.random() * Math.PI * 2.0;
            double distance = 6.0 + Math.random() * Math.max(10.0, arena.radius() - 8.0);
            int x = arena.centerX() + (int) Math.round(Math.cos(angle) * distance);
            int z = arena.centerZ() + (int) Math.round(Math.sin(angle) * distance);
            Block floor = world.getHighestBlockAt(x, z);
            if (floor.getType() != Material.BEDROCK && !floor.getType().isAir()) {
                return floor.getLocation().add(0.5, 1.0, 0.5);
            }
        }
        return new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
    }

    private void trimOldHits(UUID dragonUuid, long now) {
        Deque<HitRecord> queue = recentHits.get(dragonUuid);
        if (queue == null) {
            return;
        }
        while (!queue.isEmpty() && now - queue.peekFirst().atMillis() > PRESSURE_WINDOW_MILLIS) {
            queue.pollFirst();
        }
        if (queue.isEmpty()) {
            recentHits.remove(dragonUuid);
        }
    }

    private java.util.Optional<Player> nearestPlayer(EnderDragon dragon, int radius) {
        return nearbyPlayers(dragon, radius).stream()
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(dragon.getLocation())));
    }

    private List<Player> nearbyPlayers(EnderDragon dragon, int radius) {
        Location center = dragon.getLocation();
        return dragon.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(Entity::isValid)
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .filter(player -> !player.isDead())
                .toList();
    }

    private long cooldownFor(DragonAbility ability) {
        return switch (ability) {
            case CHARGE -> 5_000L;
            case DRAGON_BREATH, FIRE_BREATH, ICE_BREATH -> 9_000L;
            case SUMMON_ENDERMITE -> 18_000L;
            case FROST_NOVA, BLIZZARD -> 14_000L;
            case METEOR_STRIKE -> 12_000L;
            case PHASE_SHIFT -> 16_000L;
            case LAVA_POOL, IGNITE_AREA, FREEZE_AREA -> 13_000L;
            case FLARE_BURST -> 10_000L;
            case ICE_SPIKES -> 11_000L;
        };
    }

    private interface PlayerEffect {
        void apply(Player player);
    }

    private static Location lerpLocation(Location from, Location to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double x = from.getX() + (to.getX() - from.getX()) * clamped;
        double y = from.getY() + (to.getY() - from.getY()) * clamped;
        double z = from.getZ() + (to.getZ() - from.getZ()) * clamped;
        return new Location(from.getWorld(), x, y, z);
    }

    private static double easeInCubic(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped * clamped;
    }

    private static double easeInQuad(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped;
    }

    private record MeteorSite(World world, int blockX, int blockZ, Block floor) {
        private Location surfaceImpact() {
            return new Location(world, blockX + 0.5, floor.getY() + 1.0, blockZ + 0.5);
        }

        private Location cubeCenterAtSurface() {
            return new Location(world, blockX + 0.5, floor.getY() + 2.5, blockZ + 0.5);
        }

        private Location cubeCenterAtY(double centerY) {
            return new Location(world, blockX + 0.5, centerY, blockZ + 0.5);
        }
    }

    private record HitRecord(long atMillis, UUID attackerUuid, double damage) {
    }

    private record BlockSwap(Block block, BlockData original) {
    }
}
