package fr.projetcompensation.gymbuddy.fixtures;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * One-shot staff insert: {@code GYM_BUDDY_BOOTSTRAP_STAFF=true mvn compile exec:java
 * -Dexec.mainClass=fr.projetcompensation.gymbuddy.fixtures.StaffBootstrapCli}.
 * Allowed on {@code prod}. Does not call the fixture generator.
 */
public final class StaffBootstrapCli {

    private StaffBootstrapCli() {}

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                FixtureCliApplication.application().run(args)) {
            if (!Boolean.parseBoolean(context.getEnvironment().getProperty("GYM_BUDDY_BOOTSTRAP_STAFF", "false"))) {
                System.err.println("set GYM_BUDDY_BOOTSTRAP_STAFF=true to insert missing demo.admin / demo.mod");
                System.exit(1);
                return;
            }
            // The startup runner performs the insert once, before run() returns.
            System.out.println("staff bootstrap completed");
        }
    }
}
