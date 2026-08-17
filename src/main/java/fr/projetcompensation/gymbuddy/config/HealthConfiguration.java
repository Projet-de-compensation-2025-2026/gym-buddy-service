package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.health.ObjectStorageHealthPort;
import fr.projetcompensation.gymbuddy.health.PostgresHealthPort;
import fr.projetcompensation.gymbuddy.health.ReadinessChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthConfiguration {

    @Bean
    ReadinessChecker readinessChecker(PostgresHealthPort postgres, ObjectStorageHealthPort objectStorage) {
        return new ReadinessChecker(postgres, objectStorage);
    }
}
