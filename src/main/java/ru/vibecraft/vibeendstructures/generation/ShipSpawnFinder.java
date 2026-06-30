package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.vibecraft.vibeendstructures.structure.StructureFootprint;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class ShipSpawnFinder {

    private static final int MIN_Y = 100;
    private static final int MAX_Y = 120;
    private static final double MIN_VOID_RATIO = 0.70;
    private static final int SAMPLE_STEP = 4;

    private static final Set<Material> ISLAND_BLOCKS = Set.of(
            Material.END_STONE,
            Material.END_STONE_BRICKS,
            Material.PURPUR_BLOCK,
            Material.PURPUR_PILLAR,
            Material.PURPUR_STAIRS,
            Material.PURPUR_SLAB,
            Material.BEDROCK
    );

    private ShipSpawnFinder() {
    }

    public static Optional<Block> findSpawn(
            World world,
            int chunkX,
            int chunkZ,
            StructureFootprint footprint,
            Random random
    ) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        for (int attempt = 0; attempt < 10; attempt++) {
            int anchorX = baseX + 2 + random.nextInt(12);
            int anchorZ = baseZ + 2 + random.nextInt(12);
            int anchorY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);

            if (meetsVoidRequirement(world, anchorX, anchorY, anchorZ, footprint)) {
                return Optional.of(world.getBlockAt(anchorX, anchorY, anchorZ));
            }
        }

        return Optional.empty();
    }

    private static boolean meetsVoidRequirement(
            World world,
            int anchorX,
            int anchorY,
            int anchorZ,
            StructureFootprint footprint
    ) {
        int minX = anchorX;
        int maxX = anchorX + footprint.sizeX() - 1;
        int minZ = anchorZ;
        int maxZ = anchorZ + footprint.sizeZ() - 1;

        int samples = 0;
        int voidSamples = 0;

        for (int x = minX; x <= maxX; x += SAMPLE_STEP) {
            for (int z = minZ; z <= maxZ; z += SAMPLE_STEP) {
                samples++;
                if (isColumnOverVoid(world, x, anchorY, z)) {
                    voidSamples++;
                }
            }
        }

        if (samples == 0) {
            return false;
        }
        return (double) voidSamples / samples >= MIN_VOID_RATIO;
    }

    private static boolean isColumnOverVoid(World world, int x, int belowY, int z) {
        for (int y = belowY - 1; y >= world.getMinHeight(); y--) {
            if (ISLAND_BLOCKS.contains(world.getBlockAt(x, y, z).getType())) {
                return false;
            }
        }
        return true;
    }
}
