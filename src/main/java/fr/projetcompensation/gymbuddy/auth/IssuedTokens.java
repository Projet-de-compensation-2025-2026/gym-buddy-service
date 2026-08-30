package fr.projetcompensation.gymbuddy.auth;

import java.time.Duration;
import java.time.Instant;

public record IssuedTokens(
        String accessToken, String refreshToken, String refreshJti, Instant refreshExpiresAt, Duration accessTtl) {}
