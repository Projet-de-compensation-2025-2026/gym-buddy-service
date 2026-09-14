package fr.projetcompensation.gymbuddy.fixtures;

import java.util.Arrays;
import java.util.Map;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * No-listener entry: {@code mvn compile exec:java
 * -Dexec.mainClass=fr.projetcompensation.gymbuddy.fixtures.FixturesCli
 * -Dexec.args="--users 3000 --posts-per-user 5 --events 800 --reset"}.
 */
public final class FixturesCli {

    private FixturesCli() {}

    public static void main(String[] args) {
        FixtureArgs parsed = FixtureArgs.parse(args);
        try (ConfigurableApplicationContext context = FixtureCliApplication.application()
                .initializers(application -> configureSeed(application.getEnvironment(), args, parsed))
                .run(args)) {
            if (context.getEnvironment().acceptsProfiles(Profiles.of("prod"))) {
                System.err.println("fixtures are disabled when SPRING_PROFILES_ACTIVE=prod");
                System.exit(1);
                return;
            }
            FixtureGenerator generator = context.getBean(FixtureGenerator.class);
            if (parsed.reset()) {
                generator.reset(null);
            }
            FixtureReport report = generator.generate(parsed.magnitude());
            System.out.printf(
                    "fixtures seed=%d users=%d friendships=%d posts=%d comments=%d events=%d applications=%d messages=%d media=%d stock=%d%n",
                    context.getEnvironment().getProperty("FIXTURE_SEED", Long.class, FixtureSeed.DEFAULT),
                    report.users(),
                    report.friendships(),
                    report.posts(),
                    report.comments(),
                    report.events(),
                    report.applications(),
                    report.messages(),
                    report.media(),
                    report.stockObjects());
        }
    }

    static void configureSeed(ConfigurableEnvironment environment, String[] args, FixtureArgs parsed) {
        boolean explicit = Arrays.stream(args)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .anyMatch(arg -> arg.equals("--seed") || arg.startsWith("--seed="));
        if (explicit) {
            environment
                    .getPropertySources()
                    .addFirst(new MapPropertySource("fixture-seed-cli", Map.of("FIXTURE_SEED", parsed.seed())));
        }
    }
}
