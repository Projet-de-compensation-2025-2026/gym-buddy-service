package fr.projetcompensation.gymbuddy.admin.http;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.projetcompensation.gymbuddy.admin.AdminPage;
import fr.projetcompensation.gymbuddy.admin.AdminService;
import fr.projetcompensation.gymbuddy.auth.AccessClaims;
import fr.projetcompensation.gymbuddy.auth.TokenService;
import fr.projetcompensation.gymbuddy.auth.http.AccessTokenFilter;
import fr.projetcompensation.gymbuddy.http.ApiExceptionHandler;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = AdminController.class)
@Import({AccessTokenFilter.class, ApiExceptionHandler.class})
class AdminControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID STAFF_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TARGET_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String MEMBER_TOKEN = "member-access";
    private static final String STAFF_TOKEN = "staff-access";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokens;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private AdminService admin;

    @Test
    void fsAdm09_getAdminContentMemberIsNotFoundBeforeValidation() throws Exception {
        authenticate(MEMBER_TOKEN, member());
        authenticate(STAFF_TOKEN, staff());
        when(admin.listContent(eq(STAFF_ID), eq("post"), any(), any(), any(), any()))
                .thenReturn(new AdminPage<>(List.of(), null, 20));

        expectUnauthenticated(get("/api/v1/admin/content"));
        expectMemberNotFound(get("/api/v1/admin/content").header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN)));
        verify(admin, never()).listContent(any(), any(), any(), any(), any(), any());

        mockMvc.perform(get("/api/v1/admin/content")
                        .header(HttpHeaders.AUTHORIZATION, bearer(STAFF_TOKEN))
                        .param("type", "post"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/content").header(HttpHeaders.AUTHORIZATION, bearer(STAFF_TOKEN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.details[0].path").value("type"));
    }

    @Test
    void fsAdm09_patchRoleMemberIsNotFoundBeforeValidation() throws Exception {
        authenticate(MEMBER_TOKEN, member());
        authenticate(STAFF_TOKEN, staff());
        String path = "/api/v1/admin/users/" + TARGET_ID + "/role";

        expectUnauthenticated(
                patch(path).contentType(MediaType.APPLICATION_JSON).content(""));
        expectMemberNotFound(patch(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(""));

        mockMvc.perform(patch(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(STAFF_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        verify(admin, never()).changeRole(any(), any(), any(), any());
    }

    @Test
    void fsAdm09_postHideMemberIsNotFoundBeforeValidation() throws Exception {
        authenticate(MEMBER_TOKEN, member());
        authenticate(STAFF_TOKEN, staff());
        String path = "/api/v1/admin/content/post/" + TARGET_ID + "/hide";

        expectUnauthenticated(post(path).contentType(MediaType.APPLICATION_JSON).content(""));
        expectMemberNotFound(post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(""));

        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(STAFF_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        verify(admin, never()).hide(any(), any(), any(), any());
    }

    private void authenticate(String token, User user) {
        when(tokens.parseAccess(token))
                .thenReturn(Optional.of(new AccessClaims(user.id(), user.handle(), user.role())));
        when(users.findById(user.id())).thenReturn(Optional.of(user));
    }

    private ResultActions expectUnauthenticated(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("charset=UTF-8")));
    }

    private ResultActions expectMemberNotFound(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("not found"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static User member() {
        return new User(MEMBER_ID, "member@example.com", "member", "hash", UserRole.MEMBER, UserStatus.ACTIVE, NOW);
    }

    private static User staff() {
        return new User(STAFF_ID, "admin@example.com", "admin", "hash", UserRole.ADMIN, UserStatus.ACTIVE, NOW);
    }
}
