package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.GymBuddyApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

/**
 * Non-web entry: {@code mvn compile exec:java
 * -Dexec.mainClass=fr.projetcompensation.gymbuddy.fixtures.FixturesCli
 * -Dexec.args="--users 3000 --posts-per-user 5 --events 800 --reset"}.
 */
public final class FixturesCli {

    private FixturesCli() {}

    public static void main(String[] args) {
        FixtureArgs parsed = FixtureArgs.parse(args);
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GymBuddyApplication.class)
                .web(WebApplicationType.NONE)
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
                    parsed.seed(),
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
}
