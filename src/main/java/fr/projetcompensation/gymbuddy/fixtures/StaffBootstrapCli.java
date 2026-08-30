package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.GymBuddyApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One-shot staff insert: {@code GYM_BUDDY_BOOTSTRAP_STAFF=true mvn compile exec:java
 * -Dexec.mainClass=fr.projetcompensation.gymbuddy.fixtures.StaffBootstrapCli}.
 * Allowed on {@code prod}. Does not call the fixture generator.
 */
public final class StaffBootstrapCli {

    private StaffBootstrapCli() {}

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GymBuddyApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            if (!Boolean.parseBoolean(context.getEnvironment().getProperty("GYM_BUDDY_BOOTSTRAP_STAFF", "false"))) {
                System.err.println("set GYM_BUDDY_BOOTSTRAP_STAFF=true to insert missing demo.admin / demo.mod");
                System.exit(1);
                return;
            }
            StaffBootstrap bootstrap = context.getBean(StaffBootstrap.class);
            String adminPassword = context.getEnvironment().getProperty("DEMO_ADMIN_PASSWORD", "");
            String modPassword = context.getEnvironment().getProperty("DEMO_MOD_PASSWORD", "");
            int created = bootstrap.ensureMissingStaff(adminPassword, modPassword);
            System.out.printf("staff bootstrap inserted %d missing demo staff accounts%n", created);
        }
    }
}
