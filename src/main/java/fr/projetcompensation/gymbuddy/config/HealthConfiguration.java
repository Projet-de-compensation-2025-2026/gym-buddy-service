package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.health.ObjectStorageHealthPort;
import fr.projetcompensation.gymbuddy.health.PostgresHealthPort;
import fr.projetcompensation.gymbuddy.health.ReadinessChecker;
import fr.projetcompensation.gymbuddy.media.MediaService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthConfiguration {

    @Bean
    ReadinessChecker readinessChecker(
            PostgresHealthPort postgres, ObjectStorageHealthPort objectStorage, ObjectProvider<MediaService> media) {
        return new ReadinessChecker(postgres, objectStorage, () -> media.getIfAvailable() != null);
    }
}
