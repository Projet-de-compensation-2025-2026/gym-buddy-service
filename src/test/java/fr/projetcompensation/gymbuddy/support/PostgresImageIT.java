package fr.projetcompensation.gymbuddy.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PostgresImageIT {
    @Container
    static final PostgreSQLContainer POSTGRES = PostgresTestContainer.create();

    @Test
    void officialEntrypointDropsPrivilegesAndLogicalRestoreRebuildsIndexes() throws Exception {
        assertThat(POSTGRES.execInContainer("stat", "-c", "%U", "/proc/1")
                        .getStdout()
                        .trim())
                .isEqualTo("postgres");
        var source = new JdbcTemplate(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(source.queryForObject(
                        "SELECT datlocprovider::text FROM pg_database WHERE datname = current_database()",
                        String.class))
                .isEqualTo("i");
        source.execute("CREATE TABLE migration_probe (id integer PRIMARY KEY, handle text UNIQUE NOT NULL)");
        source.execute("INSERT INTO migration_probe VALUES (1, 'alex'), (2, 'Élodie'), (3, 'élodie'), (4, 'Zoë')");
        var dump = POSTGRES.execInContainer(
                "pg_dump",
                "-U",
                POSTGRES.getUsername(),
                "-d",
                POSTGRES.getDatabaseName(),
                "-Fc",
                "-f",
                "/tmp/probe.dump");
        assertThat(dump.getExitCode()).as(dump.getStderr()).isZero();
        assertThat(POSTGRES.execInContainer("createdb", "-U", POSTGRES.getUsername(), "restored")
                        .getExitCode())
                .isZero();
        var restore = POSTGRES.execInContainer(
                "pg_restore", "-U", POSTGRES.getUsername(), "-d", "restored", "--exit-on-error", "/tmp/probe.dump");
        assertThat(restore.getExitCode()).as(restore.getStderr()).isZero();
        String restoredUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/restored");
        var restored = new JdbcTemplate(
                new DriverManagerDataSource(restoredUrl, POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(restored.queryForList("SELECT id, handle FROM migration_probe ORDER BY id"))
                .isEqualTo(source.queryForList("SELECT id, handle FROM migration_probe ORDER BY id"));
        assertThat(restored.queryForObject(
                        "SELECT COUNT(*) FROM pg_index WHERE indrelid = 'migration_probe'::regclass AND indisunique AND indisvalid",
                        Integer.class))
                .isEqualTo(2);
    }
}
