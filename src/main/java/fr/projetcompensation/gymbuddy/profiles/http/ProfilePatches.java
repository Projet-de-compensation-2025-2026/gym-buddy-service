package fr.projetcompensation.gymbuddy.profiles.http;

import com.fasterxml.jackson.databind.JsonNode;
import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.openapi.model.PatchProfileRequest;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfilePatch;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import java.util.List;
import org.springframework.lang.Nullable;

final class ProfilePatches {

    private ProfilePatches() {}

    static ProfilePatch fromApi(PatchProfileRequest request, @Nullable JsonNode raw) {
        JsonNode json = raw == null || raw.isNull() || raw.isMissingNode() ? null : raw;
        boolean sportsSet = has(json, "sports");
        boolean windowsSet = has(json, "preferredWindows");
        if (has(json, "experienceLevel")
                && json.get("experienceLevel") != null
                && !json.get("experienceLevel").isNull()) {
            requireExperience(json.get("experienceLevel").asText());
        }
        return new ProfilePatch(
                request.getHandle(),
                request.getDisplayName(),
                request.getBio(),
                has(json, "bio"),
                visibility(request.getVisibility()),
                sportsSet ? listOrEmpty(request.getSports()) : List.of(),
                sportsSet,
                experience(request.getExperienceLevel()),
                has(json, "experienceLevel"),
                request.getCity(),
                has(json, "city"),
                request.getLat(),
                has(json, "lat"),
                request.getLng(),
                has(json, "lng"),
                windowsSet ? windows(request) : List.of(),
                windowsSet,
                request.getAvatarMediaId(),
                has(json, "avatarMediaId"));
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
        if (request.getPreferredWindows() == null) {
            return List.of();
        }
        return request.getPreferredWindows().stream()
                .map(window -> new PreferredWindow(window.getWeekday(), window.getStart(), window.getEnd()))
                .toList();
    }

    private static boolean has(JsonNode json, String field) {
        return json != null && json.has(field);
    }

    private static List<String> listOrEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static void requireExperience(String wire) {
        try {
            ExperienceLevel.fromWire(wire);
        } catch (RuntimeException ex) {
            throw AuthException.validation("experienceLevel is not allowed", new FieldIssue("experienceLevel", "enum"));
        }
    }
}
