package fr.projetcompensation.gymbuddy.suggestions;

import java.util.List;

public final class PrimaryReason {

    private PrimaryReason() {}

    public static String of(
            FeatureVector features,
            SuggestionWeights weights,
            int mutualCount,
            List<String> sharedSports,
            String city) {
        double m = features.weightedMutual(weights);
        double j = features.weightedJaccard(weights);
        double t = features.weightedTime(weights);
        double g = features.weightedGeo(weights);
        double e = features.weightedExperience(weights);
        double best = Math.max(m, Math.max(j, Math.max(t, Math.max(g, e))));
        if (best <= 0.0) {
            return "people you may know";
        }
        if (m == best && mutualCount > 0) {
            return mutualCount == 1 ? "1 mutual friend" : mutualCount + " mutual friends";
        }
        if (j == best && sharedSports != null && !sharedSports.isEmpty()) {
            return sharedSports.getFirst();
        }
        if (t == best) {
            return "same gym times";
        }
        if (g == best) {
            if (city != null && !city.isBlank()) {
                return city.trim();
            }
            return "near you";
        }
        if (e == best) {
            return "similar experience";
        }
        if (mutualCount > 0) {
            return mutualCount == 1 ? "1 mutual friend" : mutualCount + " mutual friends";
        }
        return "people you may know";
    }
}
