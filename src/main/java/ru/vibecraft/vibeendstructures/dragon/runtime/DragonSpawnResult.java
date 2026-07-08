package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.entity.EnderDragon;

public record DragonSpawnResult(
    boolean success,
    String message,
    EnderDragon dragon
) {
    public static DragonSpawnResult success(String message, EnderDragon dragon) {
        return new DragonSpawnResult(true, message, dragon);
    }

    public static DragonSpawnResult failure(String message) {
        return new DragonSpawnResult(false, message, null);
    }
}
