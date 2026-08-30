package fr.projetcompensation.gymbuddy.matching;

import java.time.Instant;
import java.util.UUID;

public record MatchingOptIn(UUID userId, Instant createdAt) {}
