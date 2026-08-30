package fr.projetcompensation.gymbuddy.events;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** RFC 5545 weekly RRULE subset: FREQ=WEEKLY;BYDAY=... and optional UNTIL. */
public final class WeeklyRrule {

    public static final int WINDOW_DAYS = 90;

    private static final DateTimeFormatter UNTIL_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter UNTIL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final Set<DayOfWeek> byDay;
    private final Instant until;

    private WeeklyRrule(Set<DayOfWeek> byDay, Instant until) {
        this.byDay = Set.copyOf(byDay);
        this.until = until;
    }

    public Set<DayOfWeek> byDay() {
        return byDay;
    }

    public Instant until() {
        return until;
    }

    public static WeeklyRrule parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("recurrence is empty");
        }
        String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, "RRULE:", 0, 6)) {
            trimmed = trimmed.substring(6);
        }
        boolean weekly = false;
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Instant until = null;
        for (String part : trimmed.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("invalid RRULE");
            }
            String key = part.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();
            switch (key) {
                case "FREQ" -> {
                    if (!"WEEKLY".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException("FREQ must be WEEKLY");
                    }
                    weekly = true;
                }
                case "BYDAY" -> {
                    if (value.isBlank()) {
                        throw new IllegalArgumentException("BYDAY is required");
                    }
                    for (String token : value.split(",")) {
                        days.add(parseDay(token.trim()));
                    }
                }
                case "UNTIL" -> until = parseUntil(value);
                case "INTERVAL" -> {
                    if (!"1".equals(value)) {
                        throw new IllegalArgumentException("INTERVAL must be 1");
                    }
                }
                default -> throw new IllegalArgumentException("unsupported RRULE part " + key);
            }
        }
        if (!weekly) {
            throw new IllegalArgumentException("FREQ=WEEKLY is required");
        }
        if (days.isEmpty()) {
            throw new IllegalArgumentException("BYDAY is required");
        }
        return new WeeklyRrule(days, until);
    }

    public List<Instant> occurrences(Instant dtStart, Instant windowEnd) {
        if (windowEnd.isBefore(dtStart)) {
            return List.of();
        }
        Instant cap = until == null || until.isAfter(windowEnd) ? windowEnd : until;
        LocalDateTime start = LocalDateTime.ofInstant(dtStart, ZoneOffset.UTC);
        LocalTime time = start.toLocalTime();
        LocalDate firstDate = start.toLocalDate();
        if (!byDay.contains(start.getDayOfWeek())) {
            firstDate = nextMatching(firstDate);
        }
        List<Instant> starts = new ArrayList<>();
        LocalDate cursor = firstDate;
        while (!cursor.atTime(time).toInstant(ZoneOffset.UTC).isAfter(cap)) {
            Instant occurrence = cursor.atTime(time).toInstant(ZoneOffset.UTC);
            if (!occurrence.isBefore(dtStart) && !occurrence.isAfter(cap)) {
                starts.add(occurrence);
            }
            cursor = nextMatching(cursor.plusDays(1));
        }
        return List.copyOf(starts);
    }

    public static Instant defaultWindowEnd(Instant from) {
        return from.plusSeconds(WINDOW_DAYS * 24L * 3600L);
    }

    private LocalDate nextMatching(LocalDate from) {
        LocalDate cursor = from;
        for (int i = 0; i < 7; i++) {
            if (byDay.contains(cursor.getDayOfWeek())) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }
        throw new IllegalStateException("BYDAY produced no weekday");
    }

    private static DayOfWeek parseDay(String token) {
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            case "SU" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("invalid BYDAY");
        };
    }

    private static Instant parseUntil(String value) {
        try {
            if (value.length() == 8) {
                return LocalDate.parse(value, UNTIL_DATE).atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
            }
            return Instant.from(UNTIL_DATE_TIME.parse(value.toUpperCase(Locale.ROOT)));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid UNTIL");
        }
    }
}
