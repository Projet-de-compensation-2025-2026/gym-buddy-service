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

        ReadinessChecker checker = new ReadinessChecker(postgres, storage);
        assertThat(checker.ready()).isTrue();
        assertThat(checker.failedDependencies()).isEmpty();
    }

    @Test
    void namesPostgresWhenPostgresIsDown() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(false);
        when(storage.reachable()).thenReturn(true);

        ReadinessChecker checker = new ReadinessChecker(postgres, storage);
        assertThat(checker.ready()).isFalse();
        assertThat(checker.failedDependencies())
                .containsExactly(new HealthStatus.FailedDependency("postgres", "unreachable"));
    }

    @Test
    void namesObjectStorageWhenObjectStorageIsDown() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(true);
        when(storage.reachable()).thenReturn(false);

        ReadinessChecker checker = new ReadinessChecker(postgres, storage);
        assertThat(checker.ready()).isFalse();
        assertThat(checker.failedDependencies())
                .containsExactly(new HealthStatus.FailedDependency("objectStorage", "unreachable"));
    }
}
