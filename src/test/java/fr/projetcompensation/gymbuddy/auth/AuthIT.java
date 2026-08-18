package fr.projetcompensation.gymbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuthIT {

    private static final String SECRET = "test-hs256-secret-that-is-long-enough";
    private static final String PASSWORD = "correct-horse";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", () -> "postgresql://%s:%s@%s:%d/%s"
                .formatted(
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        POSTGRES.getDatabaseName()));
        registry.add("REDIS_URL", () -> "redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)));
        registry.add("JWT_ACCESS_SECRET", () -> SECRET);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registerLoginRefreshLogoutAndLockedUser() {
        RestClient client = restClient();

        ResponseEntity<String> first = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("alex@example.com", "alex", PASSWORD, "Alex"))
                .retrieve()
                .toEntity(String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).contains("\"role\":\"admin\"");

        ResponseEntity<String> duplicate = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("Alex@Example.com", "other", PASSWORD, "Other"))
                .retrieve()
                .toEntity(String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("\"code\":\"CONFLICT\"");

        ResponseEntity<String> weak = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("blake@example.com", "blake", "short", "Blake"))
                .retrieve()
                .toEntity(String.class);
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(weak.getBody()).contains("\"code\":\"VALIDATION\"");

        ResponseEntity<String> login = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"alex@example.com\",\"password\":\"correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access = jsonField(login.getBody(), "accessToken");
        assertThat(access).isNotBlank();
        assertThat(access.split("\\.").length).isEqualTo(3);
        String refresh = refreshCookie(login.getHeaders());
        assertThat(refresh).isNotBlank();
        assertThat(setCookie(login.getHeaders()))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");

        ResponseEntity<String> refreshResponse = client.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh=" + refresh)
                .retrieve()
                .toEntity(String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotated = refreshCookie(refreshResponse.getHeaders());
        assertThat(rotated).isNotBlank().isNotEqualTo(refresh);

        ResponseEntity<String> reused = client.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh=" + refresh)
                .retrieve()
                .toEntity(String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> logout = client.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.COOKIE, "refresh=" + rotated)
                .retrieve()
                .toEntity(String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterLogout = client.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh=" + rotated)
                .retrieve()
                .toEntity(String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(afterLogout.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");

        jdbcTemplate.update("UPDATE users SET status = 'locked' WHERE email = ?", "alex@example.com");
        ResponseEntity<String> lockedWrong = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"alex@example.com\",\"password\":\"definitely-wrong\"}")
                .retrieve()
                .toEntity(String.class);
        ResponseEntity<String> lockedRight = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"alex@example.com\",\"password\":\"correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(lockedWrong.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(lockedRight.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(lockedWrong.getBody()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(jsonField(lockedWrong.getBody(), "message")).isEqualTo(jsonField(lockedRight.getBody(), "message"));
    }

    @Test
    void healthzStillWorksWhenAuthIsConfigured() {
        ResponseEntity<String> healthz =
                restClient().get().uri("/api/v1/healthz").retrieve().toEntity(String.class);
        assertThat(healthz.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthz.getBody()).contains("\"status\"").contains("\"ok\"");
    }

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    private static String registerBody(String email, String handle, String password, String displayName) {
        return """
                {"email":"%s","handle":"%s","password":"%s","displayName":"%s"}
                """.formatted(email, handle, password, displayName);
    }

    private static String refreshCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return "";
        }
        for (String cookie : cookies) {
            if (cookie.startsWith("refresh=")) {
                return cookie.substring("refresh=".length()).split(";", 2)[0];
            }
        }
        return "";
    }

    private static String setCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        return cookies == null ? "" : String.join(";", cookies);
    }

    private static String jsonField(String body, String name) {
        if (body == null) {
            return "";
        }
        String needle = "\"" + name + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < 0 ? "" : body.substring(start, end);
    }
}
