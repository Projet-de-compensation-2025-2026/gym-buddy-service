package fr.projetcompensation.gymbuddy.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class MediaIT {

    private static final String SECRET = "test-hs256-secret-that-is-long-enough";
    private static final String PASSWORD = "correct-horse";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
            MINIO.start();
        }
    }

    @BeforeAll
    static void requireRunningContainers() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for MediaIT");
        assumeTrue(POSTGRES.isRunning(), "PostgreSQL Testcontainer must stay up for MediaIT");
        assumeTrue(REDIS.isRunning(), "Redis Testcontainer must stay up for MediaIT");
        assumeTrue(MINIO.isRunning(), "MinIO Testcontainer must stay up for MediaIT");
        URI endpoint = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        try (S3Client client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("minioadmin", "minioadmin")))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            client.createBucket(
                    CreateBucketRequest.builder().bucket("gym-buddy").build());
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning() || !REDIS.isRunning() || !MINIO.isRunning()) {
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
        String s3 = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        registry.add("S3_ENDPOINT", () -> s3);
        registry.add("S3_BUCKET", () -> "gym-buddy");
        registry.add("S3_ACCESS_KEY", () -> "minioadmin");
        registry.add("S3_SECRET_KEY", () -> "minioadmin");
        registry.add("S3_REGION", () -> "us-east-1");
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
    void fsMed02And06_memberPostMediaIs201AndMissingUrlIs404() {
        RestClient client = restClient();
        register(client, "seed@example.com", "seed", "Seed");
        register(client, "alex@example.com", "alex", "Alex");
        String access = login(client, "alex@example.com");

        ResponseEntity<String> readyz =
                client.get().uri("/api/v1/readyz").retrieve().toEntity(String.class);
        assertThat(readyz.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readyz.getBody()).contains("\"status\":\"ok\"");

        ResponseEntity<String> created = client.post()
                .uri("/api/v1/media")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"kind":"avatar","mime":"image/png","bytes":70}
                        """)
                .retrieve()
                .toEntity(String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody())
                .doesNotContain("UNAUTHENTICATED")
                .doesNotContain("media is not configured")
                .contains("\"mediaId\"")
                .contains("\"uploadUrl\"");

        ResponseEntity<String> missing = client.get()
                .uri("/api/v1/media/" + UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb") + "/url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .retrieve()
                .toEntity(String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("\"code\":\"NOT_FOUND\"").doesNotContain("UNAUTHENTICATED");
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
