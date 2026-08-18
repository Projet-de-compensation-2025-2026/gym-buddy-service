package fr.projetcompensation.gymbuddy.auth;

import java.time.Instant;
import java.util.UUID;

public record RefreshClaims(UUID userId, String jti, Instant expiresAt) {}
