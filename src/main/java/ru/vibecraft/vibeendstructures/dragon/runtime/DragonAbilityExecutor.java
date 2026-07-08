package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.DragonAbility;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonAbilityExecutor {

    private static final long DEFAULT_COOLDOWN_MILLIS = 8_000L;

    private final VibeEndStructuresPlugin plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public DragonAbilityExecutor(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick(EnderDragon dragon, DragonArena arena, DragonDefinition definition, List<DragonAbility> abilities) {
        if (abilities.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (DragonAbility ability : abilities) {
            String key = dragon.getUniqueId() + ":" + ability.name();
            long readyAt = cooldowns.getOrDefault(key, 0L);
            if (readyAt > now) {
                continue;
            }
            execute(dragon, arena, ability);
            cooldowns.put(key, now + cooldownFor(ability));
            return;
        }
    }

    public void clear(UUID dragonUuid) {
        String prefix = dragonUuid + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void execute(EnderDragon dragon, DragonArena arena, DragonAbility ability) {
        switch (ability) {
            case CHARGE -> charge(dragon, arena);
            case DRAGON_BREATH -> breath(dragon, arena, Particle.DRAGON_BREATH, 5.0, null);
            case FIRE_BREATH -> breath(dragon, arena, Particle.FLAME, 6.0, player ->
                    player.setFireTicks(Math.max(player.getFireTicks(), 80)));
            case ICE_BREATH -> breath(dragon, arena, Particle.SNOWFLAKE, 4.0, player ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, true, true)));
            case SUMMON_ENDERMITE -> summonEndermites(dragon);
            case FROST_NOVA -> frostNova(dragon, arena);
            default -> debugNoop(ability);
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
        world.spawnParticle(particle, origin, 80, 5.0, 2.0, 5.0, 0.02);
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
        world.spawnParticle(Particle.PORTAL, base, 80, 3.0, 1.0, 3.0, 0.2);
    }

    private void frostNova(EnderDragon dragon, DragonArena arena) {
        Location origin = dragon.getLocation();
        dragon.getWorld().spawnParticle(Particle.SNOWFLAKE, origin, 140, 10.0, 2.5, 10.0, 0.03);
        dragon.getWorld().playSound(origin, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
        for (Player player : nearbyPlayers(dragon, Math.min(arena.radius(), 18))) {
            player.damage(5.0, dragon);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 2, true, true));
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
            case FROST_NOVA -> 14_000L;
            default -> DEFAULT_COOLDOWN_MILLIS;
        };
    }

    private void debugNoop(DragonAbility ability) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("Dragon ability is configured but not implemented in v1: " + ability);
        }
    }

    private interface PlayerEffect {
        void apply(Player player);
    }
}
