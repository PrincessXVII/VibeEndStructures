package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class DragonDeathRitual {

    private static final int DURATION_TICKS = 20 * 10;

    private DragonDeathRitual() {
    }

    public static void play(JavaPlugin plugin, Location deathLocation, Runnable onComplete) {
        World world = deathLocation.getWorld();
        if (world == null) {
            onComplete.run();
            return;
        }

        Location center = deathLocation.clone();
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.HOSTILE, 5.0f, 1.0f);

        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks;

            @Override
            public void run() {
                ticks += 5;
                double progress = Math.min(1.0, ticks / (double) DURATION_TICKS);
                double height = 2.0 + progress * 18.0;

                world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(0, 2.0 + progress * 4.0, 0), 90, 6.0, 2.5, 6.0, 0.05);
                world.spawnParticle(Particle.END_ROD, center.clone().add(0, height, 0), 45, 1.4, 3.5, 1.4, 0.08);
                world.spawnParticle(Particle.PORTAL, center.clone().add(0, 1.5, 0), 70, 7.0, 3.0, 7.0, 0.45);

                if (ticks % 20 == 0) {
                    world.spawnParticle(Particle.EXPLOSION, center.clone().add(randomOffset(progress), 3.0 + progress * 8.0, randomOffset(progress)), 2, 1.5, 1.0, 1.5, 0.0);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.7f + (float) progress * 0.35f);
                }

                if (ticks >= DURATION_TICKS) {
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 5.0, 0), 1);
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 2.0, 0), 160, 8.0, 4.0, 8.0, 0.1);
                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.HOSTILE, 3.0f, 0.8f);
                    taskRef[0].cancel();
                    onComplete.run();
                }
            }
        }, 0L, 5L);
    }

    private static double randomOffset(double progress) {
        return (Math.random() - 0.5) * (4.0 + progress * 8.0);
    }
}
