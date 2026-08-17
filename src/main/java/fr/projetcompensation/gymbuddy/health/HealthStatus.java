package fr.projetcompensation.gymbuddy.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record HealthStatus(
        String status,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> details) {

    public static HealthStatus ok() {
        return new HealthStatus("ok", null);
    }

    public static HealthStatus unavailable(Map<String, String> details) {
        return new HealthStatus("unavailable", details);
    }
}
