package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.structure.StructureFootprint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class StructureOccupancy {

    private static final Set<Material> NATURAL_END_BLOCKS = EnumSet.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.END_STONE,
            Material.END_STONE_BRICKS,
            Material.PURPUR_BLOCK,
            Material.PURPUR_PILLAR,
            Material.PURPUR_STAIRS,
            Material.PURPUR_SLAB,
            Material.CHORUS_PLANT,
            Material.CHORUS_FLOWER,
            Material.BEDROCK
    );

    private final JavaPlugin plugin;
    private final File file;
    private final List<PlacedStructure> placed = new ArrayList<>();

    public StructureOccupancy(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placements.yml");
        load();
    }

    public boolean canPlace(World world, int anchorX, int anchorY, int anchorZ, StructureFootprint footprint, int minDistance) {
        int radius = Math.max(footprint.horizontalRadius(), minDistance / 2);

        for (PlacedStructure existing : placed) {
            if (!existing.world().equals(world.getName())) {
                continue;
            }
            double dx = existing.x() - anchorX;
            double dz = existing.z() - anchorZ;
            double required = existing.radius() + radius + 4;
            if (dx * dx + dz * dz < required * required) {
                return false;
            }
        }

        return isAreaNatural(world, anchorX, anchorY, anchorZ, footprint);
    }

    public void record(World world, String structureId, int anchorX, int anchorY, int anchorZ, StructureFootprint footprint) {
        placed.add(new PlacedStructure(
                world.getName(),
                structureId,
                anchorX,
                anchorY,
                anchorZ,
                footprint.horizontalRadius()
        ));
        saveAsync();
    }

    public List<PlacedStructure> placedStructures() {
        return List.copyOf(placed);
    }

    public List<PlacedStructure> placedInWorld(String worldName) {
        return placed.stream()
                .filter(structure -> structure.world().equals(worldName))
                .sorted(Comparator.comparing(PlacedStructure::world)
                        .thenComparing(PlacedStructure::structureId)
                        .thenComparingInt(PlacedStructure::x)
                        .thenComparingInt(PlacedStructure::z))
                .toList();
    }

    public Optional<PlacedStructure> nearest(Location location) {
        if (location.getWorld() == null) {
            return Optional.empty();
        }
        String worldName = location.getWorld().getName();
        return placed.stream()
                .filter(structure -> structure.world().equals(worldName))
                .min(Comparator.comparingDouble(structure -> structure.distanceSquared(location)));
    }

    private boolean isAreaNatural(World world, int anchorX, int anchorY, int anchorZ, StructureFootprint footprint) {
        int minX = anchorX;
        int maxX = anchorX + footprint.sizeX() - 1;
        int minZ = anchorZ;
        int maxZ = anchorZ + footprint.sizeZ() - 1;
        int minY = anchorY;
        int maxY = anchorY + footprint.sizeY() - 1;

        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                for (int y = minY; y <= maxY; y += 3) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!NATURAL_END_BLOCKS.contains(block.getType())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void load() {
        placed.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> entries = yaml.getList("placements", List.of());
        for (Object entry : entries) {
            if (!(entry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            Object structureId = map.containsKey("structure") ? map.get("structure") : "unknown";
            placed.add(new PlacedStructure(
                    String.valueOf(map.get("world")),
                    String.valueOf(structureId),
                    ((Number) map.get("x")).intValue(),
                    ((Number) map.get("y")).intValue(),
                    ((Number) map.get("z")).intValue(),
                    ((Number) map.get("radius")).intValue()
            ));
        }
    }

    private void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<java.util.Map<String, Object>> entries = new ArrayList<>();
        for (PlacedStructure structure : placed) {
            entries.add(java.util.Map.of(
                    "world", structure.world(),
                    "structure", structure.structureId(),
                    "x", structure.x(),
                    "y", structure.y(),
                    "z", structure.z(),
                    "radius", structure.radius()
            ));
        }
        yaml.set("placements", entries);
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save placements: " + ex.getMessage());
        }
    }

    public record PlacedStructure(String world, String structureId, int x, int y, int z, int radius) {
        public double distanceSquared(Location location) {
            double dx = x - location.getX();
            double dy = y - location.getY();
            double dz = z - location.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
