package fr.projetcompensation.gymbuddy.profiles;

import java.util.List;
import java.util.UUID;

public record Profile(
        UUID userId,
        String displayName,
        String bio,
        ProfileVisibility visibility,
        List<String> sports,
        ExperienceLevel experienceLevel,
        String city,
        Double lat,
        Double lng,
        List<PreferredWindow> preferredWindows,
        UUID avatarMediaId) {

    public Profile {
        sports = sports == null ? List.of() : List.copyOf(sports);
        preferredWindows = preferredWindows == null ? List.of() : List.copyOf(preferredWindows);
        visibility = visibility == null ? ProfileVisibility.PUBLIC : visibility;
    }

    public static Profile created(UUID userId, String displayName) {
        return new Profile(
                userId,
                displayName,
                null,
                ProfileVisibility.PUBLIC,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                null);
    }
}
