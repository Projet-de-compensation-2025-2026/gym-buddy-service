package fr.projetcompensation.gymbuddy.matching;

import fr.projetcompensation.gymbuddy.suggestions.WindowOverlap;
import java.util.UUID;

public record MatchingEdge(
        UUID left,
        UUID right,
        double weight,
        String activity,
        WindowOverlap.Overlap overlap,
        java.time.Instant earlierOptIn) {}
