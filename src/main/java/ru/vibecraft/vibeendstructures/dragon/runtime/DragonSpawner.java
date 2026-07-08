package ru.vibecraft.vibeendstructures.dragon.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DragonSpawner {

    private static final int RITUAL_TICKS = 140;
    private static final int VANILLA_RESPAWN_TIMEOUT_TICKS = 20 * 90;

    private final JavaPlugin plugin;
    private final DragonKeys keys;

    public DragonSpawner(JavaPlugin plugin, DragonKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public DragonSpawnRitual spawnWithRitual(World world, DragonArena arena, DragonDefinition definition, boolean eggDropEligible, Consumer<EnderDragon> callback) {
        Location center = endPortalCenter(world, arena);
        List<EnderCrystal> crystals = spawnRitualCrystals(world, center, arena);
        Particle primaryParticle = ritualParticle(definition);
        BukkitTask particles = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            world.spawnParticle(Particle.PORTAL, center.clone().add(0, 2.5, 0), 90, 5.0, 2.5, 5.0, 0.2);
            world.spawnParticle(primaryParticle, center.clone().add(0, 3.5, 0), 45, 4.0, 2.2, 4.0, 0.04);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0, 5.0, 0), 25, 2.0, 2.0, 2.0, 0.04);
            spawnRitualRing(world, center, primaryParticle);
            for (EnderCrystal crystal : crystals) {
                if (crystal.isValid()) {
                    crystal.setBeamTarget(center.clone().add(0, 7.0, 0));
                }
            }
        }, 0L, 10L);

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 3.0f, 0.7f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 4.0f, 0.65f);
        BukkitTask spawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DragonBattle battle = world.getEnderDragonBattle();
            boolean vanillaStarted = false;
            if (battle != null && (battle.getEnderDragon() == null || !battle.getEnderDragon().isValid())) {
                vanillaStarted = battle.initiateRespawn(crystals);
            }
            if (!vanillaStarted) {
                particles.cancel();
                removeCrystals(crystals);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 2.5, 0), 1);
                world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3.0f, 0.8f);
                world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 4.0f, 0.7f);
                callback.accept(spawn(world, arena, definition, eggDropEligible));
                return;
            }
            waitForVanillaDragon(world, arena, definition, eggDropEligible, callback, particles, crystals, 0);
        }, RITUAL_TICKS);
        return new DragonSpawnRitual(crystals, particles, spawnTask);
    }

    public EnderDragon spawn(World world, DragonArena arena, DragonDefinition definition, boolean eggDropEligible) {
        Location spawn = spawnLocation(world, arena);
        return world.spawn(spawn, EnderDragon.class, CreatureSpawnEvent.SpawnReason.CUSTOM, dragon -> {
            configureDragon(dragon, arena, definition, eggDropEligible);
        });
    }

    private List<EnderCrystal> spawnRitualCrystals(World world, Location center, DragonArena arena) {
        List<EnderCrystal> crystals = new ArrayList<>();
        double[][] offsets = {
                {3, 0},
                {-3, 0},
                {0, 3},
                {0, -3}
        };
        for (double[] offset : offsets) {
            Location location = crystalLocation(world, center, offset[0], offset[1]);
            EnderCrystal crystal = world.spawn(location, EnderCrystal.class, entity -> {
                entity.setShowingBottom(false);
                entity.setInvulnerable(true);
                entity.setBeamTarget(center.clone().add(0, 7.0, 0));
                entity.addScoreboardTag("vibedragon:ritual");
                entity.addScoreboardTag("vibedragon:ritual:" + arena.id());
            });
            crystals.add(crystal);
        }
        return crystals;
    }

    private Location spawnLocation(World world, DragonArena arena) {
        return new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
    }

    private Location endPortalCenter(World world, DragonArena arena) {
        DragonBattle battle = world.getEnderDragonBattle();
        if (battle != null && battle.getEndPortalLocation() != null) {
            Location portal = battle.getEndPortalLocation();
            return new Location(world, portal.getBlockX() + 0.5, portal.getBlockY() + 1.0, portal.getBlockZ() + 0.5);
        }
        int y = findPortalTopY(world, arena.centerX(), arena.centerZ());
        return new Location(world, arena.centerX() + 0.5, y + 1.0, arena.centerZ() + 0.5);
    }

    private Location crystalLocation(World world, Location center, double offsetX, double offsetZ) {
        int x = center.getBlockX() + (int) offsetX;
        int z = center.getBlockZ() + (int) offsetZ;
        int y = findPortalTopY(world, x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private int findPortalTopY(World world, int x, int z) {
        int max = Math.min(world.getMaxHeight() - 1, 128);
        for (int y = max; y >= world.getMinHeight(); y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type == Material.BEDROCK || type == Material.END_PORTAL || type == Material.OBSIDIAN) {
                return y;
            }
        }
        return 64;
    }

    private void waitForVanillaDragon(
            World world,
            DragonArena arena,
            DragonDefinition definition,
            boolean eggDropEligible,
            Consumer<EnderDragon> callback,
            BukkitTask particles,
            List<EnderCrystal> crystals,
            int waitedTicks
    ) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DragonBattle battle = world.getEnderDragonBattle();
            EnderDragon dragon = battle == null ? null : battle.getEnderDragon();
            if (dragon != null && dragon.isValid() && !dragon.isDead()) {
                particles.cancel();
                configureDragon(dragon, arena, definition, eggDropEligible);
                callback.accept(dragon);
                return;
            }
            if (waitedTicks >= VANILLA_RESPAWN_TIMEOUT_TICKS) {
                particles.cancel();
                removeCrystals(crystals);
                callback.accept(spawn(world, arena, definition, eggDropEligible));
                return;
            }
            waitForVanillaDragon(world, arena, definition, eggDropEligible, callback, particles, crystals, waitedTicks + 20);
        }, 20L);
    }

    private void configureDragon(EnderDragon dragon, DragonArena arena, DragonDefinition definition, boolean eggDropEligible) {
        Location spawn = spawnLocation(dragon.getWorld(), arena);
        dragon.customName(Component.text(definition.displayName()));
        dragon.setCustomNameVisible(true);
        dragon.setPersistent(true);
        dragon.setRemoveWhenFarAway(false);
        dragon.setPodium(spawn);
        dragon.setPhase(EnderDragon.Phase.CIRCLING);

        setAttribute(dragon, Attribute.MAX_HEALTH, definition.health());
        setAttribute(dragon, Attribute.ATTACK_DAMAGE, definition.damage());
        setAttribute(dragon, Attribute.ARMOR, definition.armor());
        setAttribute(dragon, Attribute.KNOCKBACK_RESISTANCE, definition.knockbackResistance());
        setAttribute(dragon, Attribute.FOLLOW_RANGE, definition.followRange());
        setAttribute(dragon, Attribute.MOVEMENT_SPEED, definition.movementSpeed());
        setAttribute(dragon, Attribute.FLYING_SPEED, definition.flyingSpeed());
        AttributeInstance maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            dragon.setHealth(Math.min(definition.health(), maxHealth.getValue()));
        }

        var data = dragon.getPersistentDataContainer();
        data.set(keys.dragonId(), PersistentDataType.STRING, definition.id());
        data.set(keys.dragonType(), PersistentDataType.STRING, definition.type().name());
        data.set(keys.arenaId(), PersistentDataType.STRING, arena.id());
        data.set(keys.eggDropEligible(), PersistentDataType.BYTE, (byte) (eggDropEligible ? 1 : 0));

        dragon.addScoreboardTag("vibedragon");
        dragon.addScoreboardTag("vibedragon:type:" + definition.id());
        dragon.addScoreboardTag("vibedragon:arena:" + arena.id());
        if (!eggDropEligible) {
            dragon.addScoreboardTag("vibedragon:summoned_by_egg");
        }
    }

    private void removeCrystals(List<EnderCrystal> crystals) {
        for (EnderCrystal crystal : crystals) {
            if (crystal.isValid()) {
                crystal.remove();
            }
        }
    }

    private Particle ritualParticle(DragonDefinition definition) {
        if (definition.type() == DragonType.FIRE) {
            return Particle.FLAME;
        }
        if (definition.type() == DragonType.ICE) {
            return Particle.SNOWFLAKE;
        }
        return Particle.DRAGON_BREATH;
    }

    private void spawnRitualRing(World world, Location center, Particle particle) {
        double radius = 5.5;
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24.0;
            Location point = center.clone().add(Math.cos(angle) * radius, 1.2, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 2, 0.08, 0.08, 0.08, 0.01);
        }
    }

    private void setAttribute(EnderDragon dragon, Attribute attribute, double value) {
        AttributeInstance instance = dragon.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
