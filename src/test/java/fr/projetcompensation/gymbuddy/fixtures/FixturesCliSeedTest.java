package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class FixturesCliSeedTest {
    @Test
    void explicitSeedOverridesConfiguredGeneratorSeed() {
        for (String[] args : new String[][] {{"--seed", "42"}, {"--seed=42"}}) {
            StandardEnvironment environment = configuredEnvironment();
            FixturesCli.configureSeed(environment, args, FixtureArgs.parse(args));
            assertThat(environment.getProperty("FIXTURE_SEED", Long.class)).isEqualTo(42L);
        }
    }

    @Test
    void omittedSeedPreservesConfiguredGeneratorSeed() {
        StandardEnvironment environment = configuredEnvironment();
        String[] args = {"--users", "12"};
        FixturesCli.configureSeed(environment, args, FixtureArgs.parse(args));
        assertThat(environment.getProperty("FIXTURE_SEED", Long.class)).isEqualTo(333L);
    }

    private StandardEnvironment configuredEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("FIXTURE_SEED", "333")));
        return environment;
    }
}
