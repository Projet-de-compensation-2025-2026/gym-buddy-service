package fr.projetcompensation.gymbuddy.suggestions;

import java.util.List;

public record SuggestionList(List<VisibleSuggestion> data, int size) {}
