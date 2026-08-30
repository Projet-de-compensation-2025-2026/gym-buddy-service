package fr.projetcompensation.gymbuddy.suggestions;

/**
 * Constants from {@code 50-Algorithms/01-Friend-suggestions.md}. Sum is 1 so a
 * defense can show a sensitivity argument without retraining.
 */
public record SuggestionWeights(double mutual, double jaccard, double geo, double time, double experience) {

    public static SuggestionWeights defaults() {
        return new SuggestionWeights(0.35, 0.25, 0.15, 0.15, 0.10);
    }
}
