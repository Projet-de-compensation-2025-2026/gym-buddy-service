package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import fr.projetcompensation.gymbuddy.auth.Argon2PasswordHasher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class FixtureGeneratorIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    private JdbcTemplate jdbc;
    private JdbcFixtureGenerator generator;

    @BeforeAll
    static void requireDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for FixtureGeneratorIT");
        assumeTrue(POSTGRES.isRunning(), "PostgreSQL Testcontainer must stay up for FixtureGeneratorIT");
    }

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        generator = new JdbcFixtureGenerator(
                jdbc,
                new Argon2PasswordHasher(),
                null,
                FixtureSeed.DEFAULT,
                Instant.parse("2026-01-01T08:00:00Z"),
                "change-me-local-demo",
                "change-me-local-demo",
                "change-me-local-demo",
                "change-me-local-demo");
        generator.reset(null);
    }

    @Test
    void tinyFactoriesInsertTensOfRowsAndDemoAccounts() {
        FixtureMagnitude tiny = FixtureMagnitude.tiny();
        FixtureReport report = generator.generate(tiny);
        assertThat(report.users()).isEqualTo(tiny.users());
        assertThat(report.friendships()).isGreaterThan(0);
        assertThat(report.posts()).isEqualTo(tiny.posts());
        assertThat(report.comments()).isEqualTo(tiny.comments());
        assertThat(report.events()).isEqualTo(tiny.events());
        assertThat(report.media()).isEqualTo(tiny.media());
        assertThat(count("users")).isEqualTo(tiny.users());
        assertThat(handles())
                .contains(
                        FixtureCatalog.ALEX_HANDLE,
                        FixtureCatalog.BLAKE_HANDLE,
                        FixtureCatalog.MOD_HANDLE,
                        FixtureCatalog.ADMIN_HANDLE);
        assertThat(count("friendships")).isEqualTo(report.friendships());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM friendships WHERE status = 'accepted'", Integer.class))
                .isEqualTo(report.friendships());
        assertThat(count("posts")).isEqualTo(tiny.posts());
        assertThat(count("comments")).isEqualTo(tiny.comments());
        assertThat(count("events")).isEqualTo(tiny.events());
        assertThat(count("event_applications")).isEqualTo(report.applications());
        assertThat(count("messages")).isEqualTo(report.messages());
        assertThat(count("media")).isEqualTo(tiny.media());
        Integer distinctKeys = jdbc.queryForObject("SELECT COUNT(DISTINCT object_key) FROM media", Integer.class);
        assertThat(distinctKeys).isLessThanOrEqualTo(StockImages.COUNT);
        assertThat(alexBlakeFriends()).isTrue();
        generator.reset(null);
        assertThat(count("users")).isZero();
        assertThat(count("posts")).isZero();
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private List<String> handles() {
        return jdbc.query("SELECT handle FROM users", (rs, i) -> rs.getString(1));
    }

    private boolean alexBlakeFriends() {
        Integer value =
                jdbc.queryForObject("""
                SELECT COUNT(*) FROM friendships f
                JOIN users a ON a.id = f.requester_id OR a.id = f.addressee_id
                JOIN users b ON b.id = f.requester_id OR b.id = f.addressee_id
                WHERE a.handle = ? AND b.handle = ? AND f.status = 'accepted' AND a.id <> b.id
                """, Integer.class, FixtureCatalog.ALEX_HANDLE, FixtureCatalog.BLAKE_HANDLE);
        return value != null && value > 0;
    }
}
