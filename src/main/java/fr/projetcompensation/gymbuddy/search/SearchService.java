package fr.projetcompensation.gymbuddy.search;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.FriendshipStatus;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SearchService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int GRAPH_PAGE = 10_000;

    private final SearchCatalog catalog;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final Clock clock;

    public SearchService(
            SearchCatalog catalog,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            Clock clock) {
        this.catalog = catalog;
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    public PeopleSearchList searchPeople(
            UUID callerId,
            String q,
            List<String> sports,
            String experience,
            String city,
            Integer radiusKm,
            String friendState,
            String sort,
            Boolean debug,
            String before,
            Integer size) {
        User caller = requireActive(callerId);
        SearchSort parsedSort = peopleSort(sort);
        FriendStateFilter parsedFriends = friendState(friendState);
        Integer radius = radiusKm(radiusKm);
        ExperienceLevel level = experience(experience);
        int pageSize = pageSize(size);
        Profile viewerProfile =
                profiles.findByUserId(caller.id()).orElse(Profile.created(caller.id(), caller.handle()));
        Set<UUID> friendIds = acceptedFriendIds(caller.id());
        Set<UUID> fofIds = friendOfFriendIds(caller.id(), friendIds);
        List<String> terms = SearchText.tokens(q);
        List<String> sportFilter = sports == null ? List.of() : sports;
        Instant now = clock.instant();
        List<PeopleSearchHit> hits = new ArrayList<>();
        for (PersonCandidate candidate : catalog.people()) {
            Optional<PeopleSearchHit> hit = peopleHit(
                    caller,
                    viewerProfile,
                    candidate,
                    terms,
                    sportFilter,
                    level,
                    city,
                    radius,
                    parsedFriends,
                    friendIds,
                    fofIds,
                    Boolean.TRUE.equals(debug),
                    now);
            hit.ifPresent(hits::add);
        }
        sortPeople(hits, parsedSort);
        return pagePeople(hits, parsedSort, before, pageSize);
    }

    public EventSearchList searchEvents(
            UUID callerId,
            String q,
            String activity,
            Instant from,
            Instant to,
            Boolean remaining,
            Integer radiusKm,
            Boolean organizerIsFriend,
            String sort,
            Boolean debug,
            String before,
            Integer size) {
        User caller = requireActive(callerId);
        SearchSort parsedSort = eventSort(sort);
        Integer radius = radiusKm(radiusKm);
        int pageSize = pageSize(size);
        Profile viewerProfile =
                profiles.findByUserId(caller.id()).orElse(Profile.created(caller.id(), caller.handle()));
        Set<UUID> friendIds = acceptedFriendIds(caller.id());
        List<String> terms = SearchText.tokens(q);
        Instant now = clock.instant();
        List<EventSearchHit> hits = new ArrayList<>();
        for (EventCandidate candidate : catalog.events()) {
            Optional<EventSearchHit> hit = eventHit(
                    caller,
                    viewerProfile,
                    candidate,
                    terms,
                    activity,
                    from,
                    to,
                    Boolean.TRUE.equals(remaining),
                    radius,
                    Boolean.TRUE.equals(organizerIsFriend),
                    friendIds,
                    Boolean.TRUE.equals(debug),
                    now);
            hit.ifPresent(hits::add);
        }
        sortEvents(hits, parsedSort);
        return pageEvents(hits, parsedSort, before, pageSize);
    }

    private Optional<PeopleSearchHit> peopleHit(
            User caller,
            Profile viewerProfile,
            PersonCandidate candidate,
            List<String> terms,
            List<String> sports,
            ExperienceLevel experience,
            String city,
            Integer radiusKm,
            FriendStateFilter friendState,
            Set<UUID> friendIds,
            Set<UUID> fofIds,
            boolean debug,
            Instant now) {
        User owner = candidate.user();
        Profile profile = candidate.profile();
        if (owner == null || profile == null || !owner.active()) {
            return Optional.empty();
        }
        if (owner.id().equals(caller.id())) {
            return Optional.empty();
        }
        if (friendships.isBlockedEitherWay(caller.id(), owner.id())) {
            return Optional.empty();
        }
        boolean friend = friendIds.contains(owner.id());
        if (profile.visibility() == ProfileVisibility.PRIVATE && !friend && !caller.isStaff()) {
            return Optional.empty();
        }
        if (friendState == FriendStateFilter.NOT_FRIENDS && friend) {
            return Optional.empty();
        }
        if (!sports.isEmpty() && !anySport(profile.sports(), sports)) {
            return Optional.empty();
        }
        if (experience != null && profile.experienceLevel() != experience) {
            return Optional.empty();
        }
        if (city != null && !city.isBlank() && !cityMatches(profile.city(), city)) {
            return Optional.empty();
        }
        Double distanceKm = Geo.km(viewerProfile.lat(), viewerProfile.lng(), profile.lat(), profile.lng());
        if (radiusKm != null && (distanceKm == null || distanceKm > radiusKm)) {
            return Optional.empty();
        }
        String aFields = SearchText.join(profile.displayName(), owner.handle());
        String bFields = SearchText.join(profile.bio(), String.join(" ", profile.sports()));
        String cFields = SearchText.join(profile.city());
        if (!SearchText.matches(terms, aFields, bFields, cFields)) {
            return Optional.empty();
        }
        double tsRank = SearchText.tsRank(terms, aFields, bFields, cFields);
        double recency = SearchRank.peopleRecency(candidate.recencyAt(), now);
        double geo = SearchRank.geo(distanceKm);
        double social = SearchRank.peopleSocial(friend, fofIds.contains(owner.id()));
        double rank = SearchRank.composite(tsRank, recency, geo, social);
        String reason = debug ? peopleReason(terms, sports, city, radiusKm, distanceKm, friend) : null;
        return Optional.of(new PeopleSearchHit(
                owner, profile, distanceKm, friendStateOf(caller.id(), owner.id(), friend), rank, reason));
    }

    private Optional<EventSearchHit> eventHit(
            User caller,
            Profile viewerProfile,
            EventCandidate event,
            List<String> terms,
            String activity,
            Instant from,
            Instant to,
            boolean remainingOnly,
            Integer radiusKm,
            boolean organizerIsFriendOnly,
            Set<UUID> friendIds,
            boolean debug,
            Instant now) {
        if (event.hidden() || event.cancelled()) {
            return Optional.empty();
        }
        User organizer = event.organizer();
        if (organizer == null || !organizer.active()) {
            return Optional.empty();
        }
        if (!organizer.id().equals(caller.id()) && friendships.isBlockedEitherWay(caller.id(), organizer.id())) {
            return Optional.empty();
        }
        if (!canViewEvent(event, caller, friendIds)) {
            return Optional.empty();
        }
        boolean organizerFriend = friendIds.contains(organizer.id());
        if (organizerIsFriendOnly && !organizerFriend) {
            return Optional.empty();
        }
        if (activity != null && !activity.isBlank() && !activity.equalsIgnoreCase(event.activity())) {
            return Optional.empty();
        }
        if (from != null && event.startsAt() != null && event.startsAt().isBefore(from)) {
            return Optional.empty();
        }
        if (to != null && event.startsAt() != null && event.startsAt().isAfter(to)) {
            return Optional.empty();
        }
        if (remainingOnly && event.remainingSeats() <= 0) {
            return Optional.empty();
        }
        Double distanceKm = Geo.km(viewerProfile.lat(), viewerProfile.lng(), event.lat(), event.lng());
        if (radiusKm != null && (distanceKm == null || distanceKm > radiusKm)) {
            return Optional.empty();
        }
        String aFields = SearchText.join(event.title(), event.activity());
        String bFields = SearchText.join(event.description());
        String cFields = SearchText.join(event.place());
        if (!SearchText.matches(terms, aFields, bFields, cFields)) {
            return Optional.empty();
        }
        double tsRank = SearchText.tsRank(terms, aFields, bFields, cFields);
        double recency = SearchRank.eventRecency(event.startsAt(), now);
        double geo = SearchRank.geo(distanceKm);
        double social = SearchRank.eventSocial(organizerFriend);
        double rank = SearchRank.composite(tsRank, recency, geo, social);
        String reason =
                debug ? eventReason(terms, activity, remainingOnly, radiusKm, distanceKm, organizerFriend) : null;
        return Optional.of(new EventSearchHit(
                event.id(),
                event.title(),
                event.activity(),
                event.place(),
                event.startsAt(),
                event.remainingSeats(),
                event.capacity(),
                organizer,
                event.organizerProfile(),
                distanceKm,
                rank,
                reason));
    }

    private boolean canViewEvent(EventCandidate event, User viewer, Set<UUID> friendIds) {
        if (event.organizer().id().equals(viewer.id())) {
            return true;
        }
        if (event.acceptedApplicantIds().contains(viewer.id())) {
            return true;
        }
        return switch (event.visibility()) {
            case PUBLIC -> true;
            case FRIENDS -> friendIds.contains(event.organizer().id());
            case PRIVATE -> event.inviteeIds().contains(viewer.id());
        };
    }

    private static void sortPeople(List<PeopleSearchHit> hits, SearchSort sort) {
        Comparator<PeopleSearchHit> byId =
                Comparator.comparing((PeopleSearchHit hit) -> hit.user().id()).reversed();
        if (sort == SearchSort.DISTANCE) {
            hits.sort(Comparator.comparing((PeopleSearchHit hit) ->
                            hit.distanceKm() == null ? Double.POSITIVE_INFINITY : hit.distanceKm())
                    .thenComparing(byId));
            return;
        }
        hits.sort(Comparator.comparingDouble(PeopleSearchHit::rank).reversed().thenComparing(byId));
    }

    private static void sortEvents(List<EventSearchHit> hits, SearchSort sort) {
        Comparator<EventSearchHit> byId =
                Comparator.comparing(EventSearchHit::id).reversed();
        if (sort == SearchSort.DISTANCE) {
            hits.sort(Comparator.comparing((EventSearchHit hit) ->
                            hit.distanceKm() == null ? Double.POSITIVE_INFINITY : hit.distanceKm())
                    .thenComparing(byId));
            return;
        }
        if (sort == SearchSort.STARTS_AT) {
            hits.sort(
                    Comparator.comparing((EventSearchHit hit) -> hit.startsAt() == null ? Instant.MAX : hit.startsAt())
                            .thenComparing(byId));
            return;
        }
        hits.sort(Comparator.comparingDouble(EventSearchHit::rank).reversed().thenComparing(byId));
    }

    private static PeopleSearchList pagePeople(
            List<PeopleSearchHit> hits, SearchSort sort, String before, int pageSize) {
        List<PeopleSearchHit> sliced = slicePeople(hits, sort, before);
        String next = null;
        if (sliced.size() > pageSize) {
            PeopleSearchHit last = sliced.get(pageSize - 1);
            next = peopleCursor(sort, last);
            sliced = sliced.subList(0, pageSize);
        }
        return new PeopleSearchList(List.copyOf(sliced), next, pageSize);
    }

    private static EventSearchList pageEvents(List<EventSearchHit> hits, SearchSort sort, String before, int pageSize) {
        List<EventSearchHit> sliced = sliceEvents(hits, sort, before);
        String next = null;
        if (sliced.size() > pageSize) {
            EventSearchHit last = sliced.get(pageSize - 1);
            next = eventCursor(sort, last);
            sliced = sliced.subList(0, pageSize);
        }
        return new EventSearchList(List.copyOf(sliced), next, pageSize);
    }

    private static List<PeopleSearchHit> slicePeople(List<PeopleSearchHit> hits, SearchSort sort, String before) {
        if (before == null || before.isBlank()) {
            return hits;
        }
        if (sort == SearchSort.DISTANCE) {
            RankIdCursor cursor = RankIdCursor.parse(before).orElse(null);
            if (cursor == null) {
                return hits;
            }
            List<PeopleSearchHit> out = new ArrayList<>();
            for (PeopleSearchHit hit : hits) {
                double key = hit.distanceKm() == null ? Double.POSITIVE_INFINITY : hit.distanceKm();
                if (Double.compare(key, cursor.rank()) < 0
                        || (Double.compare(key, cursor.rank()) == 0
                                && hit.user().id().compareTo(cursor.id()) >= 0)) {
                    continue;
                }
                out.add(hit);
            }
            return out;
        }
        RankIdCursor cursor = RankIdCursor.parse(before).orElse(null);
        if (cursor == null) {
            return hits;
        }
        List<PeopleSearchHit> out = new ArrayList<>();
        for (PeopleSearchHit hit : hits) {
            if (!cursor.after(hit.rank(), hit.user().id())) {
                continue;
            }
            out.add(hit);
        }
        return out;
    }

    private static List<EventSearchHit> sliceEvents(List<EventSearchHit> hits, SearchSort sort, String before) {
        if (before == null || before.isBlank()) {
            return hits;
        }
        if (sort == SearchSort.STARTS_AT) {
            InstantIdCursor cursor = InstantIdCursor.parse(before).orElse(null);
            if (cursor == null) {
                return hits;
            }
            List<EventSearchHit> out = new ArrayList<>();
            for (EventSearchHit hit : hits) {
                Instant at = hit.startsAt() == null ? Instant.MAX : hit.startsAt();
                if (at.isBefore(cursor.at())
                        || (at.equals(cursor.at()) && hit.id().compareTo(cursor.id()) >= 0)) {
                    continue;
                }
                out.add(hit);
            }
            return out;
        }
        RankIdCursor cursor = RankIdCursor.parse(before).orElse(null);
        if (cursor == null) {
            return hits;
        }
        if (sort == SearchSort.DISTANCE) {
            List<EventSearchHit> out = new ArrayList<>();
            for (EventSearchHit hit : hits) {
                double key = hit.distanceKm() == null ? Double.POSITIVE_INFINITY : hit.distanceKm();
                if (Double.compare(key, cursor.rank()) < 0
                        || (Double.compare(key, cursor.rank()) == 0 && hit.id().compareTo(cursor.id()) >= 0)) {
                    continue;
                }
                out.add(hit);
            }
            return out;
        }
        List<EventSearchHit> out = new ArrayList<>();
        for (EventSearchHit hit : hits) {
            if (!cursor.after(hit.rank(), hit.id())) {
                continue;
            }
            out.add(hit);
        }
        return out;
    }

    private static String peopleCursor(SearchSort sort, PeopleSearchHit last) {
        if (sort == SearchSort.DISTANCE) {
            double key = last.distanceKm() == null ? Double.POSITIVE_INFINITY : last.distanceKm();
            return new RankIdCursor(key, last.user().id()).encode();
        }
        return new RankIdCursor(last.rank(), last.user().id()).encode();
    }

    private static String eventCursor(SearchSort sort, EventSearchHit last) {
        if (sort == SearchSort.STARTS_AT) {
            Instant at = last.startsAt() == null ? Instant.MAX : last.startsAt();
            return new InstantIdCursor(at, last.id()).encode();
        }
        if (sort == SearchSort.DISTANCE) {
            double key = last.distanceKm() == null ? Double.POSITIVE_INFINITY : last.distanceKm();
            return new RankIdCursor(key, last.id()).encode();
        }
        return new RankIdCursor(last.rank(), last.id()).encode();
    }

    private Set<UUID> acceptedFriendIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        for (Friendship row : friendships.listAccepted(userId, null, GRAPH_PAGE)) {
            ids.add(row.other(userId));
        }
        return ids;
    }

    private Set<UUID> friendOfFriendIds(UUID userId, Set<UUID> friendIds) {
        Set<UUID> ids = new HashSet<>();
        for (UUID friend : friendIds) {
            for (Friendship row : friendships.listAccepted(friend, null, GRAPH_PAGE)) {
                UUID other = row.other(friend);
                if (!other.equals(userId) && !friendIds.contains(other)) {
                    ids.add(other);
                }
            }
        }
        return ids;
    }

    private HitFriendState friendStateOf(UUID callerId, UUID otherId, boolean friend) {
        if (friend) {
            return HitFriendState.FRIENDS;
        }
        Optional<Friendship> pair = friendships.findPair(callerId, otherId);
        if (pair.isPresent() && pair.get().status() == FriendshipStatus.PENDING) {
            return HitFriendState.PENDING;
        }
        return HitFriendState.NONE;
    }

    private static boolean anySport(List<String> have, List<String> want) {
        for (String needed : want) {
            if (needed == null || needed.isBlank()) {
                continue;
            }
            for (String sport : have) {
                if (needed.equalsIgnoreCase(sport)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean cityMatches(String city, String filter) {
        if (city == null || city.isBlank()) {
            return false;
        }
        return city.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private static String peopleReason(
            List<String> terms, List<String> sports, String city, Integer radiusKm, Double distanceKm, boolean friend) {
        List<String> parts = new ArrayList<>();
        if (!terms.isEmpty()) {
            parts.add("text");
        }
        if (!sports.isEmpty()) {
            parts.add("sports");
        }
        if (city != null && !city.isBlank()) {
            parts.add("city");
        }
        if (radiusKm != null && distanceKm != null) {
            parts.add("geo");
        }
        if (friend) {
            parts.add("social");
        }
        return String.join(",", parts);
    }

    private static String eventReason(
            List<String> terms,
            String activity,
            boolean remainingOnly,
            Integer radiusKm,
            Double distanceKm,
            boolean organizerFriend) {
        List<String> parts = new ArrayList<>();
        if (!terms.isEmpty()) {
            parts.add("text");
        }
        if (activity != null && !activity.isBlank()) {
            parts.add("activity");
        }
        if (remainingOnly) {
            parts.add("remaining");
        }
        if (radiusKm != null && distanceKm != null) {
            parts.add("geo");
        }
        if (organizerFriend) {
            parts.add("social");
        }
        return String.join(",", parts);
    }

    private static SearchSort peopleSort(String sort) {
        try {
            SearchSort parsed = SearchSort.fromWire(sort);
            if (parsed == SearchSort.STARTS_AT) {
                throw AuthException.validation("unknown sort", new FieldIssue("sort", "enum"));
            }
            return parsed;
        } catch (IllegalArgumentException ex) {
            throw AuthException.validation("unknown sort", new FieldIssue("sort", "enum"));
        }
    }

    private static SearchSort eventSort(String sort) {
        try {
            return SearchSort.fromWire(sort);
        } catch (IllegalArgumentException ex) {
            throw AuthException.validation("unknown sort", new FieldIssue("sort", "enum"));
        }
    }

    private static FriendStateFilter friendState(String value) {
        try {
            return FriendStateFilter.fromWire(value);
        } catch (IllegalArgumentException ex) {
            throw AuthException.validation("unknown friendState", new FieldIssue("friendState", "enum"));
        }
    }

    private static ExperienceLevel experience(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ExperienceLevel.fromWire(value);
        } catch (IllegalArgumentException ex) {
            throw AuthException.validation("unknown experience", new FieldIssue("experience", "enum"));
        }
    }

    private static Integer radiusKm(Integer radiusKm) {
        if (radiusKm == null) {
            return null;
        }
        if (radiusKm < 1 || radiusKm > 50) {
            throw AuthException.validation("radiusKm must be between 1 and 50", new FieldIssue("radiusKm", "range"));
        }
        return radiusKm;
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

    private User requireActive(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> AuthException.unauthenticated("missing or invalid access token"));
        if (!user.active()) {
            throw AuthException.unauthenticated("missing or invalid access token");
        }
        return user;
    }
}
