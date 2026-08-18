package fr.projetcompensation.gymbuddy.auth;

import io.lettuce.core.api.sync.RedisCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class RedisRefreshTokenStore implements RefreshTokenStore {

    static final String ALLOW_PREFIX = "auth:refresh:allow:";
    static final String DENY_PREFIX = "auth:refresh:deny:";

    private final RedisCommands<String, String> redis;
    private final Clock clock;

    public RedisRefreshTokenStore(RedisCommands<String, String> redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void store(String jti, UUID userId, Instant expiresAt) {
        long ttl = ttlSeconds(expiresAt);
        if (ttl > 0) {
            redis.setex(ALLOW_PREFIX + jti, ttl, userId.toString());
        }
    }

    @Override
    public Optional<UUID> findAllowedUserId(String jti) {
        if (redis.exists(DENY_PREFIX + jti) > 0) {
            return Optional.empty();
        }
        String value = redis.get(ALLOW_PREFIX + jti);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value));
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        redis.del(ALLOW_PREFIX + jti);
        long ttl = ttlSeconds(expiresAt);
        if (ttl > 0) {
            redis.setex(DENY_PREFIX + jti, ttl, "1");
        }
    }

    private long ttlSeconds(Instant expiresAt) {
        long seconds = Duration.between(clock.instant(), expiresAt).getSeconds();
        return Math.max(seconds, 0);
    }
}
