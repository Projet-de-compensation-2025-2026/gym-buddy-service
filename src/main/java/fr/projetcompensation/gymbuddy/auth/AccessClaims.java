package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.users.UserRole;
import java.util.UUID;

public record AccessClaims(UUID userId, String handle, UserRole role, java.time.Instant expiresAt) {
    public AccessClaims(UUID userId, String handle, UserRole role) {
        this(userId, handle, role, java.time.Instant.EPOCH);
    }
}
