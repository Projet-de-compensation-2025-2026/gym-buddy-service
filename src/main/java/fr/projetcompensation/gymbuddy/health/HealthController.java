package fr.projetcompensation.gymbuddy.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final ReadinessChecker readinessChecker;

    public HealthController(ReadinessChecker readinessChecker) {
        this.readinessChecker = readinessChecker;
    }

    @GetMapping("/healthz")
    public HealthStatus healthz() {
        return HealthStatus.ok();
    }

    @GetMapping("/readyz")
    public ResponseEntity<HealthStatus> readyz() {
        HealthStatus body = readinessChecker.evaluate();
        if ("ok".equals(body.status())) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
