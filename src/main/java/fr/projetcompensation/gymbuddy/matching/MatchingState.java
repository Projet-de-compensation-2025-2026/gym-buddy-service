package fr.projetcompensation.gymbuddy.matching;

import fr.projetcompensation.gymbuddy.suggestions.MemberSnapshot;
import java.time.LocalDate;

public record MatchingState(boolean optedIn, LocalDate weekStart, MemberSnapshot pair, ProposedMatch match) {}
