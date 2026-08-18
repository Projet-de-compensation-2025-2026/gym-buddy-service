package fr.projetcompensation.gymbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String SECRET = "test-hs256-secret-that-is-long-enough";
    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    private final JwtTokenService tokens = new JwtTokenService(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void accessTokenHasRequiredClaimsAndNoEmail() {
        User user = user();

        IssuedTokens issued = tokens.issue(user);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(issued.accessToken())
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(user.id().toString());
        assertThat(claims.get("handle", String.class)).isEqualTo("alex");
        assertThat(claims.get("role", String.class)).isEqualTo("member");
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
        assertThat(claims.get("email")).isNull();
        assertThat(claims.getExpiration()).isEqualTo(Date.from(NOW.plus(JwtTokenService.ACCESS_TTL)));
        assertThat(tokens.parseAccess(issued.accessToken()))
                .contains(new AccessClaims(user.id(), "alex", UserRole.MEMBER));
    }

    @Test
    void refreshTokenHasSubJtiAndTyp() {
        IssuedTokens issued = tokens.issue(user());
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(issued.refreshToken())
                .getPayload();

        assertThat(claims.getSubject()).isNotBlank();
        assertThat(claims.getId()).isEqualTo(issued.refreshJti());
        assertThat(claims.get("typ", String.class)).isEqualTo("refresh");
        assertThat(claims.getExpiration()).isEqualTo(Date.from(NOW.plus(JwtTokenService.REFRESH_TTL)));
    }

    @Test
    void parseAccessRejectsRefreshTypAndTamperedTokens() {
        IssuedTokens issued = tokens.issue(user());

        assertThat(tokens.parseAccess(issued.refreshToken())).isEmpty();
        assertThat(tokens.parseAccess(issued.accessToken() + "x")).isEmpty();
        assertThat(tokens.parseRefresh("not-a-jwt")).isEmpty();
    }

    private static User user() {
        return new User(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alex@example.com",
                "alex",
                "hashed",
                UserRole.MEMBER,
                UserStatus.ACTIVE,
                NOW);
    }
}
