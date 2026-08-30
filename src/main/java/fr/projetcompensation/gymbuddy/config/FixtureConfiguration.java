package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.auth.PasswordHasher;
import fr.projetcompensation.gymbuddy.auth.TransactionRunner;
import fr.projetcompensation.gymbuddy.fixtures.FixtureGenerator;
import fr.projetcompensation.gymbuddy.fixtures.FixtureSeed;
import fr.projetcompensation.gymbuddy.fixtures.JdbcFixtureGenerator;
import fr.projetcompensation.gymbuddy.fixtures.StaffBootstrap;
import fr.projetcompensation.gymbuddy.media.ObjectStorage;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class FixtureConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FixtureConfiguration.class);

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

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    StaffBootstrap staffBootstrap(
            UserRepository users,
            ProfileRepository profiles,
            PasswordHasher passwords,
            TransactionRunner transactions) {
        return new StaffBootstrap(users, profiles, passwords, transactions, ORIGIN);
    }

    @Bean
    @ConditionalOnBean(StaffBootstrap.class)
    @ConditionalOnProperty(name = "GYM_BUDDY_BOOTSTRAP_STAFF", havingValue = "true")
    ApplicationRunner staffBootstrapRunner(
            StaffBootstrap bootstrap,
            @Value("${DEMO_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${DEMO_MOD_PASSWORD:}") String modPassword) {
        return args -> {
            int created = bootstrap.ensureMissingStaff(adminPassword, modPassword);
            log.info("staff bootstrap inserted {} missing demo staff accounts", created);
        };
    }
}
