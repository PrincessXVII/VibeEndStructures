package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public final class DragonSpawnRitual {

    private final List<EnderCrystal> crystals;
    private final List<BukkitTask> tasks;

    public DragonSpawnRitual(List<EnderCrystal> crystals, BukkitTask particleTask, BukkitTask spawnTask) {
        this(crystals, List.of(particleTask, spawnTask));
    }

    public DragonSpawnRitual(List<EnderCrystal> crystals, List<BukkitTask> tasks) {
        this.crystals = crystals;
        this.tasks = tasks;
    }

    public void cancel() {
        for (BukkitTask task : tasks) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        removeCrystals();
    }

    public void removeCrystals() {
        for (EnderCrystal crystal : crystals) {
            if (crystal.isValid()) {
                crystal.remove();
            }
        }
    }
}
