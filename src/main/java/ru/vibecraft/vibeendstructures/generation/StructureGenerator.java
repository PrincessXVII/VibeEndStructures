package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.persistence.PersistentDataType;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.model.PlacementType;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;
import ru.vibecraft.vibeendstructures.model.StructurePiece;
import ru.vibecraft.vibeendstructures.structure.PlacementResult;
import ru.vibecraft.vibeendstructures.structure.ShipPlacement;
import ru.vibecraft.vibeendstructures.structure.StructureFootprint;
import ru.vibecraft.vibeendstructures.structure.StructurePlacer;
import ru.vibecraft.vibeendstructures.structure.StructureRegistry;

import java.util.List;
import java.util.Random;

public final class StructureGenerator {

    private final VibeEndStructuresPlugin plugin;
    private final StructureRegistry registry;
    private final StructurePlacer placer;
    private final StructureOccupancy occupancy;
    private final NamespacedKey processedKey;

    public StructureGenerator(
            VibeEndStructuresPlugin plugin,
            StructureRegistry registry,
            StructurePlacer placer,
            StructureOccupancy occupancy
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.placer = placer;
        this.occupancy = occupancy;
        this.processedKey = new NamespacedKey(plugin, "chunk_processed");
    }

    public void tryGenerate(Chunk chunk) {
        if (!isWorldEligible(chunk.getWorld())) {
            return;
        }

        if (chunk.getPersistentDataContainer().has(processedKey, PersistentDataType.BYTE)) {
            return;
        }

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        long seed = chunk.getWorld().getSeed();
        List<StructureDefinition> enabled = enabledStructures();

        int placedInChunk = 0;
        int maxPerChunk = plugin.getPluginConfig().getMaxStructuresPerChunk();

        for (StructureDefinition definition : enabled) {
            if (placedInChunk >= maxPerChunk) {
                break;
            }
            GenerationResult result = attemptPlacement(chunk.getWorld(), chunkX, chunkZ, seed, definition, true);
            if (result == GenerationResult.PLACED) {
                placedInChunk++;
            }
        }

        chunk.getPersistentDataContainer().set(processedKey, PersistentDataType.BYTE, (byte) 1);
    }

    public GenerationResult attemptPlacement(
            World world,
            int chunkX,
            int chunkZ,
            long seed,
            String structureId,
            boolean asyncFinalize
    ) {
        StructureDefinition definition = registry.getDefinition(structureId).orElse(null);
        if (definition == null || !definition.enabled()) {
            return GenerationResult.NOT_APPLICABLE;
        }
        return attemptPlacement(world, chunkX, chunkZ, seed, definition, asyncFinalize);
    }

    public GenerationResult attemptPlacement(
            World world,
            int chunkX,
            int chunkZ,
            long seed,
            StructureDefinition chosen,
            boolean asyncFinalize
    ) {
        if (!isWorldEligible(world)) {
            return GenerationResult.NOT_APPLICABLE;
        }

        if (!PlacementEngine.isSpawnChunk(
                seed,
                chunkX,
                chunkZ,
                chosen.spacing(),
                chosen.separation(),
                chosen.salt()
        )) {
            return GenerationResult.NOT_APPLICABLE;
        }

        if (!plugin.getPluginConfig().isCategoryEnabled(chosen.category())
                || !plugin.getPluginConfig().isStructureEnabled(chosen.id())) {
            return GenerationResult.NOT_APPLICABLE;
        }

        int minDist = chosen.minDistanceFromOrigin() != null
                ? chosen.minDistanceFromOrigin()
                : plugin.getPluginConfig().getMinDistanceFromOrigin();

        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        if (Math.hypot(blockX, blockZ) < minDist) {
            return GenerationResult.SKIPPED;
        }

        world.getChunkAt(chunkX, chunkZ);
        Random random = new Random(seed ^ (chunkX * 31L + chunkZ) ^ chosen.salt());
        Location anchor = PlacementAnchorResolver.resolve(world, chunkX, chunkZ, chosen, random).orElse(null);
        if (anchor == null) {
            if (plugin.getPluginConfig().isDebug()) {
                plugin.getLogger().info("No placement spot for " + chosen.id() + " at chunk " + chunkX + "," + chunkZ);
            }
            return GenerationResult.SKIPPED;
        }
        StructureFootprint preview = placer.previewFootprint(chosen, random);
        if (!occupancy.canPlace(
                world,
                anchor.getBlockX(),
                anchor.getBlockY(),
                anchor.getBlockZ(),
                preview,
                plugin.getPluginConfig().getStructureMinDistance()
        )) {
            if (plugin.getPluginConfig().isDebug()) {
                plugin.getLogger().info("Skipped " + chosen.id() + " at " + anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ() + " — overlaps another structure");
            }
            return GenerationResult.SKIPPED;
        }

        StructurePiece piece = registry.pickPiece(chosen, random);
        StructureRotation rotation = ShipPlacement.rotationFor(chosen, random);
        Mirror mirror = ShipPlacement.mirrorFor(chosen, random);

        if (asyncFinalize) {
            placer.placeForGeneration(
                    world,
                    anchor.getBlock(),
                    chosen,
                    piece,
                    rotation,
                    mirror,
                    random,
                    result -> onPlacementComplete(world, chosen, result)
            );
            return GenerationResult.PLACED;
        }

        PlacementResult result = placer.placeAt(anchor, chosen, rotation, mirror);
        if (!result.success()) {
            return GenerationResult.SKIPPED;
        }
        onPlacementComplete(world, chosen, result);
        return GenerationResult.PLACED;
    }

    public List<StructureDefinition> enabledStructures() {
        return PlacementEngine.filterEnabled(registry.getDefinitions(), definition ->
                definition.enabled()
                        && plugin.getPluginConfig().isCategoryEnabled(definition.category())
                        && plugin.getPluginConfig().isStructureEnabled(definition.id())
        );
    }

    private void onPlacementComplete(World world, StructureDefinition chosen, PlacementResult result) {
        if (!result.success() || result.anchor() == null || result.footprint() == null) {
            return;
        }

        occupancy.record(
                world,
                result.anchor().getBlockX(),
                result.anchor().getBlockY(),
                result.anchor().getBlockZ(),
                result.footprint()
        );

        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("Placed " + chosen.id() + " at " + result.anchor().getBlockX() + ", "
                    + result.anchor().getBlockY() + ", " + result.anchor().getBlockZ());
        }
    }

    private boolean isWorldEligible(World world) {
        if (!plugin.getPluginConfig().isEnabled()) {
            return false;
        }
        if (!plugin.getPluginConfig().isWorldEnabled(world.getName())) {
            return false;
        }
        return world.getEnvironment() == World.Environment.THE_END;
    }
}
