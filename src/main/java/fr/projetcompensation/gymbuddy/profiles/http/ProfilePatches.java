package fr.projetcompensation.gymbuddy.profiles.http;

import fr.projetcompensation.gymbuddy.openapi.model.PatchProfileRequest;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfilePatch;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import java.util.List;

final class ProfilePatches {

    private ProfilePatches() {}

    static ProfilePatch fromApi(PatchProfileRequest request) {
        boolean sportsSet = request.getSports() != null;
        boolean windowsSet = request.getPreferredWindows() != null;
        return new ProfilePatch(
                request.getHandle(),
                request.getDisplayName(),
                request.getBio(),
                request.getBio() != null,
                visibility(request.getVisibility()),
                sportsSet ? request.getSports() : List.of(),
                sportsSet,
                experience(request.getExperienceLevel()),
                request.getExperienceLevel() != null,
                request.getCity(),
                request.getCity() != null,
                request.getLat(),
                request.getLat() != null,
                request.getLng(),
                request.getLng() != null,
                windowsSet ? windows(request) : List.of(),
                windowsSet,
                request.getAvatarMediaId(),
                request.getAvatarMediaId() != null);
    }

    private static ProfileVisibility visibility(PatchProfileRequest.VisibilityEnum value) {
        if (value == null) {
            return null;
        }
        return ProfileVisibility.fromWire(value.getValue());
    }

    private static ExperienceLevel experience(PatchProfileRequest.ExperienceLevelEnum value) {
        if (value == null) {
            return null;
        }
        return ExperienceLevel.fromWire(value.getValue());
    }

    private static List<PreferredWindow> windows(PatchProfileRequest request) {
        return request.getPreferredWindows().stream()
                .map(window -> new PreferredWindow(window.getWeekday(), window.getStart(), window.getEnd()))
                .toList();
    }
}
