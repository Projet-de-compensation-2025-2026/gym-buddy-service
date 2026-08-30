package fr.projetcompensation.gymbuddy.suggestions;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CandidateGenerator {

    private CandidateGenerator() {}

    public static Set<UUID> generate(
            UUID viewerId,
            Set<UUID> friendsOfFriends,
            Set<UUID> sameCityAndSport,
            Set<UUID> coParticipants,
            Set<UUID> forbidden) {
        LinkedHashSet<UUID> candidates = new LinkedHashSet<>();
        addUntilCap(candidates, friendsOfFriends, viewerId, forbidden);
        addUntilCap(candidates, sameCityAndSport, viewerId, forbidden);
        addUntilCap(candidates, coParticipants, viewerId, forbidden);
        return Set.copyOf(candidates);
    }

    public static Set<UUID> friendsOfFriends(UUID viewerId, Map<UUID, Set<UUID>> neighbors) {
        Set<UUID> friends = neighbors.getOrDefault(viewerId, Set.of());
        LinkedHashSet<UUID> fof = new LinkedHashSet<>();
        for (UUID friend : friends) {
            for (UUID hop : neighbors.getOrDefault(friend, Set.of())) {
                if (!hop.equals(viewerId) && !friends.contains(hop)) {
                    fof.add(hop);
                }
            }
        }
        return fof;
    }

    private static void addUntilCap(LinkedHashSet<UUID> into, Set<UUID> source, UUID viewerId, Set<UUID> forbidden) {
        if (source == null) {
            return;
        }
        for (UUID id : source) {
            if (into.size() >= SuggestionScorer.CANDIDATE_CAP) {
                return;
            }
            if (id.equals(viewerId) || forbidden.contains(id)) {
                continue;
            }
            into.add(id);
        }
    }
}
