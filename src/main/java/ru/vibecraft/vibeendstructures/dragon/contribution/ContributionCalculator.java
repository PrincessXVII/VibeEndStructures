package ru.vibecraft.vibeendstructures.dragon.contribution;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class ContributionCalculator {

    public List<ContributionResult> calculate(Collection<ContributionData> contributions) {
        double totalScore = contributions.stream()
                .mapToDouble(ContributionData::score)
                .sum();

        return contributions.stream()
                .map(data -> toResult(data, totalScore))
                .sorted(Comparator.comparingDouble(ContributionResult::contribution).reversed()
                        .thenComparing(Comparator.comparingDouble(ContributionResult::damageDealt).reversed())
                        .thenComparing(ContributionResult::playerName))
                .toList();
    }

    private ContributionResult toResult(ContributionData data, double totalScore) {
        double score = data.score();
        double contribution = totalScore <= 0 ? 0 : score / totalScore;
        return new ContributionResult(
                data.playerUuid(),
                data.playerName(),
                data.damageDealt(),
                data.healingDone(),
                data.ccTimeApplied(),
                data.blocksPlaced(),
                data.revives(),
                data.deaths(),
                score,
                contribution
        );
    }
}
