package fr.projetcompensation.gymbuddy.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class EventsIT {

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
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for EventsIT");
        assumeTrue(POSTGRES.isRunning(), "PostgreSQL Testcontainer must stay up for EventsIT");
        assumeTrue(REDIS.isRunning(), "Redis Testcontainer must stay up for EventsIT");
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
            jdbcTemplate.update("DELETE FROM users");
        }
    }

    @Test
    void getEventsReturns200AndIncludesOrganizerSession() {
        RestClient client = restClient();
        String alexAccess = registerAndLogin(client, "alex@example.com", "alex", "Alex");
        String caseyAccess = registerAndLogin(client, "casey@example.com", "casey", "Casey");

        ResponseEntity<String> empty = client.get()
                .uri("/api/v1/events?size=50")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empty.getBody()).contains("\"data\":[]");

        String startsAt = Instant.now()
                .plus(7, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString();
        ResponseEntity<String> created = client.post()
                .uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"QA crawl session","activity":"Weightlifting","place":"Porto running track",
                         "startsAt":"%s","durationMin":60,"visibility":"friends","capacity":8}
                        """.formatted(startsAt))
                .retrieve()
                .toEntity(String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String eventId = jsonField(created.getBody(), "id");
        assertThat(eventId).isNotBlank();

        ResponseEntity<String> byId = client.get()
                .uri("/api/v1/events/" + eventId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byId.getBody()).contains(eventId);

        ResponseEntity<String> list = client.get()
                .uri("/api/v1/events?size=50")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(eventId).doesNotContain("Internal Server Error");

        ResponseEntity<String> stranger = client.get()
                .uri("/api/v1/events?size=50")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caseyAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stranger.getBody()).doesNotContain(eventId);
    }

    @Test
    void fsEvt08_applicantGetAfterCancelIncludesCancelledViewerApplication() {
        RestClient client = restClient();
        String organizerAccess = registerAndLogin(client, "evt08.org@example.com", "evt08org", "Evt Organizer");
        String applicantAccess = registerAndLogin(client, "evt08.app@example.com", "evt08app", "Evt Applicant");
        String startsAt = Instant.now()
                .plus(7, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString();
        ResponseEntity<String> created = client.post()
                .uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"QA cancel session","activity":"Yoga","place":"Studio A",
                         "startsAt":"%s","durationMin":45,"visibility":"public","capacity":2}
                        """.formatted(startsAt))
                .retrieve()
                .toEntity(String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String eventId = jsonField(created.getBody(), "id");
        assertThat(eventId).isNotBlank();

        ResponseEntity<String> applied = client.post()
                .uri("/api/v1/events/" + eventId + "/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicantAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(String.class);
        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> cancelled = client.post()
                .uri("/api/v1/events/" + eventId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(String.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody()).contains("\"cancelledAt\":");

        ResponseEntity<String> applicantGet = client.get()
                .uri("/api/v1/events/" + eventId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicantAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(applicantGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applicantGet.getBody())
                .contains("\"cancelledAt\":")
                .contains("\"viewerApplication\":")
                .contains("\"status\":\"cancelled\"")
                .contains("\"remainingSeats\":0")
                .contains("\"cancelled\":true");
    }

    private String registerAndLogin(RestClient client, String email, String handle, String displayName) {
        ResponseEntity<String> registered = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","handle":"%s","password":"%s","displayName":"%s"}
                        """.formatted(email, handle, PASSWORD, displayName))
                .retrieve()
                .toEntity(String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
