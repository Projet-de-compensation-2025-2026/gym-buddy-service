package fr.projetcompensation.gymbuddy.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class ReadinessCheckerTest {

    @Test
    void readyWhenPostgresAndObjectStorageAreReachable() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(true);
        when(storage.reachable()).thenReturn(true);

        HealthStatus status = new ReadinessChecker(postgres, storage).evaluate();

        assertThat(status.status()).isEqualTo("ok");
        assertThat(status.details()).isNull();
    }

    @Test
    void namesPostgresWhenItIsDown() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(false);
        when(postgres.detail()).thenReturn("connection refused");
        when(storage.reachable()).thenReturn(true);

        HealthStatus status = new ReadinessChecker(postgres, storage).evaluate();

        assertThat(status.status()).isEqualTo("unavailable");
        assertThat(status.details()).containsEntry("postgres", "connection refused");
        assertThat(status.details()).doesNotContainKey("objectStorage");
    }

    @Test
    void namesObjectStorageWhenItIsDown() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(true);
        when(storage.reachable()).thenReturn(false);
        when(storage.detail()).thenReturn("not configured");

        HealthStatus status = new ReadinessChecker(postgres, storage).evaluate();

        assertThat(status.status()).isEqualTo("unavailable");
        assertThat(status.details()).containsEntry("objectStorage", "not configured");
        assertThat(status.details()).doesNotContainKey("postgres");
    }

    @Test
    void namesObjectStorageWhenMediaServiceIsMissingEvenIfS3HealthIsOk() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(true);
        when(storage.reachable()).thenReturn(true);

        HealthStatus status = new ReadinessChecker(postgres, storage, () -> false).evaluate();

        assertThat(status.status()).isEqualTo("unavailable");
        assertThat(status.details()).containsEntry("objectStorage", "not configured");
    }
}
