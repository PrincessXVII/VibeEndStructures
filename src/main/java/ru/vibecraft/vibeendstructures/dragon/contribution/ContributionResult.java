package ru.vibecraft.vibeendstructures.dragon.contribution;

import java.util.UUID;

public record ContributionResult(
        UUID playerUuid,
        String playerName,
        double damageDealt,
        double healingDone,
        double ccTimeApplied,
        int blocksPlaced,
        int revives,
        int deaths,
        double score,
        double contribution
) {
}
