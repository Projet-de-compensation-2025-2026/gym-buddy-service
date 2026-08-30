package fr.projetcompensation.gymbuddy.matching;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

public final class IsoWeek {

    private IsoWeek() {}

    public static LocalDate mondayUtc(Instant instant) {
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
