package fr.projetcompensation.gymbuddy.media.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.auth.http.AccessTokenFilter;
import fr.projetcompensation.gymbuddy.http.ApiExceptionHandler;
import fr.projetcompensation.gymbuddy.users.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = MediaController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AccessTokenFilter.class))
@Import({ApiExceptionHandler.class, MediaExceptionHandler.class})
class MediaNotConfiguredControllerTest {

    private static final AuthPrincipal MEMBER =
            new AuthPrincipal(UUID.fromString("11111111-1111-4111-8111-111111111111"), "alex", UserRole.MEMBER);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fsMed02_memberPostMediaWhenStorageMissingIs503Not401() throws Exception {
        mockMvc.perform(post("/api/v1/media")
                        .requestAttr(AuthPrincipal.REQUEST_ATTRIBUTE, MEMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"avatar","mime":"image/png","bytes":70}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.details.objectStorage").value("not configured"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void fsMed06_memberGetUrlWhenStorageMissingIs503Not401() throws Exception {
        mockMvc.perform(get("/api/v1/media/{id}/url", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
                        .requestAttr(AuthPrincipal.REQUEST_ATTRIBUTE, MEMBER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.error.code").doesNotExist());
    }
}
