package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FS-EVT-13 / 50-Algorithms/03-User-matching.md problem 1.
 *
 * <p>{@code M = 0.30 A + 0.25 J + 0.20 G + 0.15 T + 0.10 H}. Capacity is not in the score.
 */
public final class MatchingScore {

    static final double WEIGHT_APPLIED = 0.30;
    static final double WEIGHT_SPORT = 0.25;
    static final double WEIGHT_GEO = 0.20;
    static final double WEIGHT_WINDOW = 0.15;
    static final double WEIGHT_HISTORY = 0.10;
    private static final double GEO_SCALE_KM = 50.0;

    private MatchingScore() {}

    public static double score(Event event, Profile candidate, boolean applied, int coAttendance) {
        double appliedTerm = applied ? 1.0 : 0.3;
        return clamp(WEIGHT_APPLIED * appliedTerm
                + WEIGHT_SPORT * sport(candidate, event.activity())
                + WEIGHT_GEO * geo(event, candidate)
                + WEIGHT_WINDOW * window(candidate, event.startsAt())
                + WEIGHT_HISTORY * history(coAttendance));
    }

    static double sport(Profile candidate, String activity) {
        if (activity == null || activity.isBlank()) {
            return 0;
        }
        List<String> sports = candidate.sports();
        if (sports.isEmpty()) {
            return 0;
        }
        String needle = activity.trim().toLowerCase(Locale.ROOT);
        Set<String> set = new HashSet<>();
        for (String sport : sports) {
            if (sport != null && !sport.isBlank()) {
                set.add(sport.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (set.contains(needle)) {
            return 1;
        }
        return 0;
    }

    static double geo(Event event, Profile candidate) {
        if (event.lat() == null || event.lng() == null || candidate.lat() == null || candidate.lng() == null) {
            return 0;
        }
        double km = haversineKm(event.lat(), event.lng(), candidate.lat(), candidate.lng());
        return clamp(1.0 - (km / GEO_SCALE_KM));
    }

    static double window(Profile candidate, Instant startsAt) {
        if (candidate.preferredWindows().isEmpty() || startsAt == null) {
            return 0;
        }
        ZonedDateTime zoned = startsAt.atZone(ZoneOffset.UTC);
        int weekday = zoned.getDayOfWeek().getValue() % 7;
        LocalTime time = zoned.toLocalTime();
        for (PreferredWindow preferred : candidate.preferredWindows()) {
            if (preferred.weekday() != weekday) {
                continue;
            }
            LocalTime start = LocalTime.parse(preferred.start());
            LocalTime end = LocalTime.parse(preferred.end());
            if (!end.isAfter(start)) {
                if (!time.isBefore(start) || time.isBefore(end)) {
                    return 1;
                }
            } else if (!time.isBefore(start) && time.isBefore(end)) {
                return 1;
            }
        }
        return 0;
    }

    static double history(int coAttendance) {
        if (coAttendance <= 0) {
            return 0;
        }
        return Math.min(coAttendance / 5.0, 1.0);
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2)
                        * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private static double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }
}
