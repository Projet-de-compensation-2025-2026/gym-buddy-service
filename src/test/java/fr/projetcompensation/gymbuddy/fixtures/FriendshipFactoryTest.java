package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendshipFactoryTest {

    @Test
    void powerLawHasHubsAndAlexBlakeEdge() {
        List<UserDraft> users = new UserFactory(
                        FixtureSeed.DEFAULT, Instant.parse("2026-01-01T08:00:00Z"), "h", "a", "b", "m", "d")
                .create(40);
        List<Friendship> friendships =
                new FriendshipFactory(FixtureSeed.DEFAULT, Instant.parse("2026-01-01T08:00:00Z")).create(users, 80);
        assertThat(friendships).isNotEmpty();
        assertThat(friendships).allMatch(row -> row.status() == FriendshipStatus.ACCEPTED);
        UUID alex = FixtureIds.demo(FixtureCatalog.ALEX_HANDLE);
        UUID blake = FixtureIds.demo(FixtureCatalog.BLAKE_HANDLE);
        assertThat(friendships).anyMatch(row -> row.involves(alex) && row.involves(blake));
        Map<UUID, Integer> degree = new HashMap<>();
        for (Friendship row : friendships) {
            degree.merge(row.requesterId(), 1, Integer::sum);
            degree.merge(row.addresseeId(), 1, Integer::sum);
        }
        int max = degree.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = degree.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        assertThat(max).isGreaterThan(min);
    }
}
