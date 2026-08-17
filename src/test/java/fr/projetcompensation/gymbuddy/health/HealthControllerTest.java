package fr.projetcompensation.gymbuddy.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadinessChecker readinessChecker;

    @Test
    void healthzReturnsOkWithoutCheckingDependencies() throws Exception {
        mockMvc.perform(get("/api/v1/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void readyzReturnsOkWhenDependenciesAreReachable() throws Exception {
        when(readinessChecker.failedDependencies()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/readyz"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void readyzNamesFailedDependencyOn503() throws Exception {
        when(readinessChecker.failedDependencies())
                .thenReturn(List.of(new HealthStatus.FailedDependency("postgres", "unreachable")));

        mockMvc.perform(get("/api/v1/readyz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.details[0].path").value("postgres"))
                .andExpect(jsonPath("$.details[0].issue").value("unreachable"));
    }
}
