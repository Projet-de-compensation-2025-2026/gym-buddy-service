package fr.projetcompensation.gymbuddy.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.projetcompensation.gymbuddy.health.HealthController;
import fr.projetcompensation.gymbuddy.health.ReadinessChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import(ApiCorsConfiguration.class)
@TestPropertySource(properties = "gym-buddy.cors.allowed-origins=http://127.0.0.1:4200")
class ApiCorsLocalOriginTest {

    private static final String LOOPBACK = "http://127.0.0.1:4200";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadinessChecker readinessChecker;

    @Test
    void loopbackOriginPreflightIsAllowedWithCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/healthz")
                        .header(HttpHeaders.ORIGIN, LOOPBACK)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOOPBACK))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
