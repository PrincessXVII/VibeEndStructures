package ru.vibecraft.vibeendstructures.dragon.contribution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ContributionSnapshot(
        String arenaId,
        String dragonId,
        Instant finishedAt,
        List<ContributionResult> results
) {
    public static ContributionSnapshot empty(String arenaId, String dragonId) {
        return new ContributionSnapshot(arenaId, dragonId, Instant.now(), List.of());
    }

    public Optional<ContributionResult> top() {
        return results.stream().findFirst();
    }
}
