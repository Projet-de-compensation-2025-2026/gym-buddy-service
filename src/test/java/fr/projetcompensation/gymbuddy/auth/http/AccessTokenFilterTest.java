package fr.projetcompensation.gymbuddy.auth.http;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
@Import(AccessTokenFilter.class)
class AccessTokenFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadinessChecker readinessChecker;

    @Test
    void healthzRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/healthz")).andExpect(status().isOk());
    }

    @Test
    void publicAuthRoutesDoNotRequireAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/auth/login")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNotFound());
    }

    @Test
    void protectedRouteWithoutAccessTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/not-a-public-route"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unauthenticatedAdminJsonIsUtf8() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")));
    }

    @Test
    void optionsPreflightIsNotBlockedByAccessToken() throws Exception {
        mockMvc.perform(options("/api/v1/not-a-public-route")).andExpect(status().isNotFound());
    }
}
