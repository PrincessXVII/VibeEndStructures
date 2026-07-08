package ru.vibecraft.vibeendstructures.dragon.model;

import java.util.UUID;

public record DragonArena(
    String id,
    String name,
    String dragonTypeId,
    int centerX,
    int centerZ,
    int radius,
    int height,
    long cooldownHours,
    String spawnStructure,
    boolean enabled,
    UUID activeDragonUuid,
    long cooldownUntil,
    ArenaState state
) {
    public enum ArenaState {
        IDLE,
        SPAWNING,
        ACTIVE,
        COOLDOWN,
        DISABLED
    }

    public boolean isAvailable() {
        return state == ArenaState.IDLE && enabled;
    }

    public boolean isOnCooldown() {
        return state == ArenaState.COOLDOWN && System.currentTimeMillis() < cooldownUntil;
    }

    public DragonArena withState(ArenaState newState) {
        return new DragonArena(id, name, dragonTypeId, centerX, centerZ, radius, height, cooldownHours, spawnStructure, enabled, activeDragonUuid, cooldownUntil, newState);
    }

    public DragonArena withSpawning() {
        return new DragonArena(id, name, dragonTypeId, centerX, centerZ, radius, height, cooldownHours, spawnStructure, enabled, null, cooldownUntil, ArenaState.SPAWNING);
    }

    public DragonArena withActiveDragon(UUID dragonUuid) {
        return new DragonArena(id, name, dragonTypeId, centerX, centerZ, radius, height, cooldownHours, spawnStructure, enabled, dragonUuid, cooldownUntil, ArenaState.ACTIVE);
    }

    public DragonArena withCooldown() {
        long until = System.currentTimeMillis() + cooldownHours * 3600_000L;
        return new DragonArena(id, name, dragonTypeId, centerX, centerZ, radius, height, cooldownHours, spawnStructure, enabled, null, until, ArenaState.COOLDOWN);
    }

    public DragonArena withClearedCooldown() {
        return new DragonArena(id, name, dragonTypeId, centerX, centerZ, radius, height, cooldownHours, spawnStructure, enabled, null, 0, ArenaState.IDLE);
    }

    public DragonArena withCooldownHours(long newCooldownHours) {
        return new DragonArena(id, name, dragonTypeId, centerX, centerZ, radius, height, newCooldownHours, spawnStructure, enabled, activeDragonUuid, cooldownUntil, state);
    }

    public DragonArena withPosition(int newCenterX, int newCenterZ, int newHeight, int newRadius) {
        return new DragonArena(id, name, dragonTypeId, newCenterX, newCenterZ, newRadius, newHeight, cooldownHours, spawnStructure, enabled, activeDragonUuid, cooldownUntil, state);
    }
}
