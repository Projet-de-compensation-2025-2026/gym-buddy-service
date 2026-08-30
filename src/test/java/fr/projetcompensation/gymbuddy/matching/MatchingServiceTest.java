package fr.projetcompensation.gymbuddy.matching;

import static org.assertj.core.api.Assertions.assertThat;

import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.suggestions.MemberSnapshot;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionGraph;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

class MatchingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    private InMemoryGraph graph;
    private InMemoryMatchingStore store;
    private MatchingService service;
    private MemberSnapshot alex;
    private MemberSnapshot blake;
    private MemberSnapshot casey;

    @BeforeEach
    void setUp() {
        graph = new InMemoryGraph();
        store = new InMemoryMatchingStore();
        service = new MatchingService(store, graph, Clock.fixed(NOW, ZoneOffset.UTC));
        alex = member("alex", List.of("running"), 1, "07:00", "09:00");
        blake = member("blake", List.of("running"), 1, "07:30", "09:30");
        casey = member("casey", List.of("yoga"), 3, "18:00", "20:00");
    }

    @Test
    void fsMatch01_optInAndOutAreIdempotent() {
        service.optIn(alex.userId());
        service.optIn(alex.userId());
        MatchingState me = service.me(alex.userId());
        assertThat(me.optedIn()).isTrue();
        assertThat(me.weekStart()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(me.pair()).isNull();
        service.optOut(alex.userId());
        service.optOut(alex.userId());
        assertThat(service.me(alex.userId()).optedIn()).isFalse();
    }

    @Test
    void fsMatch02And03_greedyJobAssignsUniquePairAndDraftEvent() {
        service.optIn(alex.userId());
        service.optIn(blake.userId());
        service.optIn(casey.userId());
        List<ProposedMatch> matches = service.assignCurrentWeek();
        assertThat(matches).hasSize(1);
        MatchingState alexMe = service.me(alex.userId());
        MatchingState caseyMe = service.me(casey.userId());
        assertThat(alexMe.pair().userId()).isEqualTo(blake.userId());
        assertThat(alexMe.match().durationMin()).isGreaterThanOrEqualTo(60);
        assertThat(alexMe.match().activity()).isEqualTo("running");
        assertThat(caseyMe.pair()).isNull();
        assertThat(service.assignCurrentWeek()).isEmpty();
    }

    private MemberSnapshot member(String handle, List<String> sports, int weekday, String start, String end) {
        MemberSnapshot snapshot = new MemberSnapshot(
                UUID.nameUUIDFromBytes(handle.getBytes()),
                handle,
                handle,
                UserStatus.ACTIVE,
                ProfileVisibility.PUBLIC,
                sports,
                "Porto",
                null,
                null,
                List.of(new PreferredWindow(weekday, start, end)),
                ExperienceLevel.INTERMEDIATE,
                null,
                NOW);
        graph.put(snapshot);
        return snapshot;
    }

    private static final class InMemoryGraph implements SuggestionGraph {
        private final Map<UUID, MemberSnapshot> members = new LinkedHashMap<>();

        void put(MemberSnapshot snapshot) {
            members.put(snapshot.userId(), snapshot);
        }

        @Override
        public MemberSnapshot requireMember(UUID userId) {
            MemberSnapshot member = members.get(userId);
            if (member == null) {
                throw new IllegalStateException("missing member");
            }
            return member;
        }

        @Override
        public Set<UUID> acceptedFriendIds(UUID userId) {
            return Set.of();
        }

        @Override
        public Set<UUID> pendingIds(UUID userId) {
            return Set.of();
        }

        @Override
        public Set<UUID> blockedIds(UUID userId) {
            return Set.of();
        }

        @Override
        public Set<UUID> dismissedIds(UUID viewerId, Instant now) {
            return Set.of();
        }

        @Override
        public Instant latestRelationshipChange(UUID userId) {
            return Instant.EPOCH;
        }

        @Override
        public Map<UUID, Set<UUID>> neighbors(Collection<UUID> userIds) {
            Map<UUID, Set<UUID>> map = new HashMap<>();
            for (UUID id : userIds) {
                map.put(id, new HashSet<>());
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
            return Set.of();
        }

        @Override
        public Set<UUID> recentCoParticipants(UUID userId, Instant since) {
            return Set.of();
        }

        @Override
        public List<MemberSnapshot> activeMembers() {
            return List.copyOf(members.values());
        }
    }

    private static final class InMemoryMatchingStore implements MatchingStore {
        private final Map<UUID, MatchingOptIn> optIns = new LinkedHashMap<>();
        private final List<ProposedMatch> pairs = new ArrayList<>();
        private LocalDate pairWeek;

        @Override
        public void optIn(UUID userId, LocalDate weekStart, Instant at) {
            optIns.putIfAbsent(userId, new MatchingOptIn(userId, at));
        }

        @Override
        public void optOut(UUID userId, LocalDate weekStart) {
            optIns.remove(userId);
        }

        @Override
        public boolean optedIn(UUID userId, LocalDate weekStart) {
            return optIns.containsKey(userId);
        }

        @Override
        public List<MatchingOptIn> listOptIns(LocalDate weekStart) {
            return List.copyOf(optIns.values());
        }

        @Override
        public void replacePairs(LocalDate weekStart, List<ProposedMatch> matches) {
            pairWeek = weekStart;
            pairs.clear();
            pairs.addAll(matches);
        }

        @Override
        public Optional<ProposedMatch> pairFor(UUID userId, LocalDate weekStart) {
            return pairs.stream()
                    .filter(match ->
                            match.userA().equals(userId) || match.userB().equals(userId))
                    .findFirst();
        }

        @Override
        public boolean hasPairs(LocalDate weekStart) {
            return pairWeek != null && pairWeek.equals(weekStart) && !pairs.isEmpty();
        }
    }
}
