package fr.projetcompensation.gymbuddy.profiles;

import java.util.List;
import java.util.UUID;

public record ProfilePatch(
        String handle,
        String displayName,
        String bio,
        boolean bioSet,
        ProfileVisibility visibility,
        List<String> sports,
        boolean sportsSet,
        ExperienceLevel experienceLevel,
        boolean experienceSet,
        String city,
        boolean citySet,
        Double lat,
        boolean latSet,
        Double lng,
        boolean lngSet,
        List<PreferredWindow> preferredWindows,
        boolean windowsSet,
        UUID avatarMediaId,
        boolean avatarSet) {}
