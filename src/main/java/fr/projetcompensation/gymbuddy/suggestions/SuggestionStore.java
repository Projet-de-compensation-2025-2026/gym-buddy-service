package fr.projetcompensation.gymbuddy.suggestions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuggestionStore {

    void replaceScores(UUID viewerId, Instant computedAt, List<ScoredCandidate> ranked);

    Optional<Instant> scoresComputedAt(UUID viewerId);

    List<ScoredCandidate> loadScores(UUID viewerId);

    void dismiss(UUID viewerId, UUID candidateId, Instant until);

    void deleteScore(UUID viewerId, UUID candidateId);
}
