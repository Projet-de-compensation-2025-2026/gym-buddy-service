package fr.projetcompensation.gymbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
    private static final String SECRET = "test-hs256-secret-that-is-long-enough";
    private static final String PASSWORD = "correct-horse";

    private FakeUserRepository users;
    private FakeProfileRepository profiles;
    private FakeRefreshTokenStore refreshTokens;
    private JwtTokenService tokens;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = new FakeUserRepository();
        profiles = new FakeProfileRepository();
        refreshTokens = new FakeRefreshTokenStore();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        tokens = new JwtTokenService(SECRET, clock);
        auth = new AuthService(
                users,
                profiles,
                new FakePasswordHasher(),
                tokens,
                refreshTokens,
                new PasswordPolicy(),
                TransactionRunner.immediate(),
                clock);
    }

    @Test
    void fsAcct01_registerCreatesUserWithEmailHandleAndDisplayName() {
        RegisteredUser registered = auth.register(register("alex@example.com", "alex", "Alex"));

        assertThat(registered.email()).isEqualTo("alex@example.com");
        assertThat(registered.handle()).isEqualTo("alex");
        assertThat(registered.displayName()).isEqualTo("Alex");
        assertThat(users.findByEmail("alex@example.com")).isPresent();
        assertThat(profiles.saved).hasSize(1);
        assertThat(profiles.saved.getFirst().displayName()).isEqualTo("Alex");
    }

    @Test
    void fsAcct02_registerRejectsDuplicateEmailIgnoringCase() {
        auth.register(register("alex@example.com", "alex", "Alex"));

        assertThatThrownBy(() -> auth.register(register("Alex@Example.com", "other", "Other")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.code()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(authEx.details()).contains(new FieldIssue("email", "duplicate"));
                });
    }

    @Test
    void fsAcct02_registerRejectsDuplicateHandleIgnoringCase() {
        auth.register(register("alex@example.com", "alex", "Alex"));

        assertThatThrownBy(() -> auth.register(register("blake@example.com", "Alex", "Blake")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.code()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(authEx.details()).contains(new FieldIssue("handle", "duplicate"));
                });
    }

    @Test
    void fsAcct03_registerRejectsPasswordShorterThan10() {
        assertThatThrownBy(() -> auth.register(register("alex@example.com", "alex", "Alex", "short")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsAcct10_firstRegisteredUserBecomesAdminAndLaterUsersAreMembers() {
        RegisteredUser first = auth.register(register("alex@example.com", "alex", "Alex"));
        RegisteredUser second = auth.register(register("blake@example.com", "blake", "Blake"));

        assertThat(first.role()).isEqualTo(UserRole.ADMIN);
        assertThat(second.role()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void fsAcct04_loginReturnsAccessJwtWithoutEmailAndRefreshJti() {
        auth.register(register("alex@example.com", "alex", "Alex"));

        AuthSession session = auth.login(new LoginCommand("alex@example.com", PASSWORD));

        AccessClaims access = tokens.parseAccess(session.tokens().accessToken()).orElseThrow();
        RefreshClaims refresh =
                tokens.parseRefresh(session.tokens().refreshToken()).orElseThrow();
        assertThat(access.handle()).isEqualTo("alex");
        assertThat(access.role()).isEqualTo(UserRole.ADMIN);
        assertThat(session.tokens().accessToken()).doesNotContain("email");
        assertThat(refresh.jti()).isEqualTo(session.tokens().refreshJti());
        assertThat(refreshTokens.findAllowedUserId(refresh.jti())).contains(access.userId());
    }

    @Test
    void loginWithUnknownEmailReturnsForbiddenInvalidCredentials() {
        assertThatThrownBy(() -> auth.login(new LoginCommand("missing@example.com", PASSWORD)))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.code()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(authEx.getMessage()).isEqualTo("invalid credentials");
                });
    }

    @Test
    void loginWithWrongPasswordReturnsForbiddenInvalidCredentials() {
        auth.register(register("alex@example.com", "alex", "Alex"));

        assertThatThrownBy(() -> auth.login(new LoginCommand("alex@example.com", "wrong-password")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.code()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(authEx.getMessage()).isEqualTo("invalid credentials");
                });
    }

    @Test
    void lockedUserLoginReturnsForbiddenWithoutRevealingPassword() {
        RegisteredUser registered = auth.register(register("alex@example.com", "alex", "Alex"));
        User stored = users.findById(registered.id()).orElseThrow();
        users.replace(new User(
                stored.id(),
                stored.email(),
                stored.handle(),
                stored.passwordHash(),
                stored.role(),
                UserStatus.LOCKED,
                stored.createdAt()));

        assertThatThrownBy(() -> auth.login(new LoginCommand("alex@example.com", "wrong-password")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.code()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(authEx.getMessage()).isEqualTo("account is locked");
                });
        assertThatThrownBy(() -> auth.login(new LoginCommand("alex@example.com", PASSWORD)))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void fsAcct06_logoutRevokesRefreshCredential() {
        auth.register(register("alex@example.com", "alex", "Alex"));
        AuthSession session = auth.login(new LoginCommand("alex@example.com", PASSWORD));
        String refresh = session.tokens().refreshToken();

        auth.logout(refresh);

        assertThat(refreshTokens.revoked(session.tokens().refreshJti())).isTrue();
        assertThatThrownBy(() -> auth.refresh(refresh))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void refreshRotatesJtiAndRejectsThePreviousCredential() {
        auth.register(register("alex@example.com", "alex", "Alex"));
        AuthSession first = auth.login(new LoginCommand("alex@example.com", PASSWORD));

        AuthSession second = auth.refresh(first.tokens().refreshToken());

        assertThat(second.tokens().refreshJti()).isNotEqualTo(first.tokens().refreshJti());
        assertThatThrownBy(() -> auth.refresh(first.tokens().refreshToken()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void fsAcct05_changePasswordRequiresCurrentAndRevokesRefresh() {
        RegisteredUser registered = auth.register(register("alex@example.com", "alex", "Alex"));
        AuthSession session = auth.login(new LoginCommand("alex@example.com", PASSWORD));

        auth.changePassword(registered.id(), PASSWORD, "new-correct-horse");

        assertThatThrownBy(() -> auth.login(new LoginCommand("alex@example.com", PASSWORD)))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        AuthSession fresh = auth.login(new LoginCommand("alex@example.com", "new-correct-horse"));
        assertThat(fresh.tokens().accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.refresh(session.tokens().refreshToken()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void fsAcct05_wrongCurrentPasswordDoesNotChangeHash() {
        RegisteredUser registered = auth.register(register("alex@example.com", "alex", "Alex"));

        assertThatThrownBy(() -> auth.changePassword(registered.id(), "wrong-password", "new-correct-horse"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        AuthSession session = auth.login(new LoginCommand("alex@example.com", PASSWORD));
        assertThat(session.tokens().accessToken()).isNotBlank();
    }

    @Test
    void fsAcct07_closeAccountHidesLoginWithGenericForbidden() {
        RegisteredUser registered = auth.register(register("alex@example.com", "alex", "Alex"));
        AuthSession session = auth.login(new LoginCommand("alex@example.com", PASSWORD));

        auth.closeAccount(registered.id(), PASSWORD);

        assertThatThrownBy(() -> auth.login(new LoginCommand("alex@example.com", PASSWORD)))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.code()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(authEx.getMessage()).isEqualTo("account is locked");
                });
        assertThatThrownBy(() -> auth.login(new LoginCommand("alex@example.com", "wrong-password")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getMessage()).isEqualTo("account is locked"));
        assertThatThrownBy(() -> auth.refresh(session.tokens().refreshToken()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void lockedUserRefreshReturnsForbidden() {
        RegisteredUser registered = auth.register(register("alex@example.com", "alex", "Alex"));
        AuthSession session = auth.login(new LoginCommand("alex@example.com", PASSWORD));
        User stored = users.findById(registered.id()).orElseThrow();
        users.replace(new User(
                stored.id(),
                stored.email(),
                stored.handle(),
                stored.passwordHash(),
                stored.role(),
                UserStatus.LOCKED,
                stored.createdAt()));

        assertThatThrownBy(() -> auth.refresh(session.tokens().refreshToken()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private static RegisterCommand register(String email, String handle, String displayName) {
        return register(email, handle, displayName, PASSWORD);
    }

    private static RegisterCommand register(String email, String handle, String displayName, String password) {
        return new RegisterCommand(email, handle, password, displayName);
    }
}
