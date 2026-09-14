package fr.projetcompensation.gymbuddy.auth;

import io.lettuce.core.api.sync.RedisCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RedisRefreshTokenStore implements RefreshTokenStore {

    static final String ALLOW_PREFIX = "auth:refresh:allow:";
    static final String DENY_PREFIX = "auth:refresh:deny:";
    static final String USER_PREFIX = "auth:refresh:user:";

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
            String userKey = USER_PREFIX + userId;
            redis.sadd(userKey, jti);
            Long existing = redis.ttl(userKey);
            long expire = ttl;
            if (existing != null && existing > expire) {
                expire = existing;
            }
            redis.expire(userKey, expire);
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
    public Optional<UUID> consume(String jti, Instant expiresAt) {
        long ttl = ttlSeconds(expiresAt);
        if (ttl <= 0) return Optional.empty();
        String userId = redis.eval(
                """
                if redis.call('EXISTS', KEYS[2]) == 1 then return false end
                local user = redis.call('GET', KEYS[1])
                if not user then return false end
                redis.call('DEL', KEYS[1])
                redis.call('SETEX', KEYS[2], ARGV[1], '1')
                redis.call('SREM', ARGV[2] .. user, ARGV[3])
                return user
                """,
                io.lettuce.core.ScriptOutputType.VALUE,
                new String[] {ALLOW_PREFIX + jti, DENY_PREFIX + jti},
                Long.toString(ttl),
                USER_PREFIX,
                jti);
        return userId == null ? Optional.empty() : Optional.of(UUID.fromString(userId));
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        String userId = redis.get(ALLOW_PREFIX + jti);
        redis.del(ALLOW_PREFIX + jti);
        if (userId != null && !userId.isBlank()) {
            redis.srem(USER_PREFIX + userId, jti);
        }
        long ttl = ttlSeconds(expiresAt);
        if (ttl > 0) {
            redis.setex(DENY_PREFIX + jti, ttl, "1");
        }
    }

    @Override
    public void revokeAll(UUID userId) {
        String userKey = USER_PREFIX + userId;
        Set<String> jtis = redis.smembers(userKey);
        if (jtis != null) {
            for (String jti : jtis) {
                Long ttl = redis.ttl(ALLOW_PREFIX + jti);
                redis.del(ALLOW_PREFIX + jti);
                if (ttl != null && ttl > 0) {
                    redis.setex(DENY_PREFIX + jti, ttl, "1");
                }
            }
        }
        redis.del(userKey);
    }

    private long ttlSeconds(Instant expiresAt) {
        long seconds = Duration.between(clock.instant(), expiresAt).getSeconds();
        return Math.max(seconds, 0);
    }
}
