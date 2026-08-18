package fr.projetcompensation.gymbuddy.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {

    void store(String jti, UUID userId, Instant expiresAt);

    Optional<UUID> findAllowedUserId(String jti);

    void revoke(String jti, Instant expiresAt);
}
