package ru.vibecraft.vibeendstructures.model;

import java.util.Random;

public record StartHeight(
        int surfaceOffset,
        Integer absoluteY,
        Integer minY,
        Integer maxY
) {
    public static StartHeight onSurface() {
        return new StartHeight(0, null, null, null);
    }

    public static StartHeight surfaceOffset(int offset) {
        return new StartHeight(offset, null, null, null);
    }

    public static StartHeight absolute(int y) {
        return new StartHeight(0, y, null, null);
    }

    public static StartHeight uniform(int min, int max) {
        return new StartHeight(0, null, min, max);
    }

    public int resolveGroundY(int surfaceY, Random random) {
        return surfaceY + surfaceOffset;
    }

    public int resolveAirY(Random random) {
        if (minY != null && maxY != null) {
            int low = Math.min(minY, maxY);
            int high = Math.max(minY, maxY);
            return low + random.nextInt(high - low + 1);
        }
        if (absoluteY != null) {
            return absoluteY;
        }
        return 60;
    }
}
