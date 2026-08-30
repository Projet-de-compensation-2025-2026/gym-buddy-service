package fr.projetcompensation.gymbuddy.matching;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingStore {

    void optIn(UUID userId, LocalDate weekStart, Instant at);

    void optOut(UUID userId, LocalDate weekStart);

    boolean optedIn(UUID userId, LocalDate weekStart);

    List<MatchingOptIn> listOptIns(LocalDate weekStart);

    void replacePairs(LocalDate weekStart, List<ProposedMatch> matches);

    Optional<ProposedMatch> pairFor(UUID userId, LocalDate weekStart);

    boolean hasPairs(LocalDate weekStart);
}
