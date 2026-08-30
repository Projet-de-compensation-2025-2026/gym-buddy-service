package fr.projetcompensation.gymbuddy.suggestions;

import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MemberSnapshot(
        UUID userId,
        String handle,
        String displayName,
        UserStatus status,
        ProfileVisibility visibility,
        List<String> sports,
        String city,
        Double lat,
        Double lng,
        List<PreferredWindow> preferredWindows,
        ExperienceLevel experienceLevel,
        UUID avatarMediaId,
        Instant createdAt) {

    public MemberSnapshot {
        sports = sports == null ? List.of() : List.copyOf(sports);
        preferredWindows = preferredWindows == null ? List.of() : List.copyOf(preferredWindows);
        visibility = visibility == null ? ProfileVisibility.PUBLIC : visibility;
        status = status == null ? UserStatus.ACTIVE : status;
    }

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }
}
