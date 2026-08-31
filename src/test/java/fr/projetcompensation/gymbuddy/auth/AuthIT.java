package fr.projetcompensation.gymbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuthIT {

    private static final String SECRET = "test-hs256-secret-that-is-long-enough";
    private static final String PASSWORD = "correct-horse";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            REDIS.start();
        }
    }

    @BeforeAll
    static void requireRunningContainers() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for AuthIT");
        assumeTrue(POSTGRES.isRunning(), "PostgreSQL Testcontainer must stay up for AuthIT");
        assumeTrue(REDIS.isRunning(), "Redis Testcontainer must stay up for AuthIT");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning() || !REDIS.isRunning()) {
            return;
        }
        registry.add("DATABASE_URL", () -> "postgresql://%s:%s@%s:%d/%s"
                .formatted(
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        POSTGRES.getDatabaseName()));
        registry.add("REDIS_URL", () -> "redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379)));
        registry.add("JWT_ACCESS_SECRET", () -> SECRET);
    }

    @LocalServerPort
    private int port;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetUsers() {
        if (jdbcTemplate != null) {
            jdbcTemplate.update("DELETE FROM friendships");
            jdbcTemplate.update("DELETE FROM profiles");
            jdbcTemplate.update("DELETE FROM users");
        }
    }

    @Test
    void registerLoginRefreshLogoutAndLockedUser() {
        RestClient client = restClient();

        ResponseEntity<String> first = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("alex@example.com", "alex", PASSWORD, "Alex"))
                .retrieve()
                .toEntity(String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).contains("\"email\":\"alex@example.com\"").contains("\"role\":\"admin\"");

        ResponseEntity<String> duplicate = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("Alex@Example.com", "other", PASSWORD, "Other"))
                .retrieve()
                .toEntity(String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("\"code\":\"CONFLICT\"");

        ResponseEntity<String> weak = client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("blake@example.com", "blake", "short", "Blake"))
                .retrieve()
                .toEntity(String.class);
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(weak.getBody()).contains("\"code\":\"VALIDATION\"");

        ResponseEntity<String> unknown = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"missing@example.com\",\"password\":\"correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unknown.getBody())
                .contains("\"code\":\"FORBIDDEN\"")
                .contains("\"message\":\"invalid credentials\"");

        ResponseEntity<String> wrongPassword = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"alex@example.com\",\"password\":\"definitely-wrong\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongPassword.getBody())
                .contains("\"code\":\"FORBIDDEN\"")
                .contains("\"message\":\"invalid credentials\"");

        ResponseEntity<String> login = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"alex@example.com\",\"password\":\"correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access = jsonField(login.getBody(), "accessToken");
        assertThat(access).isNotBlank();
        assertThat(access.split("\\.").length).isEqualTo(3);
        String refresh = refreshCookie(login.getHeaders());
        assertThat(refresh).isNotBlank();
        assertThat(setCookie(login.getHeaders()))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=None")
                .contains("Partitioned");

        ResponseEntity<String> refreshResponse = client.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh=" + refresh)
                .retrieve()
                .toEntity(String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotated = refreshCookie(refreshResponse.getHeaders());
        assertThat(rotated).isNotBlank().isNotEqualTo(refresh);

        ResponseEntity<String> reused = client.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh=" + refresh)
                .retrieve()
                .toEntity(String.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> logout = client.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.COOKIE, "refresh=" + rotated)
                .retrieve()
                .toEntity(String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterLogout = client.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh=" + rotated)
                .retrieve()
                .toEntity(String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(afterLogout.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");

        jdbcTemplate.update("UPDATE users SET status = 'locked' WHERE email = ?", "alex@example.com");
        ResponseEntity<String> lockedWrong = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"alex@example.com\",\"password\":\"definitely-wrong\"}")
                .retrieve()
                .toEntity(String.class);
        ResponseEntity<String> lockedRight = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"alex@example.com\",\"password\":\"correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(lockedWrong.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(lockedRight.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(lockedWrong.getBody()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(jsonField(lockedWrong.getBody(), "message")).isEqualTo(jsonField(lockedRight.getBody(), "message"));
    }

    @Test
    void fsAcct05And07_passwordChangeAndCloseAccount() {
        RestClient client = restClient();
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("casey@example.com", "casey", PASSWORD, "Casey"))
                .retrieve()
                .toEntity(String.class);
        ResponseEntity<String> login = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"casey@example.com\",\"password\":\"correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        String access = jsonField(login.getBody(), "accessToken");
        String refresh = refreshCookie(login.getHeaders());

        ResponseEntity<String> wrong = client.post()
                .uri("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currentPassword\":\"nope-nope-nope\",\"newPassword\":\"new-correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> changed = client.post()
                .uri("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currentPassword\":\"correct-horse\",\"newPassword\":\"new-correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> oldRefresh = client.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh=" + refresh)
                .retrieve()
                .toEntity(String.class);
        assertThat(oldRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> oldLogin = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"casey@example.com\",\"password\":\"correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> newLogin = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"casey@example.com\",\"password\":\"new-correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccess = jsonField(newLogin.getBody(), "accessToken");

        ResponseEntity<String> closed = client.post()
                .uri("/api/v1/me/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"password\":\"new-correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> closedLogin = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"casey@example.com\",\"password\":\"new-correct-horse\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(closedLogin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jsonField(closedLogin.getBody(), "message")).isEqualTo("account is locked");
    }

    @Test
    void fsProf04_privateProfileIsStubForStrangerAndFullForOwner() {
        RestClient client = restClient();
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("blake@example.com", "blake", PASSWORD, "Blake"))
                .retrieve()
                .toEntity(String.class);
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("viewer@example.com", "viewer", PASSWORD, "Viewer"))
                .retrieve()
                .toEntity(String.class);
        String ownerAccess = jsonField(
                client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"email\":\"blake@example.com\",\"password\":\"correct-horse\"}")
                        .retrieve()
                        .toEntity(String.class)
                        .getBody(),
                "accessToken");
        String strangerAccess = jsonField(
                client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"email\":\"viewer@example.com\",\"password\":\"correct-horse\"}")
                        .retrieve()
                        .toEntity(String.class)
                        .getBody(),
                "accessToken");

        ResponseEntity<String> patched = client.patch()
                .uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"visibility":"private","bio":"secret-bio","sports":["running"],"city":"Austin, TX"}
                        """)
                .retrieve()
                .toEntity(String.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody()).contains("\"view\":\"full\"").contains("secret-bio");

        ResponseEntity<String> unauthenticated =
                client.get().uri("/api/v1/profiles/blake").retrieve().toEntity(String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> stub = client.get()
                .uri("/api/v1/profiles/blake")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(stub.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stub.getBody())
                .contains("\"view\":\"stub\"")
                .contains("\"visibility\":\"private\"")
                .doesNotContain("secret-bio")
                .doesNotContain("Austin, TX")
                .doesNotContain("running");

        ResponseEntity<String> ownerView = client.get()
                .uri("/api/v1/profiles/blake")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(ownerView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownerView.getBody())
                .contains("\"view\":\"full\"")
                .contains("secret-bio")
                .contains("Austin, TX");
    }

    @Test
    void fsFrnd_requestAcceptUnlocksPrivateProfileAndBlockHides() {
        RestClient client = restClient();
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("seed@example.com", "seed", PASSWORD, "Seed"))
                .retrieve()
                .toEntity(String.class);
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("alex@example.com", "alex", PASSWORD, "Alex"))
                .retrieve()
                .toEntity(String.class);
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("blake@example.com", "blake", PASSWORD, "Blake"))
                .retrieve()
                .toEntity(String.class);
        String alexAccess = jsonField(
                client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"email\":\"alex@example.com\",\"password\":\"correct-horse\"}")
                        .retrieve()
                        .toEntity(String.class)
                        .getBody(),
                "accessToken");
        String blakeAccess = jsonField(
                client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"email\":\"blake@example.com\",\"password\":\"correct-horse\"}")
                        .retrieve()
                        .toEntity(String.class)
                        .getBody(),
                "accessToken");
        client.patch()
                .uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + blakeAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"visibility\":\"private\",\"bio\":\"secret-bio\"}")
                .retrieve()
                .toEntity(String.class);

        ResponseEntity<String> self = client.post()
                .uri("/api/v1/friendships")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"handle\":\"alex\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        ResponseEntity<String> created = client.post()
                .uri("/api/v1/friendships")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"handle\":\"blake\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String friendshipId = jsonField(created.getBody(), "id");

        ResponseEntity<String> duplicate = client.post()
                .uri("/api/v1/friendships")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"handle\":\"blake\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> stub = client.get()
                .uri("/api/v1/profiles/blake")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(stub.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stub.getBody()).contains("\"view\":\"stub\"");

        ResponseEntity<String> accepted = client.post()
                .uri("/api/v1/friendships/" + friendshipId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + blakeAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> full = client.get()
                .uri("/api/v1/profiles/blake")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(full.getBody()).contains("\"view\":\"full\"").contains("secret-bio");

        ResponseEntity<String> blocked = client.post()
                .uri("/api/v1/blocks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + blakeAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"userId\":\"" + jsonField(created.getBody(), "requesterId") + "\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterBlock = client.post()
                .uri("/api/v1/friendships")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alexAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"handle\":\"blake\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(afterBlock.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void registerRejectsHandleThatIsAnEmail() {
        ResponseEntity<String> asEmail = restClient()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("alex@example.com", "alex@example.com", PASSWORD, "Alex"))
                .retrieve()
                .toEntity(String.class);
        assertThat(asEmail.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(asEmail.getBody()).contains("\"code\":\"VALIDATION\"").contains("\"path\":\"handle\"");
    }

    @Test
    void unauthenticatedAdminJsonIsUtf8AndMemberIsNotFound() {
        RestClient client = restClient();
        ResponseEntity<String> anonymous =
                client.get().uri("/api/v1/admin/users").retrieve().toEntity(String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).containsIgnoringCase("charset=UTF-8");
        assertThat(anonymous.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");

        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("first@example.com", "first", PASSWORD, "First"))
                .retrieve()
                .toEntity(String.class);
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("member@example.com", "member", PASSWORD, "Member"))
                .retrieve()
                .toEntity(String.class);
        ResponseEntity<String> login = client.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"member@example.com\",\"password\":\"" + PASSWORD + "\"}")
                .retrieve()
                .toEntity(String.class);
        String access = jsonField(login.getBody(), "accessToken");
        ResponseEntity<String> member = client.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .retrieve()
                .toEntity(String.class);
        assertThat(member.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(member.getBody()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void fsAdm09_memberAdminSurfaceIsNotFoundBeforeValidation() {
        RestClient client = restClient();
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("admin@example.com", "admin", PASSWORD, "Admin"))
                .retrieve()
                .toEntity(String.class);
        client.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registerBody("member@example.com", "member", PASSWORD, "Member"))
                .retrieve()
                .toEntity(String.class);
        String staffAccess = jsonField(
                client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"email\":\"admin@example.com\",\"password\":\"" + PASSWORD + "\"}")
                        .retrieve()
                        .toEntity(String.class)
                        .getBody(),
                "accessToken");
        String memberAccess = jsonField(
                client.post()
                        .uri("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"email\":\"member@example.com\",\"password\":\"" + PASSWORD + "\"}")
                        .retrieve()
                        .toEntity(String.class)
                        .getBody(),
                "accessToken");
        String targetId = "33333333-3333-4333-8333-333333333333";

        ResponseEntity<String> anonymousContent =
                client.get().uri("/api/v1/admin/content").retrieve().toEntity(String.class);
        assertThat(anonymousContent.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousContent.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .containsIgnoringCase("charset=UTF-8");
        assertThat(anonymousContent.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");

        ResponseEntity<String> memberContent = client.get()
                .uri("/api/v1/admin/content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(memberContent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(memberContent.getBody()).contains("\"code\":\"NOT_FOUND\"").doesNotContain("Bad Request");

        ResponseEntity<String> staffContent = client.get()
                .uri("/api/v1/admin/content?type=post")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffAccess)
                .retrieve()
                .toEntity(String.class);
        assertThat(staffContent.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> memberRole = client.patch()
                .uri("/api/v1/admin/users/" + targetId + "/role")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .retrieve()
                .toEntity(String.class);
        assertThat(memberRole.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(memberRole.getBody()).contains("\"code\":\"NOT_FOUND\"").doesNotContain("VALIDATION");

        ResponseEntity<String> anonymousRole = client.patch()
                .uri("/api/v1/admin/users/" + targetId + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .retrieve()
                .toEntity(String.class);
        assertThat(anonymousRole.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousRole.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");

        ResponseEntity<String> staffRole = client.patch()
                .uri("/api/v1/admin/users/" + targetId + "/role")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .retrieve()
                .toEntity(String.class);
        assertThat(staffRole.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(staffRole.getBody()).contains("\"code\":\"VALIDATION\"");

        ResponseEntity<String> memberHide = client.post()
                .uri("/api/v1/admin/content/post/" + targetId + "/hide")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .retrieve()
                .toEntity(String.class);
        assertThat(memberHide.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(memberHide.getBody()).contains("\"code\":\"NOT_FOUND\"").doesNotContain("VALIDATION");

        ResponseEntity<String> anonymousHide = client.post()
                .uri("/api/v1/admin/content/post/" + targetId + "/hide")
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .retrieve()
                .toEntity(String.class);
        assertThat(anonymousHide.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousHide.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");

        ResponseEntity<String> staffHide = client.post()
                .uri("/api/v1/admin/content/post/" + targetId + "/hide")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .retrieve()
                .toEntity(String.class);
        assertThat(staffHide.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(staffHide.getBody()).contains("\"code\":\"VALIDATION\"");
    }

    @Test
    void healthzStillWorksWhenAuthIsConfigured() {
        ResponseEntity<String> healthz =
                restClient().get().uri("/api/v1/healthz").retrieve().toEntity(String.class);
        assertThat(healthz.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthz.getBody()).contains("\"status\"").contains("\"ok\"");
    }

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    private static String registerBody(String email, String handle, String password, String displayName) {
        return """
                {"email":"%s","handle":"%s","password":"%s","displayName":"%s"}
                """.formatted(email, handle, password, displayName);
    }

    private static String refreshCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return "";
        }
        for (String cookie : cookies) {
            if (cookie.startsWith("refresh=")) {
                return cookie.substring("refresh=".length()).split(";", 2)[0];
            }
        }
        return "";
    }

    private static String setCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        return cookies == null ? "" : String.join(";", cookies);
    }

    private static String jsonField(String body, String name) {
        if (body == null) {
            return "";
        }
        String needle = "\"" + name + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < 0 ? "" : body.substring(start, end);
    }
}
