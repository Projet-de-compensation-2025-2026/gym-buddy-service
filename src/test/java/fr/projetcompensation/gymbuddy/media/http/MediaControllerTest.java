package fr.projetcompensation.gymbuddy.media.http;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.auth.http.AccessTokenFilter;
import fr.projetcompensation.gymbuddy.http.ApiExceptionHandler;
import fr.projetcompensation.gymbuddy.media.CreateUpload;
import fr.projetcompensation.gymbuddy.media.MediaService;
import fr.projetcompensation.gymbuddy.users.UserRole;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = MediaController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AccessTokenFilter.class))
@Import({ApiExceptionHandler.class, MediaExceptionHandler.class})
class MediaControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final AuthPrincipal MEMBER = new AuthPrincipal(USER_ID, "alex", UserRole.MEMBER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    @Test
    void fsMed02_postMediaWithMemberJwtIsCreatedNot401() throws Exception {
        UUID mediaId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        when(mediaService.create(eq(USER_ID), eq("avatar"), eq("image/png"), eq(70L)))
                .thenReturn(new CreateUpload(
                        mediaId, URI.create("http://minio.example/put"), Instant.parse("2026-08-31T12:01:00Z")));

        mockMvc.perform(post("/api/v1/media")
                        .requestAttr(AuthPrincipal.REQUEST_ATTRIBUTE, MEMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"avatar","mime":"image/png","bytes":70}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.uploadUrl").value("http://minio.example/put"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void fsMed06_missingIdWithMemberJwtIsNotFoundNot401() throws Exception {
        UUID missing = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        when(mediaService.url(eq(USER_ID), eq(missing))).thenThrow(AuthException.notFound("media not found"));

        mockMvc.perform(get("/api/v1/media/{id}/url", missing).requestAttr(AuthPrincipal.REQUEST_ATTRIBUTE, MEMBER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("media not found"));
    }

    @Test
    void anonymousMediaIsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/media")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"avatar","mime":"image/png","bytes":70}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
