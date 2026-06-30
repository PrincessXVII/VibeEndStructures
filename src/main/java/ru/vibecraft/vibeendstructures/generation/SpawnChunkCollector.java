package ru.vibecraft.vibeendstructures.generation;

import ru.vibecraft.vibeendstructures.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;

public final class SpawnChunkCollector {

    private SpawnChunkCollector() {
    }

    public static List<SpawnCandidate> collect(WorldBounds bounds, long worldSeed, List<StructureDefinition> structures) {
        List<SpawnCandidate> candidates = new ArrayList<>();

        for (StructureDefinition definition : structures) {
            if (!definition.enabled()) {
                continue;
            }

            int minChunkX = floorDiv(bounds.minBlockX(), 16);
            int maxChunkX = floorDiv(bounds.maxBlockX(), 16);
            int minChunkZ = floorDiv(bounds.minBlockZ(), 16);
            int maxChunkZ = floorDiv(bounds.maxBlockZ(), 16);

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!isInsideRadius(bounds, chunkX, chunkZ)) {
                        continue;
                    }
                    if (!PlacementEngine.isSpawnChunk(
                            worldSeed,
                            chunkX,
                            chunkZ,
                            definition.spacing(),
                            definition.separation(),
                            definition.salt()
                    )) {
                        continue;
                    }
                    candidates.add(new SpawnCandidate(chunkX, chunkZ, definition.id()));
                }
            }
        }

        return candidates;
    }

    private static boolean isInsideRadius(WorldBounds bounds, int chunkX, int chunkZ) {
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        return Math.hypot(centerX, centerZ) <= bounds.radiusBlocks();
    }

    private static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }

    public record SpawnCandidate(int chunkX, int chunkZ, String structureId) {
    }

    public record WorldBounds(int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ, int radiusBlocks) {
        public static WorldBounds fromRadius(int radiusBlocks) {
            return new WorldBounds(-radiusBlocks, radiusBlocks, -radiusBlocks, radiusBlocks, radiusBlocks);
        }
    }
}
