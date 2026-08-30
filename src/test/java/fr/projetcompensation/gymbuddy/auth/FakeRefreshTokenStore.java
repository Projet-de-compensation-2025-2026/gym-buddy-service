package fr.projetcompensation.gymbuddy.auth;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FakeRefreshTokenStore implements RefreshTokenStore {

    private final Map<String, UUID> allowed = new ConcurrentHashMap<>();
    private final Set<String> denied = new HashSet<>();

    @Override
    public void store(String jti, UUID userId, Instant expiresAt) {
        allowed.put(jti, userId);
    }

    @Override
    public Optional<UUID> findAllowedUserId(String jti) {
        if (denied.contains(jti)) {
            return Optional.empty();
        }
        return Optional.ofNullable(allowed.get(jti));
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        allowed.remove(jti);
        denied.add(jti);
    }

    boolean revoked(String jti) {
        return denied.contains(jti);
    }

    @Override
    public void revokeAll(UUID userId) {
        List<String> jtis = allowed.entrySet().stream()
                .filter(entry -> entry.getValue().equals(userId))
                .map(Map.Entry::getKey)
                .toList();
        for (String jti : jtis) {
            allowed.remove(jti);
            denied.add(jti);
        }
    }
}
