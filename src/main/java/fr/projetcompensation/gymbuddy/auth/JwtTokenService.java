package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

public final class JwtTokenService implements TokenService {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(14);
    static final String TYP_ACCESS = "access";
    static final String TYP_REFRESH = "refresh";

    private final SecretKey key;
    private final Clock clock;

    public JwtTokenService(String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT_ACCESS_SECRET is blank");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_ACCESS_SECRET must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.clock = clock;
    }

    @Override
    public IssuedTokens issue(User user) {
        Instant now = clock.instant();
        Instant accessExp = now.plus(ACCESS_TTL);
        Instant refreshExp = now.plus(REFRESH_TTL);
        String refreshJti = UUID.randomUUID().toString();
        String access = Jwts.builder()
                .subject(user.id().toString())
                .claim("handle", user.handle())
                .claim("role", user.role().wireValue())
                .claim("typ", TYP_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        String refresh = Jwts.builder()
                .subject(user.id().toString())
                .id(refreshJti)
                .claim("typ", TYP_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(refreshExp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedTokens(access, refresh, refreshJti, refreshExp, ACCESS_TTL);
    }

    @Override
    public Optional<AccessClaims> parseAccess(String token) {
        return parse(token).flatMap(claims -> {
            if (!TYP_ACCESS.equals(claims.get("typ", String.class))) {
                return Optional.empty();
            }
            try {
                UUID userId = UUID.fromString(claims.getSubject());
                String handle = claims.get("handle", String.class);
                UserRole role = UserRole.fromWire(claims.get("role", String.class));
                if (handle == null || handle.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new AccessClaims(userId, handle, role));
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        });
    }

    @Override
    public Optional<RefreshClaims> parseRefresh(String token) {
        return parse(token).flatMap(claims -> {
            if (!TYP_REFRESH.equals(claims.get("typ", String.class))) {
                return Optional.empty();
            }
            try {
                UUID userId = UUID.fromString(claims.getSubject());
                String jti = claims.getId();
                if (jti == null || jti.isBlank() || claims.getExpiration() == null) {
                    return Optional.empty();
                }
                return Optional.of(
                        new RefreshClaims(userId, jti, claims.getExpiration().toInstant()));
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        });
    }

    private Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
