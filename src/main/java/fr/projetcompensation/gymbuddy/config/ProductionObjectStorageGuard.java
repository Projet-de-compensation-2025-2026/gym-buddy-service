package fr.projetcompensation.gymbuddy.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

public class ProductionObjectStorageGuard implements EnvironmentPostProcessor {

    private static final String[] REQUIRED_KEYS = {"S3_ENDPOINT", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY"};

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        for (String key : REQUIRED_KEYS) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Production refuses to start without object storage. Missing or blank "
                                + key
                                + ". Local uploads/ fallback is forbidden.");
            }
        }
    }
}
