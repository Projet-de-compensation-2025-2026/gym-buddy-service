package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.GymBuddyApplication;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;

/** Shared application context for fixture commands, without an HTTP listener. */
final class FixtureCliApplication {

    private FixtureCliApplication() {}

    static SpringApplicationBuilder application() {
        // Controllers need a servlet context. Override configuration and CLI input
        // so neither fixture command can accidentally open a web port.
        return new SpringApplicationBuilder(GymBuddyApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(context -> context.getEnvironment()
                        .getPropertySources()
                        .addFirst(new MapPropertySource("fixture-cli", Map.of("server.port", -1))));
    }
}
