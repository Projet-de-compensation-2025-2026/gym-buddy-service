package fr.projetcompensation.gymbuddy.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionObjectStorageGuard implements ApplicationRunner {

    private static final String[] REQUIRED_KEYS = {"S3_ENDPOINT", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY"};

    private final Environment environment;

    public ProductionObjectStorageGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String key : REQUIRED_KEYS) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Production refuses to start without object storage. Missing or blank " + key
                                + ". Local uploads/ fallback is forbidden.");
            }
        }
    }
}
