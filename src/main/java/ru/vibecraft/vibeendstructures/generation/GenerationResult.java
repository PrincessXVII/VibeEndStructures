package ru.vibecraft.vibeendstructures.generation;

public enum GenerationResult {
    PLACED("placed", false),
    NOT_APPLICABLE("not_applicable", false),
    SKIPPED("other", true),
    SKIPPED_TOO_CLOSE("too_close_origin", true),
    SKIPPED_NO_SURFACE("no_surface", true),
    SKIPPED_OCCUPIED("occupied_or_unnatural", true),
    SKIPPED_PLACE_FAILED("place_failed", true);

    private final String reasonKey;
    private final boolean skipped;

    GenerationResult(String reasonKey, boolean skipped) {
        this.reasonKey = reasonKey;
        this.skipped = skipped;
    }

    public String reasonKey() {
        return reasonKey;
    }

    public boolean isSkipped() {
        return skipped;
    }
}
