package fr.projetcompensation.gymbuddy.suggestions;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface SuggestionGraph {

    MemberSnapshot requireMember(UUID userId);

    Set<UUID> acceptedFriendIds(UUID userId);

    Set<UUID> pendingIds(UUID userId);

    Set<UUID> blockedIds(UUID userId);

    Set<UUID> dismissedIds(UUID viewerId, Instant now);

    Instant latestRelationshipChange(UUID userId);

    Map<UUID, Set<UUID>> neighbors(Collection<UUID> userIds);

    List<MemberSnapshot> membersByIds(Collection<UUID> ids);

    Set<UUID> sameCityAndSport(MemberSnapshot viewer, int limit);

    Set<UUID> recentCoParticipants(UUID userId, Instant since);

    List<MemberSnapshot> activeMembers();
}
