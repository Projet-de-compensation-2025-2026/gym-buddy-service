package fr.projetcompensation.gymbuddy.suggestions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuggestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private InMemoryGraph graph;
    private InMemoryStore store;
    private SuggestionService service;
    private MemberSnapshot alex;
    private MemberSnapshot blake;
    private MemberSnapshot casey;
    private MemberSnapshot drew;
    private MemberSnapshot eddy;

    @BeforeEach
    void setUp() {
        graph = new InMemoryGraph();
        store = new InMemoryStore(graph);
        service = new SuggestionService(graph, store, SuggestionWeights.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));
        alex = publicMember("alex", List.of("running", "yoga"), "Porto");
        blake = publicMember("blake", List.of("running"), "Porto");
        casey = publicMember("casey", List.of("yoga"), "Lisbon");
        drew = publicMember("drew", List.of("running"), "Porto");
        eddy = publicMember("eddy", List.of("climbing"), "FarAway");
        graph.put(alex, blake, casey, drew, eddy);
        UUID zoe = publicMember("zoe", List.of("running"), "Porto").userId();
        UUID pat = publicMember("pat", List.of("running"), "Porto").userId();
        graph.friend(alex.userId(), zoe);
        graph.friend(blake.userId(), zoe);
        graph.friend(alex.userId(), pat);
        graph.friend(blake.userId(), pat);
    }

    @Test
    void fsSugg01_defaultSizeTwentyMaxFifty() {
        SuggestionList page = service.list(alex.userId(), null);
        assertThat(page.size()).isEqualTo(20);
        assertThatThrownBy(() -> service.list(alex.userId(), 51))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsSuggAcceptance_twoMutualFriendsAndSameSportRanksAboveUnrelated() {
        SuggestionList page = service.list(alex.userId(), 20);
        List<UUID> ids =
                page.data().stream().map(card -> card.candidate().userId()).toList();
        assertThat(ids).contains(blake.userId(), drew.userId());
        assertThat(ids).doesNotContain(eddy.userId());
        assertThat(ids.indexOf(blake.userId())).isLessThan(ids.indexOf(drew.userId()));
        VisibleSuggestion blakeCard = page.data().stream()
                .filter(card -> card.candidate().userId().equals(blake.userId()))
                .findFirst()
                .orElseThrow();
        assertThat(blakeCard.scored().mutualFriends()).isEqualTo(2);
        assertThat(blakeCard.scored().reason()).contains("mutual friend");
        assertThat(blakeCard.scored().sharedSports()).contains("running");
    }

    @Test
    void fsSugg02_forbiddenSetFiltersSelfFriendsPendingBlockedLocked() {
        graph.friend(alex.userId(), casey.userId());
        graph.pending(alex.userId(), drew.userId());
        MemberSnapshot locked = graph.put(new MemberSnapshot(
                UUID.randomUUID(),
                "locked",
                "locked",
                UserStatus.LOCKED,
                ProfileVisibility.PUBLIC,
                List.of("running"),
                "Porto",
                null,
                null,
                List.of(),
                ExperienceLevel.INTERMEDIATE,
                null,
                NOW));
        graph.friend(
                alex.userId(), publicMember("hub", List.of("running"), "Porto").userId());
        graph.block(alex.userId(), eddy.userId());

        SuggestionList page = service.list(alex.userId(), 50);
        Set<UUID> ids = new HashSet<>();
        page.data().forEach(card -> ids.add(card.candidate().userId()));
        assertThat(ids).doesNotContain(alex.userId(), casey.userId(), drew.userId(), eddy.userId(), locked.userId());
    }

    @Test
    void fsSugg04_dismissedCandidateAbsentForThirtyDays() {
        service.list(alex.userId(), 20);
        service.dismiss(alex.userId(), blake.userId());
        SuggestionList page = service.list(alex.userId(), 20);
        assertThat(page.data()).noneMatch(card -> card.candidate().userId().equals(blake.userId()));
        assertThatThrownBy(() -> service.dismiss(alex.userId(), alex.userId()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsSugg06_privateStrangerIsStub() {
        MemberSnapshot privateBlake = graph.put(new MemberSnapshot(
                blake.userId(),
                "blake",
                "Blake Rivera",
                UserStatus.ACTIVE,
                ProfileVisibility.PRIVATE,
                List.of("running"),
                "Porto",
                null,
                null,
                List.of(),
                ExperienceLevel.INTERMEDIATE,
                null,
                NOW));
        SuggestionList page = service.list(alex.userId(), 20);
        VisibleSuggestion card = page.data().stream()
                .filter(row -> row.candidate().userId().equals(privateBlake.userId()))
                .findFirst()
                .orElseThrow();
        assertThat(card.stub()).isTrue();
        assertThat(card.displayName()).isEqualTo("Blake R.");
        assertThat(card.city()).isNull();
        assertThat(card.scored().mutualFriends()).isGreaterThan(0);
    }

    private MemberSnapshot publicMember(String handle, List<String> sports, String city) {
        MemberSnapshot snapshot = new MemberSnapshot(
                UUID.nameUUIDFromBytes(handle.getBytes()),
                handle,
                Character.toUpperCase(handle.charAt(0)) + handle.substring(1) + " Test",
                UserStatus.ACTIVE,
                ProfileVisibility.PUBLIC,
                sports,
                city,
                null,
                null,
                List.of(new PreferredWindow(1, "07:00", "08:00")),
                ExperienceLevel.INTERMEDIATE,
                null,
                NOW);
        graph.put(snapshot);
        return snapshot;
    }

    private static final class InMemoryGraph implements SuggestionGraph {
        private final Map<UUID, MemberSnapshot> members = new LinkedHashMap<>();
        private final Map<UUID, Set<UUID>> friends = new HashMap<>();
        private final Map<UUID, Set<UUID>> pending = new HashMap<>();
        private final Map<UUID, Set<UUID>> blocked = new HashMap<>();
        private final Map<UUID, Instant> dismissedUntil = new HashMap<>();
        private Instant graphTime = NOW;

        MemberSnapshot put(MemberSnapshot... rows) {
            MemberSnapshot last = null;
            for (MemberSnapshot row : rows) {
                members.put(row.userId(), row);
                last = row;
            }
            return last;
        }

        void friend(UUID a, UUID b) {
            friends.computeIfAbsent(a, key -> new HashSet<>()).add(b);
            friends.computeIfAbsent(b, key -> new HashSet<>()).add(a);
            graphTime = graphTime.plusSeconds(1);
        }

        void pending(UUID a, UUID b) {
            pending.computeIfAbsent(a, key -> new HashSet<>()).add(b);
            pending.computeIfAbsent(b, key -> new HashSet<>()).add(a);
            graphTime = graphTime.plusSeconds(1);
        }

        void block(UUID a, UUID b) {
            blocked.computeIfAbsent(a, key -> new HashSet<>()).add(b);
            blocked.computeIfAbsent(b, key -> new HashSet<>()).add(a);
            graphTime = graphTime.plusSeconds(1);
        }

        @Override
        public MemberSnapshot requireMember(UUID userId) {
            MemberSnapshot member = members.get(userId);
            if (member == null) {
                throw AuthException.unauthenticated("missing or invalid access token");
            }
            return member;
        }

        @Override
        public Set<UUID> acceptedFriendIds(UUID userId) {
            return Set.copyOf(friends.getOrDefault(userId, Set.of()));
        }

        @Override
        public Set<UUID> pendingIds(UUID userId) {
            return Set.copyOf(pending.getOrDefault(userId, Set.of()));
        }

        @Override
        public Set<UUID> blockedIds(UUID userId) {
            return Set.copyOf(blocked.getOrDefault(userId, Set.of()));
        }

        void dismiss(UUID candidateId, Instant until) {
            dismissedUntil.put(candidateId, until);
            graphTime = graphTime.plusSeconds(1);
        }

        @Override
        public Set<UUID> dismissedIds(UUID viewerId, Instant now) {
            Set<UUID> ids = new HashSet<>();
            dismissedUntil.forEach((id, until) -> {
                if (until.isAfter(now)) {
                    ids.add(id);
                }
            });
            return ids;
        }

        @Override
        public Instant latestRelationshipChange(UUID userId) {
            return graphTime;
        }

        @Override
        public Map<UUID, Set<UUID>> neighbors(Collection<UUID> userIds) {
            Map<UUID, Set<UUID>> map = new HashMap<>();
            for (UUID id : userIds) {
                map.put(id, new HashSet<>(friends.getOrDefault(id, Set.of())));
            }
            return map;
        }

        @Override
        public List<MemberSnapshot> membersByIds(Collection<UUID> ids) {
            List<MemberSnapshot> rows = new ArrayList<>();
            for (UUID id : ids) {
                MemberSnapshot member = members.get(id);
                if (member != null) {
                    rows.add(member);
                }
            }
            return rows;
        }

        @Override
        public Set<UUID> sameCityAndSport(MemberSnapshot viewer, int limit) {
            Set<UUID> ids = new HashSet<>();
            for (MemberSnapshot member : members.values()) {
                if (ids.size() >= limit) {
                    break;
                }
                if (member.userId().equals(viewer.userId()) || !member.active()) {
                    continue;
                }
                if (GeoScore.nearbyCandidate(
                                viewer.lat(), viewer.lng(), member.lat(), member.lng(), viewer.city(), member.city())
                        && SportsOverlap.sharesAny(viewer.sports(), member.sports())) {
                    ids.add(member.userId());
                }
            }
            return ids;
        }

        @Override
        public Set<UUID> recentCoParticipants(UUID userId, Instant since) {
            return Set.of();
        }

        @Override
        public List<MemberSnapshot> activeMembers() {
            return members.values().stream().filter(MemberSnapshot::active).toList();
        }
    }

    private static final class InMemoryStore implements SuggestionStore {
        private final InMemoryGraph graph;
        private final Map<UUID, List<ScoredCandidate>> scores = new HashMap<>();
        private final Map<UUID, Instant> computed = new HashMap<>();

        InMemoryStore(InMemoryGraph graph) {
            this.graph = graph;
        }

        @Override
        public void replaceScores(UUID viewerId, Instant computedAt, List<ScoredCandidate> ranked) {
            scores.put(viewerId, List.copyOf(ranked));
            computed.put(viewerId, computedAt);
        }

        @Override
        public Optional<Instant> scoresComputedAt(UUID viewerId) {
            return Optional.ofNullable(computed.get(viewerId));
        }

        @Override
        public List<ScoredCandidate> loadScores(UUID viewerId) {
            return scores.getOrDefault(viewerId, List.of());
        }

        @Override
        public void dismiss(UUID viewerId, UUID candidateId, Instant until) {
            graph.dismiss(candidateId, until);
        }

        @Override
        public void deleteScore(UUID viewerId, UUID candidateId) {
            List<ScoredCandidate> current = scores.get(viewerId);
            if (current == null) {
                return;
            }
            scores.put(
                    viewerId,
                    current.stream()
                            .filter(row -> !row.userId().equals(candidateId))
                            .toList());
        }
    }
}
