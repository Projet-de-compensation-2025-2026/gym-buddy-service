package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FixtureArgsTest {

    @Test
    void defaultsMatchApprovedMagnitude() {
        FixtureArgs args = FixtureArgs.parse(new String[] {});
        assertThat(args.reset()).isFalse();
        assertThat(args.magnitude()).isEqualTo(FixtureMagnitude.DEMO);
        assertThat(args.seed()).isEqualTo(FixtureSeed.DEFAULT);
    }

    @Test
    void postsPerUserAndReset() {
        FixtureArgs args = FixtureArgs.parse(
                new String[] {"--users", "20", "--posts-per-user", "5", "--events", "3", "--reset", "--seed", "20260813"
                });
        assertThat(args.reset()).isTrue();
        assertThat(args.magnitude().users()).isEqualTo(20);
        assertThat(args.magnitude().posts()).isEqualTo(100);
        assertThat(args.magnitude().events()).isEqualTo(3);
    }

    @Test
    void equalsFormIsAccepted() {
        FixtureArgs args = FixtureArgs.parse(new String[] {"--users=12", "--comments=20"});
        assertThat(args.magnitude().users()).isEqualTo(12);
        assertThat(args.magnitude().comments()).isEqualTo(20);
    }

    @Test
    void unknownFlagFails() {
        assertThatThrownBy(() -> FixtureArgs.parse(new String[] {"--explode"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
