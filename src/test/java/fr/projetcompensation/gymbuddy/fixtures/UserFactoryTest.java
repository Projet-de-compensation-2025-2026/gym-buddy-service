package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.users.UserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserFactoryTest {

    private static final Instant ORIGIN = Instant.parse("2026-01-01T08:00:00Z");

    @Test
    void sameSeedProducesSameHandlesAndClusters() {
        List<UserDraft> first = factory(FixtureSeed.DEFAULT).create(12);
        List<UserDraft> second = factory(FixtureSeed.DEFAULT).create(12);
        assertThat(first.stream().map(UserDraft::handle).toList())
                .isEqualTo(second.stream().map(UserDraft::handle).toList());
        assertThat(first.get(0).handle()).isEqualTo(FixtureCatalog.ALEX_HANDLE);
        assertThat(first.get(1).handle()).isEqualTo(FixtureCatalog.BLAKE_HANDLE);
        assertThat(first.get(2).user().role()).isEqualTo(UserRole.MODERATOR);
        assertThat(first.get(3).user().role()).isEqualTo(UserRole.ADMIN);
        assertThat(first.get(0).city()).isEqualTo(first.get(1).city());
        assertThat(first.get(0).sport()).isEqualTo("running");
        assertThat(first.get(4).handle()).isEqualTo("u00004");
    }

    @Test
    void differentSeedChangesBulkDisplayNames() {
        String a = factory(FixtureSeed.DEFAULT).create(8).get(7).profile().displayName();
        String b = factory(1L).create(8).get(7).profile().displayName();
        assertThat(a).isNotEqualTo(b);
    }

    private static UserFactory factory(long seed) {
        return new UserFactory(seed, ORIGIN, "bulk", "alex", "blake", "mod", "admin");
    }
}
