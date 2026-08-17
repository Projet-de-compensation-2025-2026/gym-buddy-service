package fr.projetcompensation.gymbuddy.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "gym-buddy-database-url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        Map<String, Object> properties = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            properties.put(
                    "spring.autoconfigure.exclude",
                    String.join(
                            ",",
                            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
                            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"));
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
            return;
        }
        DatabaseUrl parsed = DatabaseUrl.parse(raw);
        properties.put("spring.datasource.url", parsed.jdbcUrl());
        properties.put("spring.datasource.username", parsed.username());
        properties.put("spring.datasource.password", parsed.password());
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }
}
