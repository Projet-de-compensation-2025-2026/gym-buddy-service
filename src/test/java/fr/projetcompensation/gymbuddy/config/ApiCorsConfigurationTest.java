package fr.projetcompensation.gymbuddy.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import(ApiCorsConfiguration.class)
class ApiCorsConfigurationTest {

    private static final String PAGES_ORIGIN = ApiCorsConfiguration.PAGES_ORIGIN;
    private static final String FOREIGN_ORIGIN = "https://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadinessChecker readinessChecker;

    @Test
    void pagesOriginPreflightIsAllowedWithCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/healthz")
                        .header(HttpHeaders.ORIGIN, PAGES_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PAGES_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void pagesOriginActualRequestEchoesOriginAndCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/healthz").header(HttpHeaders.ORIGIN, PAGES_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PAGES_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void foreignOriginPreflightIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/healthz")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }
}
