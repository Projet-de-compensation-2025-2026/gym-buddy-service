package fr.projetcompensation.gymbuddy.auth.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.auth.AuthService;
import fr.projetcompensation.gymbuddy.auth.AuthSession;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.auth.IssuedTokens;
import fr.projetcompensation.gymbuddy.auth.JwtTokenService;
import fr.projetcompensation.gymbuddy.auth.RegisteredUser;
import fr.projetcompensation.gymbuddy.http.ApiExceptionHandler;
import fr.projetcompensation.gymbuddy.users.UserRole;
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
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AccessTokenFilter.class))
@Import(ApiExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void fsAcct01_registerReturnsCreatedUser() throws Exception {
        when(authService.register(any()))
                .thenReturn(new RegisteredUser(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "alex@example.com",
                        "alex",
                        "Alex",
                        UserRole.ADMIN));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex@example.com","handle":"alex","password":"correct-horse","displayName":"Alex"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.handle").value("alex"))
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void fsAcct02_duplicateEmailReturnsConflictEnvelope() throws Exception {
        when(authService.register(any()))
                .thenThrow(AuthException.conflict("email already registered", new FieldIssue("email", "duplicate")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex@example.com","handle":"alex","password":"correct-horse","displayName":"Alex"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].path").value("email"));
    }

    @Test
    void fsAcct03_weakPasswordReturnsValidationEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex@example.com","handle":"alex","password":"short","displayName":"Alex"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.details[0].path").value("password"))
                .andExpect(jsonPath("$.error.details[0].issue").value("size"));
    }

    @Test
    void fsAcct03_passwordEqualToEmailReturnsValidationEnvelope() throws Exception {
        when(authService.register(any()))
                .thenThrow(AuthException.validation(
                        "password does not meet requirements", new FieldIssue("password", "mustNotMatchIdentity")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex@example.com","handle":"alex","password":"alex@example.com","displayName":"Alex"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.details[0].issue").value("mustNotMatchIdentity"));
    }

    @Test
    void registerAcceptsSingleCharacterHandleFromSpec() throws Exception {
        when(authService.register(any()))
                .thenReturn(new RegisteredUser(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "alex@example.com",
                        "a",
                        "Alex",
                        UserRole.MEMBER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alex@example.com","handle":"a","password":"correct-horse","displayName":"Alex"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.handle").value("a"));
    }

    @Test
    void fsAcct04_loginSetsHttpOnlySecureRefreshCookie() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthSession(new IssuedTokens(
                        "access.jwt",
                        "refresh.jwt",
                        "jti-1",
                        Instant.parse("2026-09-01T10:00:00Z"),
                        JwtTokenService.ACCESS_TTL)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.tokenType").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").doesNotExist())
                .andExpect(cookie().value("refresh", "refresh.jwt"))
                .andExpect(cookie().httpOnly("refresh", true))
                .andExpect(cookie().secure("refresh", true))
                .andExpect(cookie().path("refresh", "/api/v1/auth"))
                .andExpect(cookie().sameSite("refresh", "Lax"));
    }

    @Test
    void loginUnknownEmailReturnsForbiddenInvalidCredentials() throws Exception {
        when(authService.login(any())).thenThrow(AuthException.forbidden("invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("invalid credentials"));
    }

    @Test
    void lockedUserLoginReturnsForbiddenEnvelope() throws Exception {
        when(authService.login(any())).thenThrow(AuthException.forbidden("account is locked"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("account is locked"));
    }

    @Test
    void fsAcct06_logoutClearsRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new jakarta.servlet.http.Cookie("refresh", "refresh.jwt")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh", 0))
                .andExpect(cookie().path("refresh", "/api/v1/auth"));
    }

    @Test
    void fsAcct05_changePasswordReturnsNoContentAndClearsCookie() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mockMvc.perform(post("/api/v1/auth/password")
                        .requestAttr(
                                AuthPrincipal.REQUEST_ATTRIBUTE, new AuthPrincipal(userId, "alex", UserRole.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"correct-horse","newPassword":"new-correct-horse"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh", 0));
    }

    @Test
    void fsAcct07_closeAccountReturnsNoContent() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mockMvc.perform(post("/api/v1/me/close")
                        .requestAttr(
                                AuthPrincipal.REQUEST_ATTRIBUTE, new AuthPrincipal(userId, "alex", UserRole.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"correct-horse\"}"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh", 0));
    }

    @Test
    void missingRefreshCookieIsUnauthenticated() throws Exception {
        doThrow(AuthException.unauthenticated("refresh credential is missing"))
                .when(authService)
                .refresh(null);

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
