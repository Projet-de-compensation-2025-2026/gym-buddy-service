package fr.projetcompensation.gymbuddy.events;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchingScoreTest {

    @Test
    void fsEvt13_activityMatchBeatsEmptySports() {
        Event event = event("Weightlifting", Instant.parse("2026-09-01T18:00:00Z"), 48.0, 2.0);
        Profile match = profile(List.of("Weightlifting", "Running"), 48.0, 2.0, List.of());
        Profile miss = profile(List.of("Yoga"), null, null, List.of());

        double high = MatchingScore.score(event, match, true, 0);
        double low = MatchingScore.score(event, miss, true, 0);

        assertThat(high).isGreaterThan(low);
        assertThat(MatchingScore.sport(match, "Weightlifting")).isEqualTo(1.0);
        assertThat(MatchingScore.sport(miss, "Weightlifting")).isEqualTo(0.0);
    }

    @Test
    void fsEvt13_capacityIsNotInTheScore() {
        Event event = event("Running", Instant.parse("2026-09-01T07:00:00Z"), null, null);
        Profile profile = profile(List.of("Running"), null, null, List.of());
        assertThat(MatchingScore.score(event, profile, true, 0))
                .isEqualTo(MatchingScore.score(event, profile, true, 0));
    }

    @Test
    void fsEvt13_windowAndHistoryIncreaseScore() {
        Instant start = Instant.parse("2026-09-01T18:00:00Z");
        Event event = event("Running", start, null, null);
        Profile withWindow = profile(
                List.of("Running"), null, null, List.of(new PreferredWindow(2, "17:00", "19:00")));
        Profile noWindow = profile(List.of("Running"), null, null, List.of());

        assertThat(MatchingScore.window(withWindow, start)).isEqualTo(1.0);
        assertThat(MatchingScore.score(event, withWindow, true, 5))
                .isGreaterThan(MatchingScore.score(event, noWindow, true, 0));
    }

    private static Event event(String activity, Instant startsAt, Double lat, Double lng) {
        return new Event(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Session",
                null,
                activity,
                "Gym",
                lat,
                lng,
                startsAt,
                60,
                EventVisibility.PUBLIC,
                3,
                null,
                List.of(),
                null,
                null,
                false,
                startsAt,
                null);
    }

    private static Profile profile(List<String> sports, Double lat, Double lng, List<PreferredWindow> windows) {
        return new Profile(
                UUID.randomUUID(),
                "Alex",
                null,
                ProfileVisibility.PUBLIC,
                sports,
                null,
                "Paris",
                lat,
                lng,
                windows,
                null);
    }
}
