package fr.projetcompensation.gymbuddy.admin;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.auth.Argon2PasswordHasher;
import fr.projetcompensation.gymbuddy.fixtures.FixtureMagnitude;
import fr.projetcompensation.gymbuddy.fixtures.FixtureSeed;
import fr.projetcompensation.gymbuddy.fixtures.JdbcFixtureGenerator;
import fr.projetcompensation.gymbuddy.support.PostgresTestContainer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class AdminCatalogIT {
    @Container
    static final PostgreSQLContainer POSTGRES = PostgresTestContainer.create();

    private JdbcTemplate jdbc;

    @BeforeEach
    void seedDisposableDatabase() {
        var dataSource =
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        var generator = new JdbcFixtureGenerator(
                jdbc,
                new Argon2PasswordHasher(),
                null,
                FixtureSeed.DEFAULT,
                Instant.parse("2026-01-01T08:00:00Z"),
                "synthetic-password",
                "synthetic-password",
                "synthetic-password",
                "synthetic-password");
        generator.reset(null);
        generator.generate(FixtureMagnitude.tiny());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staffListsHandleAbsentAndPresentFiltersAndPagination(boolean filtered) {
        var catalog = new JdbcAdminCatalog(jdbc);
        String q = filtered ? "" : null;
        var users = catalog.listUsers(q, filtered ? "member" : null, filtered ? "active" : null, null, 2);
        assertThat(users).hasSize(2);
        assertThat(catalog.listUsers(
                        q,
                        filtered ? "member" : null,
                        filtered ? "active" : null,
                        users.getLast().cursor(),
                        2))
                .hasSize(2)
                .doesNotContainAnyElementsOf(users);
        for (String type : new String[] {"post", "comment", "event", "media"}) {
            var first = catalog.listContent(type, q, filtered ? false : null, null, 2);
            assertThat(first).as(type).hasSize(2);
            assertThat(catalog.listContent(
                            type, q, filtered ? false : null, first.getLast().cursor(), 2))
                    .as(type)
                    .isNotEmpty()
                    .doesNotContainAnyElementsOf(first);
        }
        var media = catalog.listMedia(q, null, 2);
        assertThat(media).hasSize(2);
        assertThat(catalog.listMedia(q, media.getLast().cursor(), 2)).hasSize(2).doesNotContainAnyElementsOf(media);

        UUID actor = users.getFirst().user().id();
        var reports = new JdbcReportRepository(jdbc);
        var audit = new JdbcAuditEventRepository(jdbc);
        for (int i = 0; i < 3; i++) {
            UUID target = UUID.randomUUID();
            Instant at = Instant.parse("2026-02-01T00:00:00Z").plusSeconds(i);
            reports.save(new Report(UUID.randomUUID(), actor, "ignored", "post", target, "synthetic", "open", at));
            audit.save(new AuditEvent(
                    UUID.randomUUID(), actor, "ignored", "hide_content", "post", target, "synthetic", at));
        }
        var reportPage = reports.list("open", q, null, 2);
        assertThat(reportPage).hasSize(2);
        assertThat(reports.list("open", q, reportPage.getLast().cursor(), 2))
                .hasSize(1)
                .doesNotContainAnyElementsOf(reportPage);
        var auditPage = audit.list(filtered ? actor : null, filtered, q, filtered ? "hide_content" : null, null, 2);
        assertThat(auditPage).hasSize(2);
        assertThat(audit.list(
                        filtered ? actor : null,
                        filtered,
                        q,
                        filtered ? "hide_content" : null,
                        auditPage.getLast().cursor(),
                        2))
                .hasSize(1)
                .doesNotContainAnyElementsOf(auditPage);
        assertThat(catalog.listUsers("no-such-synthetic-handle", null, null, null, 10))
                .isEmpty();
        assertThat(reports.list("open", "not-present", null, 10)).isEmpty();
    }
}
