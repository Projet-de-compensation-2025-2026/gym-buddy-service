package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

class FixtureCliApplicationTest {

    @Test
    void startsTheControllerContextWithoutAnHttpListener() {
        try (var context = FixtureCliApplication.application()
                .run("--GYM_BUDDY_BOOTSTRAP_STAFF=false", "--spring.flyway.enabled=false", "--server.port=8080")) {
            assertThat(context).isInstanceOf(ServletWebServerApplicationContext.class);
            assertThat(((ServletWebServerApplicationContext) context)
                            .getWebServer()
                            .getPort())
                    .isEqualTo(-1);
        }
    }
}
