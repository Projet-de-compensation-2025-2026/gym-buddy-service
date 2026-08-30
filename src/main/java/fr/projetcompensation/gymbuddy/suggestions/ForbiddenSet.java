package fr.projetcompensation.gymbuddy.suggestions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ForbiddenSet {

    private ForbiddenSet() {}

    public static Set<UUID> of(
            UUID viewerId, Set<UUID> friends, Set<UUID> pending, Set<UUID> blocked, Set<UUID> dismissed) {
        Set<UUID> forbidden = new HashSet<>();
        forbidden.add(viewerId);
        if (friends != null) {
            forbidden.addAll(friends);
        }
        if (pending != null) {
            forbidden.addAll(pending);
        }
        if (blocked != null) {
            forbidden.addAll(blocked);
        }
        if (dismissed != null) {
            forbidden.addAll(dismissed);
        }
        return Set.copyOf(forbidden);
    }

    public static boolean excluded(UUID candidateId, Set<UUID> forbidden, MemberSnapshot snapshot) {
        if (candidateId == null || forbidden.contains(candidateId)) {
            return true;
        }
        return snapshot == null || !snapshot.active();
    }
}
