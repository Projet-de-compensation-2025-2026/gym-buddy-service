package fr.projetcompensation.gymbuddy.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
class ReadinessIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:latest")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "DATABASE_URL",
                () -> "postgresql://%s:%s@%s:%d/%s"
                        .formatted(
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword(),
                                POSTGRES.getHost(),
                                POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                                POSTGRES.getDatabaseName()));
        registry.add("S3_ENDPOINT", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("S3_BUCKET", () -> "gym-buddy");
        registry.add("S3_ACCESS_KEY", () -> "minioadmin");
        registry.add("S3_SECRET_KEY", () -> "minioadmin");
        registry.add("S3_REGION", () -> "us-east-1");
    }

    @LocalServerPort
    private int port;

    @Test
    void readyzReturnsOkWhenPostgresAndMinioAreReachable() {
        RestClient client = RestClient.create();
        ResponseEntity<HealthStatus> response = client.get()
                .uri("http://127.0.0.1:" + port + "/api/v1/readyz")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(HealthStatus.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ok");
        assertThat(response.getBody().details()).isNull();
    }
}
