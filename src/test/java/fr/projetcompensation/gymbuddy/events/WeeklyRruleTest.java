package fr.projetcompensation.gymbuddy.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyRruleTest {

    @Test
    void fsEvt03_weeklyByDayEnumeratesWindow() {
        WeeklyRrule rule = WeeklyRrule.parse("FREQ=WEEKLY;BYDAY=MO,WE");
        Instant start = Instant.parse("2026-08-31T18:00:00Z");
        Instant end = Instant.parse("2026-09-14T18:00:00Z");

        List<Instant> starts = rule.occurrences(start, end);

        assertThat(rule.byDay()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(starts)
                .containsExactly(
                        Instant.parse("2026-08-31T18:00:00Z"),
                        Instant.parse("2026-09-02T18:00:00Z"),
                        Instant.parse("2026-09-07T18:00:00Z"),
                        Instant.parse("2026-09-09T18:00:00Z"),
                        Instant.parse("2026-09-14T18:00:00Z"));
    }

    @Test
    void fsEvt03_untilCapsOccurrences() {
        WeeklyRrule rule = WeeklyRrule.parse("FREQ=WEEKLY;BYDAY=TU;UNTIL=20260908T180000Z");
        Instant start = Instant.parse("2026-09-01T18:00:00Z");
        List<Instant> starts = rule.occurrences(start, WeeklyRrule.defaultWindowEnd(start));
        assertThat(starts)
                .containsExactly(Instant.parse("2026-09-01T18:00:00Z"), Instant.parse("2026-09-08T18:00:00Z"));
    }

    @Test
    void fsEvt03_rejectsNonWeekly() {
        assertThatThrownBy(() -> WeeklyRrule.parse("FREQ=DAILY;BYDAY=MO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeeklyRrule.parse("FREQ=WEEKLY")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fsEvt03_skipsDtStartWhenWeekdayNotInByDay() {
        WeeklyRrule rule = WeeklyRrule.parse("RRULE:FREQ=WEEKLY;BYDAY=WE");
        Instant monday = Instant.parse("2026-08-31T18:00:00Z");
        assertThat(LocalDate.ofInstant(monday, java.time.ZoneOffset.UTC).getDayOfWeek())
                .isEqualTo(DayOfWeek.MONDAY);
        assertThat(rule.occurrences(monday, Instant.parse("2026-09-10T00:00:00Z")))
                .containsExactly(Instant.parse("2026-09-02T18:00:00Z"), Instant.parse("2026-09-09T18:00:00Z"));
    }
}
