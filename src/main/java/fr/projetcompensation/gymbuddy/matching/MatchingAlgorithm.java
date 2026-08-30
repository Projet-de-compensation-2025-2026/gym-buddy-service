package fr.projetcompensation.gymbuddy.matching;

import fr.projetcompensation.gymbuddy.suggestions.GeoScore;
import fr.projetcompensation.gymbuddy.suggestions.SportsOverlap;
import fr.projetcompensation.gymbuddy.suggestions.WindowOverlap;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MatchingAlgorithm {

    public static final double FRIEND_BONUS = 0.2;

    private MatchingAlgorithm() {}

    public static List<MatchingEdge> edges(List<MatchingMember> members) {
        List<MatchingEdge> edges = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            MatchingMember a = members.get(i);
            for (int j = i + 1; j < members.size(); j++) {
                MatchingMember b = members.get(j);
                edge(a, b).ifPresent(edges::add);
            }
        }
        return List.copyOf(edges);
    }

    public static Optional<MatchingEdge> edge(MatchingMember a, MatchingMember b) {
        if (a.userId().equals(b.userId())) {
            return Optional.empty();
        }
        if (a.blockedIds().contains(b.userId()) || b.blockedIds().contains(a.userId())) {
            return Optional.empty();
        }
        if (!a.publicOrFriendsOk(b)) {
            return Optional.empty();
        }
        List<String> shared = SportsOverlap.sharedInOrder(a.sports(), b.sports());
        if (shared.isEmpty()) {
            return Optional.empty();
        }
        Optional<WindowOverlap.Overlap> overlap = WindowOverlap.longestAtLeast(
                a.preferredWindows(), b.preferredWindows(), WindowOverlap.MATCH_MIN_MINUTES);
        if (overlap.isEmpty()) {
            return Optional.empty();
        }
        double j = SportsOverlap.jaccard(a.sports(), b.sports());
        double t = WindowOverlap.timeFeature(a.preferredWindows(), b.preferredWindows());
        double g = GeoScore.feature(a.lat(), a.lng(), b.lat(), b.lng(), a.city(), b.city());
        boolean friends = a.friendIds().contains(b.userId());
        double weight = j + t + g + (friends ? FRIEND_BONUS : 0.0);
        java.time.Instant earlier = a.optedAt().isBefore(b.optedAt()) ? a.optedAt() : b.optedAt();
        return Optional.of(new MatchingEdge(a.userId(), b.userId(), weight, shared.getFirst(), overlap.get(), earlier));
    }

    public static List<ProposedMatch> greedy(List<MatchingMember> members, LocalDate weekStart) {
        List<MatchingEdge> edges = new ArrayList<>(edges(members));
        edges.sort(Comparator.comparingDouble(MatchingEdge::weight)
                .reversed()
                .thenComparing(MatchingEdge::earlierOptIn)
                .thenComparing(MatchingEdge::left)
                .thenComparing(MatchingEdge::right));
        Set<UUID> taken = new HashSet<>();
        List<ProposedMatch> matches = new ArrayList<>();
        for (MatchingEdge edge : edges) {
            if (taken.contains(edge.left()) || taken.contains(edge.right())) {
                continue;
            }
            taken.add(edge.left());
            taken.add(edge.right());
            matches.add(new ProposedMatch(
                    edge.left(),
                    edge.right(),
                    edge.weight(),
                    edge.activity(),
                    WindowOverlap.midpointInstant(weekStart, edge.overlap()),
                    edge.overlap().durationMinCapped(),
                    weekStart,
                    null));
        }
        return List.copyOf(matches);
    }
}
