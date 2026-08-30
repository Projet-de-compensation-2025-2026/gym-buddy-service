package fr.projetcompensation.gymbuddy.suggestions;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SuggestionScorerTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private final SuggestionWeights weights = SuggestionWeights.defaults();
    private final SuggestionScorer scorer = new SuggestionScorer(weights);

    @Test
    void fsSuggFeatureMath_adamicAdarJaccardGeoTimeExperience() {
        UUID viewer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID candidate = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID rare = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID hub = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        MemberSnapshot u =
                member(viewer, "alex", List.of("running", "yoga"), "Porto", 41.15, -8.61, ExperienceLevel.INTERMEDIATE);
        MemberSnapshot v =
                member(candidate, "blake", List.of("running"), "Porto", 41.15, -8.61, ExperienceLevel.INTERMEDIATE);
        Map<UUID, Set<UUID>> neighbors = new HashMap<>();
        neighbors.put(viewer, Set.of(rare, hub));
        neighbors.put(candidate, Set.of(rare, hub));
        neighbors.put(rare, Set.of(viewer, candidate));
        neighbors.put(hub, new HashSet<>(Set.of(viewer, candidate)));
        for (int i = 0; i < 20; i++) {
            neighbors.get(hub).add(UUID.randomUUID());
        }

        double rawRare = 1.0 / Math.log(1 + 2);
        double rawHub = 1.0 / Math.log(1 + neighbors.get(hub).size());
        assertThat(AdamicAdar.raw(Set.of(rare, hub), id -> neighbors.get(id).size()))
                .isEqualTo(rawRare + rawHub);

        List<ScoredCandidate> ranked = scorer.score(u, List.of(v), neighbors);
        assertThat(ranked).hasSize(1);
        FeatureVector features = ranked.getFirst().features();
        assertThat(features.mutual()).isEqualTo(1.0);
        assertThat(features.jaccard()).isEqualTo(0.5);
        assertThat(features.geo()).isEqualTo(1.0);
        assertThat(features.experience()).isEqualTo(1.0);
        assertThat(features.score(weights)).isGreaterThan(0.7);
    }

    @Test
    void fsSuggPrimaryReason_largestWeightedFeature() {
        FeatureVector mutualWins = new FeatureVector(1.0, 0.0, 0.0, 0.0, 0.0);
        assertThat(PrimaryReason.of(mutualWins, weights, 3, List.of("yoga"), "Porto"))
                .isEqualTo("3 mutual friends");
        assertThat(PrimaryReason.of(mutualWins, weights, 1, List.of(), null)).isEqualTo("1 mutual friend");

        FeatureVector sportWins = new FeatureVector(0.0, 1.0, 0.0, 0.0, 0.0);
        assertThat(PrimaryReason.of(sportWins, weights, 0, List.of("CrossFit"), null))
                .isEqualTo("CrossFit");

        FeatureVector timeWins = new FeatureVector(0.0, 0.0, 0.0, 1.0, 0.0);
        assertThat(PrimaryReason.of(timeWins, weights, 0, List.of(), null)).isEqualTo("same gym times");

        FeatureVector geoCity = new FeatureVector(0.0, 0.0, 1.0, 0.0, 0.0);
        assertThat(PrimaryReason.of(geoCity, weights, 0, List.of(), "Lisbon")).isEqualTo("Lisbon");
        assertThat(PrimaryReason.of(geoCity, weights, 0, List.of(), null)).isEqualTo("near you");

        FeatureVector exp = new FeatureVector(0.0, 0.0, 0.0, 0.0, 1.0);
        assertThat(PrimaryReason.of(exp, weights, 0, List.of(), null)).isEqualTo("similar experience");
    }

    @Test
    void fsSuggTopK_ordersByScoreThenId() {
        UUID viewer = UUID.randomUUID();
        UUID high = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID mid = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        UUID low = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
        MemberSnapshot u = member(viewer, "alex", List.of("running"), "Porto", null, null, ExperienceLevel.ADVANCED);
        MemberSnapshot a = member(high, "high", List.of("running"), "Porto", null, null, ExperienceLevel.ADVANCED);
        MemberSnapshot b = member(mid, "mid", List.of("running", "yoga"), "Porto", null, null, null);
        MemberSnapshot c = member(low, "low", List.of("yoga"), "Far", null, null, ExperienceLevel.BEGINNER);
        Map<UUID, Set<UUID>> neighbors = Map.of(viewer, Set.of(), high, Set.of(), mid, Set.of(), low, Set.of());

        List<ScoredCandidate> ranked = scorer.score(u, List.of(c, a, b), neighbors);
        List<ScoredCandidate> top2 = scorer.topK(ranked, 2);
        assertThat(top2).hasSize(2);
        assertThat(top2.getFirst().userId()).isEqualTo(high);
        assertThat(ranked.getLast().userId()).isEqualTo(low);
        assertThat(scorer.topK(ranked, 50)).hasSize(3);
    }

    @Test
    void fsSuggGeo_sameCityWithoutCoordsIsPointFour() {
        assertThat(GeoScore.feature(null, null, null, null, "Porto", "porto")).isEqualTo(0.4);
        assertThat(GeoScore.feature(null, null, null, null, "Porto", "Lisbon")).isEqualTo(0.0);
        double close = GeoScore.feature(41.15, -8.61, 41.16, -8.62, "Porto", "Porto");
        assertThat(close).isGreaterThan(0.9);
        assertThat(GeoScore.nearbyCandidate(41.15, -8.61, 41.16, -8.62, "Porto", "Gaia"))
                .isTrue();
    }

    @Test
    void fsSuggTime_hoursSharedOverTenCappedAtOne() {
        List<PreferredWindow> morning = List.of(new PreferredWindow(1, "07:00", "09:00"));
        List<PreferredWindow> overlap = List.of(new PreferredWindow(1, "08:00", "10:00"));
        assertThat(WindowOverlap.overlapMinutesPerWeek(morning, overlap)).isEqualTo(60);
        assertThat(WindowOverlap.timeFeature(morning, overlap)).isEqualTo(0.1);
        List<PreferredWindow> longDay = List.of(
                new PreferredWindow(1, "06:00", "18:00"),
                new PreferredWindow(2, "06:00", "18:00"),
                new PreferredWindow(3, "06:00", "18:00"));
        assertThat(WindowOverlap.timeFeature(longDay, longDay)).isEqualTo(1.0);
    }

    @Test
    void fsSuggExperience_equalAdjacentOrElse() {
        assertThat(ExperienceCloseness.feature(ExperienceLevel.INTERMEDIATE, ExperienceLevel.INTERMEDIATE))
                .isEqualTo(1.0);
        assertThat(ExperienceCloseness.feature(ExperienceLevel.BEGINNER, ExperienceLevel.INTERMEDIATE))
                .isEqualTo(0.5);
        assertThat(ExperienceCloseness.feature(ExperienceLevel.BEGINNER, ExperienceLevel.ADVANCED))
                .isEqualTo(0.0);
        assertThat(ExperienceCloseness.feature(null, ExperienceLevel.ADVANCED)).isEqualTo(0.0);
    }

    private static MemberSnapshot member(
            UUID id,
            String handle,
            List<String> sports,
            String city,
            Double lat,
            Double lng,
            ExperienceLevel experience) {
        return new MemberSnapshot(
                id,
                handle,
                handle,
                UserStatus.ACTIVE,
                ProfileVisibility.PUBLIC,
                sports,
                city,
                lat,
                lng,
                List.of(),
                experience,
                null,
                NOW);
    }
}
