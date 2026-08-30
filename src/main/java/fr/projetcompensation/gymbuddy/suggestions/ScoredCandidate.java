package fr.projetcompensation.gymbuddy.suggestions;

import java.util.List;
import java.util.UUID;

public record ScoredCandidate(
        UUID userId,
        double score,
        String reason,
        int mutualFriends,
        List<String> sharedSports,
        FeatureVector features) {

    public ScoredCandidate {
        sharedSports = sharedSports == null ? List.of() : List.copyOf(sharedSports);
    }
}
