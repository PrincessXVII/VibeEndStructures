package ru.vibecraft.vibeendstructures.model;

public enum PlacementType {
    GROUND,
    AIR;

    public static PlacementType fromConfig(String value) {
        if (value == null) {
            return GROUND;
        }
        return switch (value.toLowerCase()) {
            case "air" -> AIR;
            default -> GROUND;
        };
    }
}
