package fr.projetcompensation.gymbuddy.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReadyzUnavailableTest {

    @LocalServerPort
    private int port;

    @Test
    void readyzNamesMissingPostgresAndObjectStorage() {
        RestClient client = RestClient.create();
        ResponseEntity<HealthStatus> response = client.get()
                .uri("http://127.0.0.1:" + port + "/api/v1/readyz")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> true, (request, resp) -> {})
                .toEntity(HealthStatus.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("unavailable");
        assertThat(response.getBody().details()).containsKeys("postgres", "objectStorage");
    }
}
