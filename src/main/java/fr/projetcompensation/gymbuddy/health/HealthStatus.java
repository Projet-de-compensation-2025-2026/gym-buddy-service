package fr.projetcompensation.gymbuddy.health;

public record HealthStatus(String status) {

    public static HealthStatus ok() {
        return new HealthStatus("ok");
    }

    public static HealthStatus unavailable() {
        return new HealthStatus("unavailable");
    }
}
