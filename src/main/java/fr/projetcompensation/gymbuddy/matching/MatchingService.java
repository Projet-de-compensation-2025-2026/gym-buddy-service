package fr.projetcompensation.gymbuddy.matching;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.events.EventDraft;
import fr.projetcompensation.gymbuddy.events.EventService;
import fr.projetcompensation.gymbuddy.events.VisibleEvent;
import fr.projetcompensation.gymbuddy.suggestions.MemberSnapshot;
import fr.projetcompensation.gymbuddy.suggestions.SuggestionGraph;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MatchingService {

    private final MatchingStore store;
    private final SuggestionGraph graph;
    private final Clock clock;
    private final EventService events;

    public MatchingService(MatchingStore store, SuggestionGraph graph, Clock clock) {
        this(store, graph, clock, null);
    }

    public MatchingService(MatchingStore store, SuggestionGraph graph, Clock clock, EventService events) {
        this.store = store;
        this.graph = graph;
        this.clock = clock;
        this.events = events;
    }

    public void optIn(UUID userId) {
        requireActive(userId);
        LocalDate week = IsoWeek.mondayUtc(clock.instant());
        store.optIn(userId, week, clock.instant());
    }

    public void optOut(UUID userId) {
        requireActive(userId);
        store.optOut(userId, IsoWeek.mondayUtc(clock.instant()));
    }

    public MatchingState me(UUID userId) {
        requireActive(userId);
        LocalDate week = IsoWeek.mondayUtc(clock.instant());
        boolean opted = store.optedIn(userId, week);
        ProposedMatch match = store.pairFor(userId, week).orElse(null);
        MemberSnapshot pair = null;
        if (match != null) {
            UUID other = match.userA().equals(userId) ? match.userB() : match.userA();
            pair = graph.membersByIds(List.of(other)).stream().findFirst().orElse(null);
        }
        return new MatchingState(opted, week, pair, match);
    }

    public List<ProposedMatch> assignCurrentWeek() {
        Instant now = clock.instant();
        LocalDate week = IsoWeek.mondayUtc(now);
        if (store.hasPairs(week)) {
            return List.of();
        }
        List<MatchingOptIn> optIns = store.listOptIns(week);
        List<MatchingMember> members = new ArrayList<>();
        for (MatchingOptIn optIn : optIns) {
            MemberSnapshot snapshot = graph.membersByIds(List.of(optIn.userId())).stream()
                    .findFirst()
                    .orElse(null);
            if (snapshot == null || !snapshot.active()) {
                continue;
            }
            members.add(new MatchingMember(
                    snapshot.userId(),
                    optIn.createdAt(),
                    snapshot.sports(),
                    snapshot.city(),
                    snapshot.lat(),
                    snapshot.lng(),
                    snapshot.preferredWindows(),
                    snapshot.visibility(),
                    graph.acceptedFriendIds(snapshot.userId()),
                    graph.blockedIds(snapshot.userId())));
        }
        List<ProposedMatch> matches = MatchingAlgorithm.greedy(members, week);
        List<ProposedMatch> withEvents = new ArrayList<>();
        for (ProposedMatch match : matches) {
            withEvents.add(attachDraftEvent(match, members));
        }
        store.replacePairs(week, withEvents);
        return List.copyOf(withEvents);
    }

    private ProposedMatch attachDraftEvent(ProposedMatch match, List<MatchingMember> members) {
        Instant startsAt = match.startsAt();
        Instant now = clock.instant();
        while (!startsAt.isAfter(now)) {
            startsAt = startsAt.plus(Duration.ofDays(7));
        }
        ProposedMatch future = match.withStartsAt(startsAt);
        if (events == null) {
            return future;
        }
        MatchingMember organizer = members.stream()
                .filter(member -> member.userId().equals(future.left()))
                .findFirst()
                .orElse(null);
        String place = organizer != null
                        && organizer.city() != null
                        && !organizer.city().isBlank()
                ? organizer.city()
                : "Gym";
        try {
            VisibleEvent created = events.create(
                    future.left(),
                    new EventDraft(
                            "Weekly gym match",
                            "Proposed session from weekly matching. You still accept.",
                            future.activity(),
                            place,
                            organizer == null ? null : organizer.lat(),
                            organizer == null ? null : organizer.lng(),
                            startsAt,
                            future.durationMin(),
                            "friends",
                            1,
                            null,
                            List.of(),
                            null,
                            List.of()));
            return future.withEventId(created.event().id());
        } catch (RuntimeException ignored) {
            return future;
        }
    }

    private void requireActive(UUID userId) {
        MemberSnapshot member = graph.requireMember(userId);
        if (!member.active()) {
            throw AuthException.unauthenticated("missing or invalid access token");
        }
    }
}
