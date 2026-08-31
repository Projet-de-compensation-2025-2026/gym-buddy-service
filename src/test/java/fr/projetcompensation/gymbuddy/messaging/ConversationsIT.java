package fr.projetcompensation.gymbuddy.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.UUID;
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
class ConversationsIT {

    private static final String SECRET = "test-hs256-secret-that-is-long-enough";
    private static final String PASSWORD = "correct-horse";

    /** Live v1.1.0 pair: Java {@code UUID.compareTo} lo is not PostgreSQL unsigned lo. */
    private static final UUID PG_LO = UUID.fromString("760a6a67-3de6-4b09-8437-9d292869512d");

    private static final UUID JAVA_LO = UUID.fromString("afcada6c-62a6-4059-853e-5f256b5d86f1");

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
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for ConversationsIT");
        assumeTrue(POSTGRES.isRunning(), "PostgreSQL Testcontainer must stay up for ConversationsIT");
        assumeTrue(REDIS.isRunning(), "Redis Testcontainer must stay up for ConversationsIT");
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
    void fsMsg01_acceptedFriendsPostConversationsReturns201Not500() {
        assertThat(JAVA_LO.compareTo(PG_LO)).isNegative();
        RestClient client = restClient();
        register(client, "seed@example.com", "seed", "Seed");
        register(client, "alex@example.com", "alex", "Alex");
        String hash =
                jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE handle = ?", String.class, "alex");
        jdbcTemplate.update("""
                INSERT INTO users (id, email, handle, password_hash, role, status)
                VALUES (?, ?, ?, ?, 'member', 'active')
                """, PG_LO, "pairlo@example.com", "pairlo", hash);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, handle, password_hash, role, status)
                VALUES (?, ?, ?, ?, 'member', 'active')
                """, JAVA_LO, "pairhi@example.com", "pairhi", hash);
        jdbcTemplate.update("INSERT INTO profiles (user_id, display_name) VALUES (?, ?)", PG_LO, "Pair Lo");
        jdbcTemplate.update("INSERT INTO profiles (user_id, display_name) VALUES (?, ?)", JAVA_LO, "Pair Hi");

        String loAccess = login(client, "pairlo@example.com");
        String hiAccess = login(client, "pairhi@example.com");
        String strangerAccess = login(client, "alex@example.com");

        ResponseEntity<String> requested = client.post()
                .uri("/api/v1/friendships")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"handle\":\"pairhi\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String friendshipId = jsonField(requested.getBody(), "id");

        ResponseEntity<String> accepted = client.post()
                .uri("/api/v1/friendships/" + friendshipId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hiAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> opened = client.post()
                .uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"userId\":\"" + JAVA_LO + "\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(opened.getBody())
                .doesNotContain("Internal Server Error")
                .contains("\"id\"")
                .contains(JAVA_LO.toString());
        String conversationId = jsonField(opened.getBody(), "id");
        assertThat(conversationId).isNotBlank();
        UUID storedLo = jdbcTemplate.queryForObject(
                "SELECT user_lo FROM conversations WHERE id = ?", UUID.class, UUID.fromString(conversationId));
        assertThat(storedLo).isEqualTo(PG_LO);

        ResponseEntity<String> again = client.post()
                .uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hiAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"userId\":\"" + PG_LO + "\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jsonField(again.getBody(), "id")).isEqualTo(conversationId);

        ResponseEntity<String> stranger = client.post()
                .uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"userId\":\"" + JAVA_LO + "\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stranger.getBody()).contains("\"code\":\"FORBIDDEN\"").contains("not friends");
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
