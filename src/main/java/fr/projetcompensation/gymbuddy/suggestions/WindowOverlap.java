package fr.projetcompensation.gymbuddy.suggestions;

import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public final class WindowOverlap {

    public static final int MATCH_MIN_MINUTES = 60;

    private WindowOverlap() {}

    /**
     * Hours per week of overlapping preferred windows, divided by 10 and capped at 1.
     */
    public static double timeFeature(List<PreferredWindow> left, List<PreferredWindow> right) {
        int minutes = overlapMinutesPerWeek(left, right);
        return Math.min(1.0, (minutes / 60.0) / 10.0);
    }

    public static int overlapMinutesPerWeek(List<PreferredWindow> left, List<PreferredWindow> right) {
        int total = 0;
        for (int weekday = 0; weekday <= 6; weekday++) {
            total += longestOverlapMinutes(left, right, weekday);
        }
        return total;
    }

    public static Optional<Overlap> longestAtLeast(
            List<PreferredWindow> left, List<PreferredWindow> right, int minMinutes) {
        Overlap best = null;
        for (int weekday = 0; weekday <= 6; weekday++) {
            int minutes = longestOverlapMinutes(left, right, weekday);
            if (minutes < minMinutes) {
                continue;
            }
            int start = overlapStartMinutes(left, right, weekday);
            if (best == null || minutes > best.minutes() || (minutes == best.minutes() && weekday < best.weekday())) {
                best = new Overlap(weekday, start, start + minutes, minutes);
            }
        }
        return Optional.ofNullable(best);
    }

    public static LocalDateTime midpointUtc(LocalDate weekMonday, Overlap overlap) {
        LocalDate day = weekMonday.plusDays(daysFromMonday(overlap.weekday()));
        LocalTime mid = LocalTime.ofSecondOfDay((overlap.startMinutes() * 60L + overlap.endMinutes() * 60L) / 2);
        return LocalDateTime.of(day, mid);
    }

    public static java.time.Instant midpointInstant(LocalDate weekMonday, Overlap overlap) {
        return midpointUtc(weekMonday, overlap).toInstant(ZoneOffset.UTC);
    }

    public static long daysFromMonday(int weekdaySundayZero) {
        DayOfWeek day = dayOfWeek(weekdaySundayZero);
        return day.getValue() - 1L;
    }

    public static DayOfWeek dayOfWeek(int weekdaySundayZero) {
        if (weekdaySundayZero == 0) {
            return DayOfWeek.SUNDAY;
        }
        return DayOfWeek.of(weekdaySundayZero);
    }

    private static int longestOverlapMinutes(List<PreferredWindow> left, List<PreferredWindow> right, int weekday) {
        int best = 0;
        for (PreferredWindow a : windowsOn(left, weekday)) {
            for (PreferredWindow b : windowsOn(right, weekday)) {
                int start = Math.max(minutes(a.start()), minutes(b.start()));
                int end = Math.min(minutes(a.end()), minutes(b.end()));
                best = Math.max(best, Math.max(0, end - start));
            }
        }
        return best;
    }

    private static int overlapStartMinutes(List<PreferredWindow> left, List<PreferredWindow> right, int weekday) {
        int best = 0;
        int bestStart = 0;
        for (PreferredWindow a : windowsOn(left, weekday)) {
            for (PreferredWindow b : windowsOn(right, weekday)) {
                int start = Math.max(minutes(a.start()), minutes(b.start()));
                int end = Math.min(minutes(a.end()), minutes(b.end()));
                int length = Math.max(0, end - start);
                if (length > best) {
                    best = length;
                    bestStart = start;
                }
            }
        }
        return bestStart;
    }

    private static List<PreferredWindow> windowsOn(List<PreferredWindow> windows, int weekday) {
        if (windows == null) {
            return List.of();
        }
        return windows.stream()
                .filter(window -> window != null && window.weekday() == weekday && valid(window))
                .toList();
    }

    private static boolean valid(PreferredWindow window) {
        int start = minutes(window.start());
        int end = minutes(window.end());
        return start >= 0 && end > start;
    }

    private static int minutes(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        return time.getHour() * 60 + time.getMinute();
    }

    public record Overlap(int weekday, int startMinutes, int endMinutes, int minutes) {
        public int durationMinCapped() {
            return Math.min(1440, Math.max(1, minutes));
        }

        public Duration duration() {
            return Duration.ofMinutes(minutes);
        }
    }
}
