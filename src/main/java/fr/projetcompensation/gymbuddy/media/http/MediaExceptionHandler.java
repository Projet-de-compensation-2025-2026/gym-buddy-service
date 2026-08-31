package fr.projetcompensation.gymbuddy.media.http;

import fr.projetcompensation.gymbuddy.health.HealthStatus;
import fr.projetcompensation.gymbuddy.media.MediaUnavailableException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MediaExceptionHandler {

    @ExceptionHandler(MediaUnavailableException.class)
    public ResponseEntity<HealthStatus> handleUnavailable(MediaUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(HealthStatus.unavailable(Map.of("objectStorage", ex.getMessage())));
    }
}
