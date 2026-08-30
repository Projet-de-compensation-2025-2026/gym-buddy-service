package fr.projetcompensation.gymbuddy.suggestions;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SuggestionService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final Duration FRESHNESS = Duration.ofHours(48);
    static final Duration DISMISS_FOR = Duration.ofDays(30);
    static final Duration CO_PARTICIPANT_WINDOW = Duration.ofDays(90);

    private final SuggestionGraph graph;
    private final SuggestionStore store;
    private final SuggestionScorer scorer;
    private final Clock clock;

    public SuggestionService(SuggestionGraph graph, SuggestionStore store, SuggestionWeights weights, Clock clock) {
        this.graph = graph;
        this.store = store;
        this.scorer = new SuggestionScorer(weights);
        this.clock = clock;
    }

    public SuggestionList list(UUID viewerId, Integer size) {
        MemberSnapshot viewer = requireActive(viewerId);
        int pageSize = pageSize(size);
        Instant now = clock.instant();
        List<ScoredCandidate> ranked = freshScores(viewer, now);
        List<UUID> ids = ranked.stream().map(ScoredCandidate::userId).toList();
        Map<UUID, MemberSnapshot> snapshots = index(graph.membersByIds(ids));
        Set<UUID> friends = graph.acceptedFriendIds(viewer.userId());
        List<VisibleSuggestion> cards = new ArrayList<>();
        for (ScoredCandidate scored : scorer.topK(ranked, pageSize)) {
            MemberSnapshot candidate = snapshots.get(scored.userId());
            if (candidate == null || !candidate.active()) {
                continue;
            }
            boolean stub = candidate.visibility() == ProfileVisibility.PRIVATE && !friends.contains(candidate.userId());
            List<String> shared = SportsOverlap.sharedInOrder(viewer.sports(), candidate.sports());
            cards.add(new VisibleSuggestion(
                    candidate,
                    new ScoredCandidate(
                            scored.userId(),
                            scored.score(),
                            scored.reason(),
                            scored.mutualFriends(),
                            shared,
                            scored.features()),
                    stub));
        }
        return new SuggestionList(List.copyOf(cards), pageSize);
    }

    public void dismiss(UUID viewerId, UUID candidateId) {
        MemberSnapshot viewer = requireActive(viewerId);
        if (candidateId == null || candidateId.equals(viewer.userId())) {
            throw AuthException.notFound("suggestion not found");
        }
        MemberSnapshot candidate = graph.membersByIds(List.of(candidateId)).stream()
                .findFirst()
                .orElseThrow(() -> AuthException.notFound("suggestion not found"));
        if (!candidate.active()) {
            throw AuthException.notFound("suggestion not found");
        }
        Instant until = clock.instant().plus(DISMISS_FOR);
        store.dismiss(viewer.userId(), candidateId, until);
        store.deleteScore(viewer.userId(), candidateId);
    }

    public void recomputeAll() {
        Instant now = clock.instant();
        for (MemberSnapshot member : graph.activeMembers()) {
            recompute(member, now);
        }
    }

    private List<ScoredCandidate> freshScores(MemberSnapshot viewer, Instant now) {
        Instant computedAt = store.scoresComputedAt(viewer.userId()).orElse(null);
        Instant graphChange = graph.latestRelationshipChange(viewer.userId());
        boolean stale = computedAt == null
                || computedAt.isBefore(now.minus(FRESHNESS))
                || (graphChange != null && graphChange.isAfter(computedAt));
        if (!stale) {
            return store.loadScores(viewer.userId());
        }
        return recompute(viewer, now);
    }

    private List<ScoredCandidate> recompute(MemberSnapshot viewer, Instant now) {
        Set<UUID> friends = graph.acceptedFriendIds(viewer.userId());
        Set<UUID> pending = graph.pendingIds(viewer.userId());
        Set<UUID> blocked = graph.blockedIds(viewer.userId());
        Set<UUID> dismissed = graph.dismissedIds(viewer.userId(), now);
        Set<UUID> forbidden = ForbiddenSet.of(viewer.userId(), friends, pending, blocked, dismissed);
        Set<UUID> neighborIds = new HashSet<>(friends);
        neighborIds.add(viewer.userId());
        Map<UUID, Set<UUID>> viewerNeighbors = graph.neighbors(neighborIds);
        Set<UUID> fof = CandidateGenerator.friendsOfFriends(viewer.userId(), viewerNeighbors);
        Set<UUID> citySport = graph.sameCityAndSport(viewer, SuggestionScorer.CANDIDATE_CAP);
        Set<UUID> co = graph.recentCoParticipants(viewer.userId(), now.minus(CO_PARTICIPANT_WINDOW));
        Set<UUID> candidateIds = CandidateGenerator.generate(viewer.userId(), fof, citySport, co, forbidden);
        List<MemberSnapshot> snapshots = graph.membersByIds(candidateIds).stream()
                .filter(member -> !ForbiddenSet.excluded(member.userId(), forbidden, member))
                .toList();
        Set<UUID> scoreIds = new HashSet<>();
        scoreIds.add(viewer.userId());
        scoreIds.addAll(friends);
        for (MemberSnapshot snapshot : snapshots) {
            scoreIds.add(snapshot.userId());
        }
        Map<UUID, Set<UUID>> neighbors = graph.neighbors(scoreIds);
        List<ScoredCandidate> ranked = scorer.score(viewer, snapshots, neighbors);
        store.replaceScores(viewer.userId(), now, ranked);
        return ranked;
    }

    private MemberSnapshot requireActive(UUID userId) {
        MemberSnapshot member = graph.requireMember(userId);
        if (!member.active()) {
            throw AuthException.unauthenticated("missing or invalid access token");
        }
        return member;
    }

    private static int pageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw AuthException.validation("size must be between 1 and 50", new FieldIssue("size", "range"));
        }
        return size;
    }

    private static Map<UUID, MemberSnapshot> index(List<MemberSnapshot> members) {
        Map<UUID, MemberSnapshot> map = new java.util.LinkedHashMap<>();
        for (MemberSnapshot member : members) {
            map.put(member.userId(), member);
        }
        return map;
    }
}
