package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.health.PostgresHealthPort;
import org.springframework.stereotype.Component;

@Component
public class UnavailablePostgresHealthAdapter implements PostgresHealthPort {

    @Override
    public boolean reachable() {
        return false;
    }

    @Override
    public String detail() {
        return "not configured";
    }
}
