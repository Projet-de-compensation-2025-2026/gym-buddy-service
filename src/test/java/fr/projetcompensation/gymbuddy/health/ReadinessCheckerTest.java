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

        assertThat(new ReadinessChecker(postgres, storage).ready()).isTrue();
    }

    @Test
    void notReadyWhenPostgresIsDown() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(false);
        when(storage.reachable()).thenReturn(true);

        assertThat(new ReadinessChecker(postgres, storage).ready()).isFalse();
    }

    @Test
    void notReadyWhenObjectStorageIsDown() {
        PostgresHealthPort postgres = mock(PostgresHealthPort.class);
        ObjectStorageHealthPort storage = mock(ObjectStorageHealthPort.class);
        when(postgres.reachable()).thenReturn(true);
        when(storage.reachable()).thenReturn(false);

        assertThat(new ReadinessChecker(postgres, storage).ready()).isFalse();
    }
}
