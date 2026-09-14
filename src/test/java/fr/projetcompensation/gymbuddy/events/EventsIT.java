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

    static final PostgreSQLContainer POSTGRES = fr.projetcompensation.gymbuddy.support.PostgresTestContainer.create();

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine")
            .withCreateContainerCmdModifier(fr.projetcompensation.gymbuddy.support.IsolatedContainers::configure)
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

    @Autowired
    private fr.projetcompensation.gymbuddy.matching.MatchingService matching;

    @Autowired
    private fr.projetcompensation.gymbuddy.friends.FriendshipService friendships;

    @Autowired
    private EventService eventService;

    @Test
    void weeklyMatchInvitesPublicStrangerWithoutExposingPrivateSession() throws Exception {
        RestClient client = restClient();
        registerAndLogin(client, "staff@example.com", "staff", "Staff");
        String alexAccess = registerAndLogin(client, "match.alex@example.com", "matchalex", "Alex");
        String blakeAccess = registerAndLogin(client, "match.blake@example.com", "matchblake", "Blake");
        String caseyAccess = registerAndLogin(client, "match.casey@example.com", "matchcasey", "Casey");
        jdbcTemplate.update("""
                UPDATE profiles SET visibility = 'public', sports = ARRAY['running'], city = 'Paris',
                  preferred_windows = '[{"weekday":1,"start":"07:00","end":"09:00"}]'::jsonb
                WHERE user_id IN (SELECT id FROM users WHERE handle IN ('matchalex', 'matchblake'))
                """);
        var alex = jdbcTemplate.queryForObject("SELECT id FROM users WHERE handle = 'matchalex'", java.util.UUID.class);
        var blake =
                jdbcTemplate.queryForObject("SELECT id FROM users WHERE handle = 'matchblake'", java.util.UUID.class);
        matching.optIn(alex);
        matching.optIn(blake);
        var match = matching.assignCurrentWeek().getFirst();
        assertThat(match.eventId()).isNotNull();
        assertThat(client.get()
                        .uri("/api/v1/matching/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                        .retrieve()
                        .toEntity(String.class)
                        .getBody())
                .contains("\"visibility\":\"private\"");
        String applicantAccess = match.left().equals(alex) ? blakeAccess : alexAccess;
        var detail = client.get()
                .uri("/api/v1/events/" + match.eventId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicantAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).contains("\"visibility\":\"private\"");
        assertThat(client.post()
                        .uri("/api/v1/events/" + match.eventId() + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicantAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}")
                        .retrieve()
                        .toEntity(String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(client.get()
                        .uri("/api/v1/events/" + match.eventId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + caseyAccess)
                        .retrieve()
                        .toEntity(String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM friendships", Integer.class))
                .isZero();
        registerAndLogin(client, "match.dana@example.com", "matchdana", "Dana");
        registerAndLogin(client, "match.eli@example.com", "matcheli", "Eli");
        jdbcTemplate.update("""
                UPDATE profiles SET (visibility, sports, city, preferred_windows) =
                  (SELECT visibility, sports, city, preferred_windows FROM profiles WHERE user_id = ?)
                WHERE user_id IN (SELECT id FROM users WHERE handle IN ('matchdana', 'matcheli'))
                """, alex);
        var dana = jdbcTemplate.queryForObject("SELECT id FROM users WHERE handle = 'matchdana'", java.util.UUID.class);
        var eli = jdbcTemplate.queryForObject("SELECT id FROM users WHERE handle = 'matcheli'", java.util.UUID.class);
        matching.optIn(dana);
        matching.optIn(eli);
        EventService failingEvents = org.mockito.Mockito.mock(EventService.class);
        org.mockito.Mockito.when(
                        failingEvents.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    eventService.create(invocation.getArgument(0), invocation.getArgument(1));
                    throw new IllegalStateException("synthetic failure after event insert");
                });
        var failingMatching = new fr.projetcompensation.gymbuddy.matching.MatchingService(
                new fr.projetcompensation.gymbuddy.matching.JdbcMatchingStore(jdbcTemplate),
                new fr.projetcompensation.gymbuddy.suggestions.JdbcSuggestionGraph(jdbcTemplate),
                java.time.Clock.systemUTC(),
                failingEvents);
        org.assertj.core.api.Assertions.assertThatThrownBy(failingMatching::assignCurrentWeek)
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_pairs", Integer.class))
                .isEqualTo(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(matching::assignCurrentWeek);
            var second = workers.submit(matching::assignCurrentWeek);
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).size()
                            + second.get(10, java.util.concurrent.TimeUnit.SECONDS)
                                    .size())
                    .isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_pairs", Integer.class))
                .isEqualTo(2);
        assertThat(matching.me(alex).match().eventId()).isEqualTo(match.eventId());
        assertThat(matching.assignCurrentWeek()).isEmpty();
        friendships.block(blake, alex);
        assertThat(matching.me(alex).pair()).isNull();
        assertThat(matching.me(alex).match()).isNull();
        friendships.unblock(blake, alex);
        jdbcTemplate.update("UPDATE profiles SET visibility = 'private' WHERE user_id = ?", blake);
        assertThat(matching.me(alex).pair()).isNull();
        assertThat(matching.me(alex).match()).isNull();
        jdbcTemplate.update("UPDATE profiles SET visibility = 'public' WHERE user_id = ?", blake);
        jdbcTemplate.update("UPDATE users SET status = 'closed' WHERE id = ?", blake);
        assertThat(matching.me(alex).pair()).isNull();
        assertThat(matching.me(alex).match()).isNull();
    }

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
