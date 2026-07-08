package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.reward.DragonEggManager;

public final class DragonEggGlowTask {

    private final VibeEndStructuresPlugin plugin;
    private final DragonEggManager eggManager;
    private BukkitTask task;

    public DragonEggGlowTask(VibeEndStructuresPlugin plugin, DragonEggManager eggManager) {
        this.plugin = plugin;
        this.eggManager = eggManager;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (eggManager.hasRenewableEgg(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 70, 0, true, false, true));
            }
        }
    }
}
