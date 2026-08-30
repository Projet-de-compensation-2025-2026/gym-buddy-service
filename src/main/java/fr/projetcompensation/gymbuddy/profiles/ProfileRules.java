package fr.projetcompensation.gymbuddy.profiles;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ProfileRules {

    static final int MAX_SPORTS = 12;
    static final int MIN_SPORT_LEN = 2;
    static final int MAX_SPORT_LEN = 32;
    static final int MAX_WINDOWS = 14;
    private static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    private ProfileRules() {}

    static void validate(ProfilePatch patch) {
        List<FieldIssue> issues = new ArrayList<>();
        if (patch.handle() != null && patch.handle().isBlank()) {
            issues.add(new FieldIssue("handle", "required"));
        }
        if (patch.displayName() != null && patch.displayName().isBlank()) {
            issues.add(new FieldIssue("displayName", "required"));
        }
        if (patch.sportsSet()) {
            List<String> sports = patch.sports() == null ? List.of() : patch.sports();
            if (sports.size() > MAX_SPORTS) {
                issues.add(new FieldIssue("sports", "maxItems"));
            }
            for (String sport : sports) {
                if (sport == null || sport.length() < MIN_SPORT_LEN || sport.length() > MAX_SPORT_LEN) {
                    issues.add(new FieldIssue("sports", "length"));
                    break;
                }
            }
        }
        if (patch.windowsSet()) {
            List<PreferredWindow> windows = patch.preferredWindows() == null ? List.of() : patch.preferredWindows();
            if (windows.size() > MAX_WINDOWS) {
                issues.add(new FieldIssue("preferredWindows", "maxItems"));
            }
            for (PreferredWindow window : windows) {
                if (window.weekday() < 0 || window.weekday() > 6) {
                    issues.add(new FieldIssue("preferredWindows.weekday", "range"));
                    break;
                }
                if (!TIME.matcher(window.start()).matches()
                        || !TIME.matcher(window.end()).matches()) {
                    issues.add(new FieldIssue("preferredWindows", "format"));
                    break;
                }
            }
        }
        if (patch.latSet() && patch.lat() != null && (patch.lat() < -90 || patch.lat() > 90)) {
            issues.add(new FieldIssue("lat", "range"));
        }
        if (patch.lngSet() && patch.lng() != null && (patch.lng() < -180 || patch.lng() > 180)) {
            issues.add(new FieldIssue("lng", "range"));
        }
        if (!issues.isEmpty()) {
            throw new AuthException(
                    fr.projetcompensation.gymbuddy.auth.ErrorCode.VALIDATION, "profile is not valid", issues);
        }
    }
}
