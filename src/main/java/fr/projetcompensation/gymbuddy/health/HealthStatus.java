package fr.projetcompensation.gymbuddy.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HealthStatus(String status, List<FailedDependency> details) {

    public static HealthStatus ok() {
        return new HealthStatus("ok", List.of());
    }

    public static HealthStatus unavailable(List<FailedDependency> details) {
        return new HealthStatus("unavailable", List.copyOf(details));
    }

    public record FailedDependency(String path, String issue) {}
}
