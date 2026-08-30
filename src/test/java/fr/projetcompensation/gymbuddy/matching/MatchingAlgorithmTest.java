package fr.projetcompensation.gymbuddy.matching;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MatchingAlgorithmTest {

    private static final Instant NOW = Instant.parse("2026-08-24T08:00:00Z");
    private static final LocalDate WEEK = LocalDate.of(2026, 8, 24);

    @Test
    void fsMatchGreedy_neverAssignsOnePersonTwice() {
        MatchingMember a = member("a", List.of("running"), windows(1, "07:00", "09:00"));
        MatchingMember b = member("b", List.of("running"), windows(1, "07:00", "09:00"));
        MatchingMember c = member("c", List.of("running"), windows(1, "07:00", "09:00"));
        List<ProposedMatch> matches = MatchingAlgorithm.greedy(List.of(a, b, c), WEEK);
        assertThat(matches).hasSize(1);
        Set<UUID> assigned = matches.stream()
                .flatMap(match -> java.util.stream.Stream.of(match.userA(), match.userB()))
                .collect(Collectors.toSet());
        assertThat(assigned).hasSize(2);
    }

    @Test
    void fsMatch_noEdgeAcrossABlock() {
        MatchingMember a = member("a", List.of("running"), windows(1, "07:00", "09:00"));
        MatchingMember b = blocked("b", a.userId(), List.of("running"), windows(1, "07:00", "09:00"));
        assertThat(MatchingAlgorithm.edge(a, b)).isEmpty();
        assertThat(MatchingAlgorithm.greedy(List.of(a, b), WEEK)).isEmpty();
    }

    @Test
    void fsMatch_emptyOverlapYieldsNoEdge() {
        MatchingMember a = member("a", List.of("running"), windows(1, "07:00", "08:00"));
        MatchingMember b = member("b", List.of("running"), windows(1, "18:00", "20:00"));
        assertThat(MatchingAlgorithm.edge(a, b)).isEmpty();
    }

    @Test
    void fsMatch_overlapUnderSixtyMinutesIsNotAnEdge() {
        MatchingMember a = member("a", List.of("yoga"), windows(2, "07:00", "07:45"));
        MatchingMember b = member("b", List.of("yoga"), windows(2, "07:00", "07:45"));
        assertThat(MatchingAlgorithm.edge(a, b)).isEmpty();
    }

    @Test
    void fsMatchTinyGraph_greedyEqualsOptimum() {
        MatchingMember a = member("a", List.of("running"), windows(1, "07:00", "09:00"));
        MatchingMember b = member("b", List.of("running"), windows(1, "07:00", "09:00"));
        List<ProposedMatch> matches = MatchingAlgorithm.greedy(List.of(a, b), WEEK);
        assertThat(matches).hasSize(1);
        ProposedMatch match = matches.getFirst();
        assertThat(Set.of(match.userA(), match.userB())).containsExactlyInAnyOrder(a.userId(), b.userId());
        assertThat(match.activity()).isEqualTo("running");
        assertThat(match.durationMin()).isEqualTo(120);
        assertThat(match.startsAt()).isEqualTo(Instant.parse("2026-08-24T08:00:00Z"));
    }

    @Test
    void fsMatch03_threeOptInsOnlyOnePairSharesSportAndWindow() {
        MatchingMember alex = member("alex", List.of("running"), windows(1, "07:00", "09:00"));
        MatchingMember blake = member("blake", List.of("running"), windows(1, "07:30", "09:30"));
        MatchingMember casey = member("casey", List.of("yoga"), windows(3, "18:00", "20:00"));
        List<ProposedMatch> matches = MatchingAlgorithm.greedy(List.of(alex, blake, casey), WEEK);
        assertThat(matches).hasSize(1);
        Set<UUID> pair = Set.of(matches.getFirst().userA(), matches.getFirst().userB());
        assertThat(pair).containsExactlyInAnyOrder(alex.userId(), blake.userId());
        assertThat(pair).doesNotContain(casey.userId());
    }

    @Test
    void fsMatch_privateStrangersAreNotMatched() {
        MatchingMember a = new MatchingMember(
                UUID.randomUUID(),
                NOW,
                List.of("running"),
                "Porto",
                null,
                null,
                windows(1, "07:00", "09:00"),
                ProfileVisibility.PRIVATE,
                Set.of(),
                Set.of());
        MatchingMember b = member("b", List.of("running"), windows(1, "07:00", "09:00"));
        assertThat(MatchingAlgorithm.edge(a, b)).isEmpty();
    }

    private static MatchingMember member(String handle, List<String> sports, List<PreferredWindow> windows) {
        return new MatchingMember(
                UUID.nameUUIDFromBytes(handle.getBytes()),
                NOW,
                sports,
                "Porto",
                null,
                null,
                windows,
                ProfileVisibility.PUBLIC,
                Set.of(),
                Set.of());
    }

    private static MatchingMember blocked(
            String handle, UUID blockedId, List<String> sports, List<PreferredWindow> windows) {
        return new MatchingMember(
                UUID.nameUUIDFromBytes(handle.getBytes()),
                NOW,
                sports,
                "Porto",
                null,
                null,
                windows,
                ProfileVisibility.PUBLIC,
                Set.of(),
                Set.of(blockedId));
    }

    private static List<PreferredWindow> windows(int weekday, String start, String end) {
        return List.of(new PreferredWindow(weekday, start, end));
    }
}
