package fr.projetcompensation.gymbuddy.health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class ReadinessChecker {

    private final PostgresHealthPort postgres;
    private final ObjectStorageHealthPort objectStorage;
    private final BooleanSupplier mediaUsable;

    public ReadinessChecker(PostgresHealthPort postgres, ObjectStorageHealthPort objectStorage) {
        this(postgres, objectStorage, () -> true);
    }

    public ReadinessChecker(
            PostgresHealthPort postgres, ObjectStorageHealthPort objectStorage, BooleanSupplier mediaUsable) {
        this.postgres = postgres;
        this.objectStorage = objectStorage;
        this.mediaUsable = mediaUsable == null ? () -> true : mediaUsable;
    }

    public HealthStatus evaluate() {
        Map<String, String> failed = new LinkedHashMap<>();
        if (!postgres.reachable()) {
            failed.put("postgres", postgres.detail());
        }
        if (!mediaUsable.getAsBoolean()) {
            failed.put("objectStorage", "not configured");
        } else if (!objectStorage.reachable()) {
            failed.put("objectStorage", objectStorage.detail());
        }
        if (failed.isEmpty()) {
            return HealthStatus.ok();
        }
        return HealthStatus.unavailable(failed);
    }
}
