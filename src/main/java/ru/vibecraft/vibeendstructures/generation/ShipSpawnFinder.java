package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.Random;

public final class ShipSpawnFinder {

    private static final int MIN_Y = 100;
    private static final int MAX_Y = 120;

    private ShipSpawnFinder() {
    }

    public static Optional<Block> findSpawn(World world, int chunkX, int chunkZ, Random random) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        int anchorX = baseX + 2 + random.nextInt(12);
        int anchorZ = baseZ + 2 + random.nextInt(12);
        int anchorY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
        return Optional.of(world.getBlockAt(anchorX, anchorY, anchorZ));
    }
}
