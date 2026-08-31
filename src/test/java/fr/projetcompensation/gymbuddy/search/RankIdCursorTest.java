package fr.projetcompensation.gymbuddy.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankIdCursorTest {

    private static final UUID ID = UUID.fromString("3a0e1017-e6ba-486d-8bd0-fe8ffc617721");

    @Test
    void fsSrch05_encodeParseRoundTripsRankThatPercent12fRoundsUp() {
        double rank = 0.20 * Math.exp(-1.0 / (14.0 * 24.0));
        String padded12 = String.format(Locale.ROOT, "%.12f", rank);
        assertThat(padded12).isEqualTo("0.199405646798");
        assertThat(Double.compare(Double.parseDouble(padded12), rank)).isPositive();

        RankIdCursor parsed =
                RankIdCursor.parse(new RankIdCursor(rank, ID).encode()).orElseThrow();

        assertThat(parsed.rank()).isEqualTo(rank);
        assertThat(parsed.id()).isEqualTo(ID);
        assertThat(parsed.after(rank, ID)).isFalse();
        assertThat(RankIdCursor.parse(padded12 + ":" + ID).orElseThrow().after(rank, ID))
                .isTrue();
    }

    @Test
    void fsSrch05_tiedExact12DecimalRanksStillAdvanceById() {
        double rank = Double.parseDouble("0.200000000000");
        UUID later = UUID.fromString("00000000-0000-4000-8000-000000000001");
        RankIdCursor cursor =
                RankIdCursor.parse(new RankIdCursor(rank, ID).encode()).orElseThrow();

        assertThat(cursor.rank()).isEqualTo(rank);
        assertThat(cursor.after(rank, later)).isTrue();
        assertThat(cursor.after(rank, ID)).isFalse();
    }
}
