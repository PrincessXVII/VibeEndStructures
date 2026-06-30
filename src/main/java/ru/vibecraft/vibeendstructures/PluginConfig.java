package ru.vibecraft.vibeendstructures;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PluginConfig {

    private final VibeEndStructuresPlugin plugin;
    private boolean enabled;
    private List<String> worlds;
    private int minDistanceFromOrigin;
    private int minY;
    private boolean installLootDatapack;
    private boolean decoration;
    private boolean structure;
    private boolean megaShip;
    private Set<String> disabledStructures;
    private boolean debug;
    private int gridSpacing;
    private int gridSeparation;
    private int gridSalt;
    private int structureMinDistance;
    private int maxStructuresPerChunk;
    private int placementsPerTick;
    private int progressIntervalTicks;

    public PluginConfig(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        enabled = cfg.getBoolean("enabled", true);
        worlds = cfg.getStringList("worlds");
        minDistanceFromOrigin = cfg.getInt("min-distance-from-origin", 1000);
        minY = cfg.getInt("min-y", 45);
        installLootDatapack = cfg.getBoolean("install-loot-datapack", true);
        decoration = cfg.getBoolean("categories.decoration", true);
        structure = cfg.getBoolean("categories.structure", true);
        megaShip = cfg.getBoolean("categories.mega_ship", true);
        disabledStructures = new HashSet<>(cfg.getStringList("disabled-structures"));
        debug = cfg.getBoolean("debug", false);
        gridSpacing = cfg.getInt("grid.spacing", 12);
        gridSeparation = cfg.getInt("grid.separation", 4);
        gridSalt = cfg.getInt("grid.salt", 928451883);
        structureMinDistance = cfg.getInt("structure-min-distance", 22);
        maxStructuresPerChunk = cfg.getInt("max-structures-per-chunk", 1);
        placementsPerTick = cfg.getInt("generation.placements-per-tick", 1);
        progressIntervalTicks = cfg.getInt("generation.progress-interval-ticks", 100);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isWorldEnabled(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }

    public int getMinDistanceFromOrigin() {
        return minDistanceFromOrigin;
    }

    public int getMinY() {
        return minY;
    }

    public boolean isInstallLootDatapack() {
        return installLootDatapack;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public boolean isCategoryEnabled(String category) {
        return switch (category) {
            case "decoration" -> decoration;
            case "structure" -> structure;
            case "mega_ship" -> megaShip;
            default -> true;
        };
    }

    public boolean isStructureEnabled(String id) {
        return !disabledStructures.contains(id);
    }

    public boolean isDebug() {
        return debug;
    }

    public int getGridSpacing() {
        return gridSpacing;
    }

    public int getGridSeparation() {
        return gridSeparation;
    }

    public int getGridSalt() {
        return gridSalt;
    }

    public int getStructureMinDistance() {
        return structureMinDistance;
    }

    public int getMaxStructuresPerChunk() {
        return maxStructuresPerChunk;
    }

    public int getPlacementsPerTick() {
        return placementsPerTick;
    }

    public int getProgressIntervalTicks() {
        return progressIntervalTicks;
    }
}
