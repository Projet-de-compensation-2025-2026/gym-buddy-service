package fr.projetcompensation.gymbuddy.health;

import fr.projetcompensation.gymbuddy.openapi.api.DefaultApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements DefaultApi {

    private final ReadinessChecker readinessChecker;

    public HealthController(ReadinessChecker readinessChecker) {
        this.readinessChecker = readinessChecker;
    }

    @Override
    public ResponseEntity<HealthStatus> getHealthz() {
        return ResponseEntity.ok(HealthStatus.ok());
    }

    @Override
    public ResponseEntity<HealthStatus> getReadyz() {
        HealthStatus body = readinessChecker.evaluate();
        if ("ok".equals(body.status())) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
