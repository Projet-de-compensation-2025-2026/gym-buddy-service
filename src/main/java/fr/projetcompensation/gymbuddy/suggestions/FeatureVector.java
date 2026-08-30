package fr.projetcompensation.gymbuddy.suggestions;

public record FeatureVector(double mutual, double jaccard, double geo, double time, double experience) {

    public double score(SuggestionWeights weights) {
        return weights.mutual() * mutual
                + weights.jaccard() * jaccard
                + weights.geo() * geo
                + weights.time() * time
                + weights.experience() * experience;
    }

    public double weightedMutual(SuggestionWeights weights) {
        return weights.mutual() * mutual;
    }

    public double weightedJaccard(SuggestionWeights weights) {
        return weights.jaccard() * jaccard;
    }

    public double weightedGeo(SuggestionWeights weights) {
        return weights.geo() * geo;
    }

    public double weightedTime(SuggestionWeights weights) {
        return weights.time() * time;
    }

    public double weightedExperience(SuggestionWeights weights) {
        return weights.experience() * experience;
    }
}
