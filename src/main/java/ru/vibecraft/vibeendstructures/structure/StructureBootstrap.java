package ru.vibecraft.vibeendstructures.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class StructureBootstrap {

    private StructureBootstrap() {
    }

    public static void ensureDefaults(JavaPlugin plugin, File structuresRoot) {
        if (!structuresRoot.exists() && !structuresRoot.mkdirs()) {
            plugin.getLogger().warning("Failed to create structures directory: " + structuresRoot.getAbsolutePath());
            return;
        }

        try (InputStream in = plugin.getResource("manifest.json")) {
            if (in == null) {
                plugin.getLogger().warning("manifest.json not found — cannot bootstrap structure configs");
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray structures = root.getAsJsonArray("structures");

            for (JsonElement element : structures) {
                JsonObject obj = element.getAsJsonObject();
                String id = obj.get("id").getAsString();
                File structureDir = new File(structuresRoot, id);
                File nbtDir = new File(structureDir, id);
                File configFile = new File(structureDir, "config.yml");

                if (!nbtDir.exists()) {
                    nbtDir.mkdirs();
                }

                if (!configFile.exists()) {
                    writeDefaultConfig(configFile, obj);
                    plugin.getLogger().info("Created default config for structure: " + id);
                }

                copyMissingNbtPieces(plugin, obj, nbtDir);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to bootstrap structure configs", ex);
        }
    }

    private static void writeDefaultConfig(File configFile, JsonObject obj) throws Exception {
        String id = obj.get("id").getAsString();
        String category = obj.get("category").getAsString();
        String terrainAdaptation = obj.has("terrain_adaptation") ? obj.get("terrain_adaptation").getAsString() : "beard_thin";
        String placementType = "none".equals(terrainAdaptation) ? "air" : "ground";

        int minY = 45;
        if (obj.has("y_allowance") && obj.get("y_allowance").isJsonObject()) {
            minY = intOrDefault(obj.getAsJsonObject("y_allowance"), "min_y_allowed", 45);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("category", category);
        yaml.set("placement-type", placementType);
        yaml.set("min-y", minY);

        JsonObject placement = obj.getAsJsonObject("placement");
        yaml.set("placement.spacing", intOrDefault(placement, "spacing", 32));
        yaml.set("placement.separation", intOrDefault(placement, "separation", 8));
        yaml.set("placement.salt", intOrDefault(placement, "salt", id.hashCode()));
        if (placement.has("min_distance_from_world_origin") && !placement.get("min_distance_from_world_origin").isJsonNull()) {
            yaml.set("placement.min-distance-from-origin", placement.get("min_distance_from_world_origin").getAsInt());
        }

        writeStartHeight(yaml, obj, placementType);

        yaml.set("jigsaw-size", intOrDefault(obj, "jigsaw_size", 1));

        List<Map<String, Object>> pieces = new ArrayList<>();
        for (JsonElement pieceEl : obj.getAsJsonArray("pieces")) {
            JsonObject piece = pieceEl.getAsJsonObject();
            String file = pieceFileName(piece.get("file").getAsString());
            Map<String, Object> pieceMap = new HashMap<>();
            pieceMap.put("file", file);
            pieceMap.put("weight", intOrDefault(piece, "weight", 1));
            if (piece.has("pool")) {
                pieceMap.put("pool", piece.get("pool").getAsString());
            }
            if (piece.has("location")) {
                pieceMap.put("location", piece.get("location").getAsString());
            }
            pieces.add(pieceMap);
        }
        yaml.set("pieces", pieces);

        List<Map<String, String>> links = new ArrayList<>();
        if (obj.has("jigsaw_links")) {
            for (JsonElement linkEl : obj.getAsJsonArray("jigsaw_links")) {
                JsonObject link = linkEl.getAsJsonObject();
                links.add(Map.of(
                        "from", link.get("from").getAsString(),
                        "to", link.get("to").getAsString()
                ));
            }
        }
        yaml.set("jigsaw-links", links);

        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }
        yaml.save(configFile);
    }

    private static void writeStartHeight(YamlConfiguration yaml, JsonObject obj, String placementType) {
        if (!obj.has("start_height") || !obj.get("start_height").isJsonObject()) {
            yaml.set("start-height.offset", 0);
            return;
        }

        JsonObject startHeight = obj.getAsJsonObject("start_height");
        if (startHeight.has("type") && "minecraft:uniform".equals(startHeight.get("type").getAsString())) {
            int min = startHeight.getAsJsonObject("min_inclusive").getAsJsonObject("absolute").getAsInt();
            int max = startHeight.getAsJsonObject("max_inclusive").getAsJsonObject("absolute").getAsInt();
            yaml.set("start-height.min", min);
            yaml.set("start-height.max", max);
            return;
        }

        if (startHeight.has("absolute")) {
            int absolute = startHeight.get("absolute").getAsInt();
            if ("ground".equals(placementType)) {
                yaml.set("start-height.offset", absolute);
            } else {
                yaml.set("start-height.absolute", absolute);
            }
            return;
        }

        yaml.set("start-height.offset", 0);
    }

    private static void copyMissingNbtPieces(JavaPlugin plugin, JsonObject obj, File nbtDir) {
        for (JsonElement pieceEl : obj.getAsJsonArray("pieces")) {
            JsonObject piece = pieceEl.getAsJsonObject();
            String fileName = pieceFileName(piece.get("file").getAsString());
            File target = new File(nbtDir, fileName);
            if (target.exists()) {
                continue;
            }

            String resourcePath = toResourcePath(piece.get("file").getAsString());
            try (InputStream nbt = plugin.getResource("structures/" + resourcePath)) {
                if (nbt == null) {
                    continue;
                }
                java.nio.file.Files.copy(nbt, target.toPath());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.FINE, "Could not copy NBT " + fileName + " from jar", ex);
            }
        }
    }

    private static String pieceFileName(String manifestFile) {
        String path = toResourcePath(manifestFile);
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String toResourcePath(String manifestFile) {
        if (manifestFile.startsWith("nbt/")) {
            return manifestFile.substring(4);
        }
        return manifestFile;
    }

    private static int intOrDefault(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : defaultValue;
    }
}
