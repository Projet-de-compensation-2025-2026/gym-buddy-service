package fr.projetcompensation.gymbuddy.suggestions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SuggestionScorer {

    public static final int CANDIDATE_CAP = 200;

    private final SuggestionWeights weights;

    public SuggestionScorer(SuggestionWeights weights) {
        this.weights = weights == null ? SuggestionWeights.defaults() : weights;
    }

    public List<ScoredCandidate> score(
            MemberSnapshot viewer, List<MemberSnapshot> candidates, Map<UUID, Set<UUID>> neighbors) {
        Set<UUID> viewerFriends = neighbors.getOrDefault(viewer.userId(), Set.of());
        Map<UUID, Double> rawMutual = new HashMap<>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (MemberSnapshot candidate : candidates) {
            Set<UUID> theirs = neighbors.getOrDefault(candidate.userId(), Set.of());
            Set<UUID> mutual = AdamicAdar.mutual(viewerFriends, theirs);
            double raw = AdamicAdar.raw(mutual, id -> AdamicAdar.degree(id, neighbors));
            rawMutual.put(candidate.userId(), raw);
            min = Math.min(min, raw);
            max = Math.max(max, raw);
        }
        if (candidates.isEmpty()) {
            min = 0.0;
            max = 0.0;
        }
        List<ScoredCandidate> scored = new ArrayList<>();
        for (MemberSnapshot candidate : candidates) {
            Set<UUID> theirs = neighbors.getOrDefault(candidate.userId(), Set.of());
            Set<UUID> mutual = AdamicAdar.mutual(viewerFriends, theirs);
            double mHat = AdamicAdar.minMax(rawMutual.getOrDefault(candidate.userId(), 0.0), min, max);
            List<String> shared = SportsOverlap.sharedInOrder(viewer.sports(), candidate.sports());
            FeatureVector features = new FeatureVector(
                    mHat,
                    SportsOverlap.jaccard(viewer.sports(), candidate.sports()),
                    GeoScore.feature(
                            viewer.lat(),
                            viewer.lng(),
                            candidate.lat(),
                            candidate.lng(),
                            viewer.city(),
                            candidate.city()),
                    WindowOverlap.timeFeature(viewer.preferredWindows(), candidate.preferredWindows()),
                    ExperienceCloseness.feature(viewer.experienceLevel(), candidate.experienceLevel()));
            String reason = PrimaryReason.of(features, weights, mutual.size(), shared, candidate.city());
            scored.add(new ScoredCandidate(
                    candidate.userId(), features.score(weights), reason, mutual.size(), shared, features));
        }
        scored.sort(
                Comparator.comparingDouble(ScoredCandidate::score).reversed().thenComparing(ScoredCandidate::userId));
        return List.copyOf(scored);
    }

    public List<ScoredCandidate> topK(List<ScoredCandidate> ranked, int k) {
        if (k <= 0 || ranked.isEmpty()) {
            return List.of();
        }
        return ranked.subList(0, Math.min(k, ranked.size()));
    }
}
