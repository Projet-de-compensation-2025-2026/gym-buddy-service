package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.auth.PasswordHasher;
import fr.projetcompensation.gymbuddy.fixtures.FixtureGenerator;
import fr.projetcompensation.gymbuddy.fixtures.FixtureSeed;
import fr.projetcompensation.gymbuddy.fixtures.JdbcFixtureGenerator;
import fr.projetcompensation.gymbuddy.media.ObjectStorage;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class FixtureConfiguration {

    static final Instant ORIGIN = Instant.parse("2026-01-01T08:00:00Z");

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    FixtureGenerator fixtureGenerator(
            JdbcTemplate jdbc,
            PasswordHasher passwords,
            ObjectProvider<ObjectStorage> storage,
            @Value("${FIXTURE_SEED:20260813}") String seed,
            @Value("${DEMO_ALEX_PASSWORD:change-me-local-demo}") String alexPassword,
            @Value("${DEMO_BLAKE_PASSWORD:change-me-local-demo}") String blakePassword,
            @Value("${DEMO_MOD_PASSWORD:change-me-local-demo}") String modPassword,
            @Value("${DEMO_ADMIN_PASSWORD:change-me-local-demo}") String adminPassword) {
        return new JdbcFixtureGenerator(
                jdbc,
                passwords,
                storage.getIfAvailable(),
                FixtureSeed.parse(seed),
                ORIGIN,
                alexPassword,
                blakePassword,
                modPassword,
                adminPassword);
    }
}
