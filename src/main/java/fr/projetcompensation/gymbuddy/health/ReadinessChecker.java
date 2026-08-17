package fr.projetcompensation.gymbuddy.health;

public final class ReadinessChecker {

    private final PostgresHealthPort postgres;
    private final ObjectStorageHealthPort objectStorage;

    public ReadinessChecker(PostgresHealthPort postgres, ObjectStorageHealthPort objectStorage) {
        this.postgres = postgres;
        this.objectStorage = objectStorage;
    }

    public boolean ready() {
        return postgres.reachable() && objectStorage.reachable();
    }
}
