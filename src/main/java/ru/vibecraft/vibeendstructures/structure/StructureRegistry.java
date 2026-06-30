package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;
import ru.vibecraft.vibeendstructures.model.StructurePiece;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;

public final class StructureRegistry {

    private final JavaPlugin plugin;
    private final Map<String, StructureDefinition> definitions = new HashMap<>();
    private final Map<String, Structure> loadedStructures = new HashMap<>();
    private final Map<String, Map<String, List<StructurePiece>>> poolIndex = new HashMap<>();

    public StructureRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File getStructuresRoot() {
        return new File(plugin.getDataFolder(), "structures");
    }

    public void load() {
        definitions.clear();
        loadedStructures.clear();
        poolIndex.clear();

        File structuresRoot = getStructuresRoot();
        StructureBootstrap.ensureDefaults(plugin, structuresRoot);

        File[] structureDirs = structuresRoot.listFiles(File::isDirectory);
        if (structureDirs == null || structureDirs.length == 0) {
            plugin.getLogger().warning("No structures found in " + structuresRoot.getAbsolutePath());
            return;
        }

        StructureManager manager = Bukkit.getStructureManager();
        int loadedPieces = 0;

        for (File structureDir : structureDirs) {
            File configFile = new File(structureDir, "config.yml");
            if (!configFile.exists()) {
                plugin.getLogger().warning("Skipping " + structureDir.getName() + " — config.yml not found");
                continue;
            }

            try {
                StructureDefinition definition = StructureConfigLoader.load(structureDir, configFile);
                definitions.put(definition.id(), definition);

                Map<String, List<StructurePiece>> pools = new HashMap<>();
                for (StructurePiece piece : definition.pieces()) {
                    if (!piece.pool().isEmpty()) {
                        pools.computeIfAbsent(piece.pool(), ignored -> new ArrayList<>()).add(piece);
                    }
                }
                poolIndex.put(definition.id(), pools);

                File nbtDir = new File(structureDir, definition.id());
                for (StructurePiece piece : definition.pieces()) {
                    String key = piece.resourcePath();
                    if (loadedStructures.containsKey(key)) {
                        continue;
                    }

                    File nbtFile = new File(nbtDir, pieceFileName(piece.resourcePath()));
                    if (!nbtFile.exists()) {
                        plugin.getLogger().warning("Missing NBT: " + nbtFile.getAbsolutePath());
                        continue;
                    }

                    try (FileInputStream nbt = new FileInputStream(nbtFile)) {
                        Structure structure = manager.loadStructure(nbt);
                        NamespacedKey nsKey = new NamespacedKey(plugin, sanitizeKey(key));
                        manager.registerStructure(nsKey, structure);
                        loadedStructures.put(key, structure);
                        loadedPieces++;
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.WARNING, "Failed to load structure piece " + key, ex);
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load structure from " + structureDir.getName(), ex);
            }
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " structure types (" + loadedPieces + " NBT pieces) from "
                + structuresRoot.getAbsolutePath());
    }

    public Collection<StructureDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Optional<StructureDefinition> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<Structure> getStructure(StructureDefinition definition, StructurePiece piece) {
        return Optional.ofNullable(loadedStructures.get(piece.resourcePath()));
    }

    public List<StructurePiece> getPiecesForPool(StructureDefinition definition, String pool) {
        Map<String, List<StructurePiece>> pools = poolIndex.get(definition.id());
        if (pools == null) {
            return List.of();
        }
        return pools.getOrDefault(pool, List.of());
    }

    public StructurePiece pickPiece(StructureDefinition definition, Random random) {
        List<StructurePiece> pieces = definition.pieces();
        if (pieces.isEmpty()) {
            throw new IllegalStateException("No pieces for " + definition.id());
        }
        if (pieces.size() == 1) {
            return pieces.getFirst();
        }

        if (definition.category().equals("mega_ship")) {
            for (StructurePiece piece : pieces) {
                String name = pieceFileName(piece.resourcePath());
                if (name.equals(definition.id() + ".nbt")) {
                    return piece;
                }
            }
            for (StructurePiece piece : pieces) {
                String name = pieceFileName(piece.resourcePath());
                if (!name.contains("_end") && !name.contains("_side") && !name.contains("_top") && !name.contains("_middle")) {
                    return piece;
                }
            }
        }

        int total = pieces.stream().mapToInt(StructurePiece::weight).sum();
        int roll = random.nextInt(total);
        int current = 0;
        for (StructurePiece piece : pieces) {
            current += piece.weight();
            if (roll < current) {
                return piece;
            }
        }
        return pieces.getLast();
    }

    private static String pieceFileName(String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        return slash >= 0 ? resourcePath.substring(slash + 1) : resourcePath;
    }

    private static String sanitizeKey(String key) {
        return key.toLowerCase().replace('/', '_').replace('.', '_');
    }

    public static StructureRotation randomRotation(Random random) {
        StructureRotation[] values = StructureRotation.values();
        return values[random.nextInt(values.length)];
    }

    public static Mirror randomMirror(Random random) {
        return random.nextBoolean() ? Mirror.NONE : Mirror.LEFT_RIGHT;
    }
}
