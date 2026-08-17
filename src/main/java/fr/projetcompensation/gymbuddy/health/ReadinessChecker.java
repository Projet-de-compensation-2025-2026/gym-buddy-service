package fr.projetcompensation.gymbuddy.health;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReadinessChecker {

    private final PostgresHealthPort postgres;
    private final ObjectStorageHealthPort objectStorage;

    public ReadinessChecker(PostgresHealthPort postgres, ObjectStorageHealthPort objectStorage) {
        this.postgres = postgres;
        this.objectStorage = objectStorage;
    }

    public HealthStatus evaluate() {
        Map<String, String> failed = new LinkedHashMap<>();
        if (!postgres.reachable()) {
            failed.put("postgres", postgres.detail());
        }
        if (!objectStorage.reachable()) {
            failed.put("objectStorage", objectStorage.detail());
        }
        if (failed.isEmpty()) {
            return HealthStatus.ok();
        }
        return HealthStatus.unavailable(failed);
    }
}
