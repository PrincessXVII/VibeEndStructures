package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.vibecraft.vibeendstructures.model.PlacementType;
import ru.vibecraft.vibeendstructures.model.StartHeight;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;
import ru.vibecraft.vibeendstructures.model.StructurePiece;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StructureConfigLoader {

    private StructureConfigLoader() {
    }

    public static StructureDefinition load(File structureDir, File configFile) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        String id = structureDir.getName();

        String category = yaml.getString("category", "structure");
        boolean enabled = yaml.getBoolean("enabled", true);
        PlacementType placementType = PlacementType.fromConfig(yaml.getString("placement-type", "ground"));
        int minY = yaml.getInt("min-y", 45);

        ConfigurationSection placement = yaml.getConfigurationSection("placement");
        int spacing = placement != null ? placement.getInt("spacing", 32) : 32;
        int separation = placement != null ? placement.getInt("separation", 8) : 8;
        int salt = placement != null ? placement.getInt("salt", id.hashCode()) : id.hashCode();
        Integer minDist = placement != null && placement.contains("min-distance-from-origin")
                ? placement.getInt("min-distance-from-origin")
                : null;

        StartHeight startHeight = parseStartHeight(yaml.getConfigurationSection("start-height"), placementType);

        int jigsawSize = yaml.getInt("jigsaw-size", 1);
        List<StructurePiece> pieces = parsePieces(yaml.getMapList("pieces"), id);
        List<Map<String, String>> jigsawLinks = parseJigsawLinks(yaml.getMapList("jigsaw-links"));

        return new StructureDefinition(
                id,
                category,
                enabled,
                placementType,
                startHeight,
                spacing,
                separation,
                salt,
                minDist,
                minY,
                jigsawSize,
                pieces,
                jigsawLinks
        );
    }

    private static StartHeight parseStartHeight(ConfigurationSection section, PlacementType placementType) {
        if (section == null) {
            return placementType == PlacementType.AIR ? StartHeight.absolute(60) : StartHeight.onSurface();
        }

        if (section.contains("min") && section.contains("max")) {
            return StartHeight.uniform(section.getInt("min"), section.getInt("max"));
        }
        if (section.contains("absolute")) {
            int absolute = section.getInt("absolute");
            return placementType == PlacementType.GROUND
                    ? StartHeight.surfaceOffset(absolute)
                    : StartHeight.absolute(absolute);
        }
        if (section.contains("offset")) {
            return StartHeight.surfaceOffset(section.getInt("offset"));
        }

        return placementType == PlacementType.AIR ? StartHeight.absolute(60) : StartHeight.onSurface();
    }

    private static List<StructurePiece> parsePieces(List<Map<?, ?>> rawPieces, String structureId) {
        List<StructurePiece> pieces = new ArrayList<>();
        if (rawPieces == null) {
            return pieces;
        }

        for (Map<?, ?> raw : rawPieces) {
            String file = String.valueOf(raw.get("file"));
            int weight = raw.containsKey("weight") ? ((Number) raw.get("weight")).intValue() : 1;
            String pool = raw.containsKey("pool") ? String.valueOf(raw.get("pool")) : "";
            String location = raw.containsKey("location") ? String.valueOf(raw.get("location")) : "";
            String resourcePath = structureId + "/" + file;
            pieces.add(new StructurePiece(resourcePath, weight, pool, location));
        }
        return pieces;
    }

    private static List<Map<String, String>> parseJigsawLinks(List<Map<?, ?>> rawLinks) {
        List<Map<String, String>> links = new ArrayList<>();
        if (rawLinks == null) {
            return links;
        }
        for (Map<?, ?> raw : rawLinks) {
            Map<String, String> link = new HashMap<>();
            link.put("from", String.valueOf(raw.get("from")));
            link.put("to", String.valueOf(raw.get("to")));
            links.add(link);
        }
        return links;
    }
}
