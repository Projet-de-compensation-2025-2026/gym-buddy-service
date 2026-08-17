package fr.projetcompensation.gymbuddy.health;

import java.util.ArrayList;
import java.util.List;

public final class ReadinessChecker {

    private final PostgresHealthPort postgres;
    private final ObjectStorageHealthPort objectStorage;

    public ReadinessChecker(PostgresHealthPort postgres, ObjectStorageHealthPort objectStorage) {
        this.postgres = postgres;
        this.objectStorage = objectStorage;
    }

    public boolean ready() {
        return failedDependencies().isEmpty();
    }

    public List<HealthStatus.FailedDependency> failedDependencies() {
        List<HealthStatus.FailedDependency> failed = new ArrayList<>();
        if (!postgres.reachable()) {
            failed.add(new HealthStatus.FailedDependency("postgres", "unreachable"));
        }
        if (!objectStorage.reachable()) {
            failed.add(new HealthStatus.FailedDependency("objectStorage", "unreachable"));
        }
        return List.copyOf(failed);
    }
}
