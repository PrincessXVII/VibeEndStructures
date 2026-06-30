package ru.vibecraft.vibeendstructures.model;

import java.util.List;
import java.util.Map;

public record StructureDefinition(
        String id,
        String category,
        boolean enabled,
        PlacementType placementType,
        StartHeight startHeight,
        int spacing,
        int separation,
        int salt,
        Integer minDistanceFromOrigin,
        int minY,
        int jigsawSize,
        List<StructurePiece> pieces,
        List<Map<String, String>> jigsawLinks
) {
}
