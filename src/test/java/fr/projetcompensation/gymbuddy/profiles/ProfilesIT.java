package fr.projetcompensation.gymbuddy.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ProfilesIT {

    private static final String SECRET = "test-hs256-secret-that-is-long-enough";
    private static final String PASSWORD = "correct-horse";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
        }
    }

    @BeforeAll
    static void requireRunningContainers() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for ProfilesIT");
        assumeTrue(POSTGRES.isRunning(), "PostgreSQL Testcontainer must stay up for ProfilesIT");
        assumeTrue(REDIS.isRunning(), "Redis Testcontainer must stay up for ProfilesIT");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning() || !REDIS.isRunning()) {
            return;
        }
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

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetUsers() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
        }
    }

    @Test
    void fsProf02And06_visibilityOnlyKeepsSportsWindowsAndEliteIs422() {
        RestClient client = restClient();
        register(client, "blake@example.com", "blake", "Blake");
        String access = login(client, "blake@example.com");

        ResponseEntity<String> filled = client.patch()
                .uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"visibility":"public","bio":"spotter","sports":["running","hiit"],
                         "experienceLevel":"intermediate","city":"Porto","lat":41.15,"lng":-8.62,
                         "preferredWindows":[{"weekday":1,"start":"07:00","end":"08:30"}]}
                        """)
                .retrieve()
                .toEntity(String.class);
        assertThat(filled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filled.getBody()).contains("running").contains("07:00");

        ResponseEntity<String> visibilityOnly = client.patch()
                .uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"visibility\":\"private\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(visibilityOnly.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(visibilityOnly.getBody())
                .contains("\"visibility\":\"private\"")
                .contains("running")
                .contains("hiit")
                .contains("spotter")
                .contains("Porto")
                .contains("07:00")
                .contains("\"experienceLevel\":\"intermediate\"");

        ResponseEntity<String> byHandle = client.get()
                .uri("/api/v1/profiles/blake")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .retrieve()
                .toEntity(String.class);
        assertThat(byHandle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byHandle.getBody()).contains("07:00").contains("08:30").contains("running");

        ResponseEntity<String> elite = client.patch()
                .uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"experienceLevel\":\"elite\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(elite.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(elite.getBody()).contains("\"code\":\"VALIDATION\"");
    }

    private void register(RestClient client, String email, String handle, String displayName) {
        ResponseEntity<String> registered = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","handle":"%s","password":"%s","displayName":"%s"}
                        """.formatted(email, handle, PASSWORD, displayName))
                .retrieve()
                .toEntity(String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String login(RestClient client, String email) {
        ResponseEntity<String> login = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .retrieve()
                .toEntity(String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return jsonField(login.getBody(), "accessToken");
    }

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
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
