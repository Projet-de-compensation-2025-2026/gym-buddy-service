package fr.projetcompensation.gymbuddy.matching;

import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MatchingMember(
        UUID userId,
        Instant optedAt,
        List<String> sports,
        String city,
        Double lat,
        Double lng,
        List<PreferredWindow> preferredWindows,
        ProfileVisibility visibility,
        Set<UUID> friendIds,
        Set<UUID> blockedIds) {

    public MatchingMember {
        sports = sports == null ? List.of() : List.copyOf(sports);
        preferredWindows = preferredWindows == null ? List.of() : List.copyOf(preferredWindows);
        visibility = visibility == null ? ProfileVisibility.PUBLIC : visibility;
        friendIds = friendIds == null ? Set.of() : Set.copyOf(friendIds);
        blockedIds = blockedIds == null ? Set.of() : Set.copyOf(blockedIds);
    }

    public boolean publicOrFriendsOk(MatchingMember other) {
        boolean friends = friendIds.contains(other.userId());
        boolean bothPublic = visibility == ProfileVisibility.PUBLIC && other.visibility == ProfileVisibility.PUBLIC;
        return friends || bothPublic;
    }
}
