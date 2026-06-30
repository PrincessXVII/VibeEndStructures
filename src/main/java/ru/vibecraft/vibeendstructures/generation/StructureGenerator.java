package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.persistence.PersistentDataType;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
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
        if (!plugin.getPluginConfig().isEnabled()) {
            return;
        }

        World world = chunk.getWorld();
        if (!plugin.getPluginConfig().isWorldEnabled(world.getName())) {
            return;
        }
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }

        if (chunk.getPersistentDataContainer().has(processedKey, PersistentDataType.BYTE)) {
            return;
        }
        chunk.getPersistentDataContainer().set(processedKey, PersistentDataType.BYTE, (byte) 1);

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        long seed = world.getSeed();

        generateAt(world, chunkX, chunkZ, seed);
    }

    private void generateAt(World world, int chunkX, int chunkZ, long seed) {
        if (!plugin.getPluginConfig().isEnabled()) {
            return;
        }
        if (!plugin.getPluginConfig().isWorldEnabled(world.getName())) {
            return;
        }
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }

        int spacing = plugin.getPluginConfig().getGridSpacing();
        int separation = plugin.getPluginConfig().getGridSeparation();
        int salt = plugin.getPluginConfig().getGridSalt();

        if (!PlacementEngine.isSpawnChunk(seed, chunkX, chunkZ, spacing, separation, salt)) {
            return;
        }

        List<StructureDefinition> enabled = PlacementEngine.filterEnabled(registry.getDefinitions(), definition ->
                plugin.getPluginConfig().isCategoryEnabled(definition.category())
                        && plugin.getPluginConfig().isStructureEnabled(definition.id())
        );
        if (enabled.isEmpty()) {
            return;
        }

        StructureDefinition chosen = PlacementEngine.pickStructure(enabled, seed, chunkX, chunkZ, salt);
        if (chosen == null) {
            return;
        }

        int minDist = chosen.minDistanceFromOrigin() != null
                ? chosen.minDistanceFromOrigin()
                : plugin.getPluginConfig().getMinDistanceFromOrigin();

        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        if (Math.hypot(blockX, blockZ) < minDist) {
            return;
        }

        Random random = new Random(seed ^ (chunkX * 31L + chunkZ));
        StructureFootprint preview = placer.previewFootprint(chosen, random);

        Block surface;
        if ("mega_ship".equals(chosen.category())) {
            surface = ShipSpawnFinder.findSpawn(world, chunkX, chunkZ, preview, random).orElse(null);
        } else {
            int minY = Math.max(chosen.minY(), plugin.getPluginConfig().getMinY());
            surface = EndSurfaceFinder.findSurface(world, chunkX, chunkZ, minY, random).orElse(null);
        }
        if (surface == null) {
            if (plugin.getPluginConfig().isDebug()) {
                plugin.getLogger().info("No placement spot for " + chosen.id() + " at chunk " + chunkX + "," + chunkZ);
            }
            return;
        }

        int anchorX = surface.getX();
        int anchorZ = surface.getZ();
        int anchorY = surface.getY();

        if (!occupancy.canPlace(world, anchorX, anchorY, anchorZ, preview, plugin.getPluginConfig().getStructureMinDistance())) {
            if (plugin.getPluginConfig().isDebug()) {
                plugin.getLogger().info("Skipped " + chosen.id() + " at " + anchorX + "," + anchorY + "," + anchorZ + " — overlaps another structure");
            }
            return;
        }

        StructurePiece piece = registry.pickPiece(chosen, random);
        StructureRotation rotation = ShipPlacement.rotationFor(chosen, random);
        Mirror mirror = ShipPlacement.mirrorFor(chosen, random);

        placer.placeForGeneration(world, surface, chosen, piece, rotation, mirror, random, result ->
                onPlacementComplete(world, chosen, result, chunkX, chunkZ)
        );
    }

    private void onPlacementComplete(World world, StructureDefinition chosen, PlacementResult result, int chunkX, int chunkZ) {
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
                    + result.anchor().getBlockY() + ", " + result.anchor().getBlockZ()
                    + " (chunk " + chunkX + "," + chunkZ + ")");
        }
    }
}
