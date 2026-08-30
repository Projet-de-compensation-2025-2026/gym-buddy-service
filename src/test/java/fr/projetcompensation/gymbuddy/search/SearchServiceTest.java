package fr.projetcompensation.gymbuddy.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
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
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T16:00:00Z");
    private static final double LYON_LAT = 45.75;
    private static final double LYON_LNG = 4.85;

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriendships friendships;
    private InMemoryCatalog catalog;
    private SearchService search;
    private User alex;
    private User sarah;
    private User privateStranger;
    private User privateFriend;
    private User blocked;
    private User parisRunner;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friendships = new InMemoryFriendships();
        catalog = new InMemoryCatalog();
        search = new SearchService(catalog, friendships, users, profiles, Clock.fixed(NOW, ZoneOffset.UTC));
        alex = member("alex");
        profile(
                alex,
                "Alex",
                ProfileVisibility.PUBLIC,
                List.of("weightlifting"),
                ExperienceLevel.INTERMEDIATE,
                "Lyon",
                LYON_LAT,
                LYON_LNG);
        sarah = member("sarahj");
        profile(
                sarah,
                "Sarah J.",
                ProfileVisibility.PUBLIC,
                List.of("weightlifting"),
                ExperienceLevel.INTERMEDIATE,
                "Lyon",
                45.76,
                4.86);
        privateStranger = member("hidden");
        profile(
                privateStranger,
                "Hidden Lifter",
                ProfileVisibility.PRIVATE,
                List.of("weightlifting"),
                ExperienceLevel.ADVANCED,
                "Lyon",
                45.75,
                4.85);
        privateFriend = member("buddy");
        profile(
                privateFriend,
                "Buddy Lift",
                ProfileVisibility.PRIVATE,
                List.of("weightlifting"),
                ExperienceLevel.BEGINNER,
                "Lyon",
                45.74,
                4.84);
        blocked = member("blocked");
        profile(
                blocked,
                "Blocked Lifter",
                ProfileVisibility.PUBLIC,
                List.of("weightlifting"),
                ExperienceLevel.INTERMEDIATE,
                "Lyon",
                45.75,
                4.85);
        parisRunner = member("parisrun");
        profile(
                parisRunner,
                "Paris Runner",
                ProfileVisibility.PUBLIC,
                List.of("running"),
                ExperienceLevel.ADVANCED,
                "Paris",
                48.86,
                2.35);
        friendships.accept(alex.id(), privateFriend.id());
        friendships.block(alex.id(), blocked.id());
    }

    @Test
    void fsSrch03_publicLyonWeightliftingReturnedAndPrivateStrangersAreNot() {
        PeopleSearchList page = search.searchPeople(
                alex.id(), null, List.of("weightlifting"), null, "Lyon", null, "any", "relevance", true, null, 20);

        assertThat(page.data())
                .extracting(hit -> hit.user().handle())
                .contains("sarahj", "buddy")
                .doesNotContain("hidden", "alex", "blocked", "parisrun");
        assertThat(page.data().stream()
                        .anyMatch(hit ->
                                hit.matchReason() != null && hit.matchReason().contains("sports")))
                .isTrue();
    }

    @Test
    void fsSrch06_privateStrangersNeverAppear() {
        PeopleSearchList page = search.searchPeople(
                alex.id(), "Hidden", List.of(), null, null, null, "any", "relevance", false, null, 20);

        assertThat(page.data()).extracting(hit -> hit.user().handle()).doesNotContain("hidden");
    }

    @Test
    void fsSrch06_blockedNeverAppear() {
        PeopleSearchList page = search.searchPeople(
                alex.id(), "Blocked", List.of(), null, null, null, "any", "relevance", false, null, 20);

        assertThat(page.data()).extracting(hit -> hit.user().handle()).doesNotContain("blocked");
    }

    @Test
    void fsSrch03_notFriendsExcludesAcceptedFriends() {
        PeopleSearchList page = search.searchPeople(
                alex.id(),
                null,
                List.of("weightlifting"),
                null,
                "Lyon",
                null,
                "not-friends",
                "relevance",
                false,
                null,
                20);

        assertThat(page.data())
                .extracting(hit -> hit.user().handle())
                .contains("sarahj")
                .doesNotContain("buddy");
    }

    @Test
    void fsSrch04_fullEventsOmittedWhenRemainingTrue() {
        EventCandidate open =
                event("Weekend HIIT", "hiit", "Lyon", 4, SearchEventVisibility.PUBLIC, alex, NOW.plusSeconds(3600));
        EventCandidate full =
                event("Packed Hall", "hiit", "Lyon", 0, SearchEventVisibility.PUBLIC, sarah, NOW.plusSeconds(7200));
        catalog.events.add(open);
        catalog.events.add(full);

        EventSearchList filtered = search.searchEvents(
                alex.id(), null, "hiit", null, null, true, null, null, "relevance", false, null, 20);
        EventSearchList all = search.searchEvents(
                alex.id(), null, "hiit", null, null, false, null, null, "relevance", false, null, 20);

        assertThat(filtered.data()).extracting(EventSearchHit::title).containsExactly("Weekend HIIT");
        assertThat(all.data()).extracting(EventSearchHit::title).contains("Weekend HIIT", "Packed Hall");
    }

    @Test
    void fsSrch07_changingOnlySortDoesNotChangeSet() {
        EventCandidate first = event(
                "Morning Track", "running", "Lyon", 3, SearchEventVisibility.PUBLIC, sarah, NOW.plusSeconds(3600));
        EventCandidate second = event(
                "Evening Track",
                "running",
                "Lyon",
                3,
                SearchEventVisibility.PUBLIC,
                parisRunner,
                NOW.plusSeconds(10_000));
        catalog.events.add(first);
        catalog.events.add(second);

        EventSearchList relevance = search.searchEvents(
                alex.id(), null, "running", null, null, null, null, null, "relevance", false, null, 50);
        EventSearchList starts = search.searchEvents(
                alex.id(), null, "running", null, null, null, null, null, "starts_at", false, null, 50);

        assertThat(starts.data())
                .extracting(EventSearchHit::id)
                .containsExactlyInAnyOrderElementsOf(
                        relevance.data().stream().map(EventSearchHit::id).toList());
        assertThat(starts.data()).extracting(EventSearchHit::title).containsExactly("Morning Track", "Evening Track");
    }

    @Test
    void fsSrch04_friendsOnlyEventHiddenFromStranger() {
        EventCandidate friendsOnly = event(
                "Crew Lifts",
                "weightlifting",
                "Lyon",
                5,
                SearchEventVisibility.FRIENDS,
                privateFriend,
                NOW.plusSeconds(3600));
        EventCandidate publicEvent = event(
                "Open Gym", "weightlifting", "Lyon", 5, SearchEventVisibility.PUBLIC, sarah, NOW.plusSeconds(3600));
        catalog.events.add(friendsOnly);
        catalog.events.add(publicEvent);

        EventSearchList asFriend =
                search.searchEvents(alex.id(), null, null, null, null, null, null, null, "relevance", false, null, 20);
        EventSearchList asStranger = search.searchEvents(
                parisRunner.id(), null, null, null, null, null, null, null, "relevance", false, null, 20);

        assertThat(asFriend.data()).extracting(EventSearchHit::title).contains("Crew Lifts", "Open Gym");
        assertThat(asStranger.data())
                .extracting(EventSearchHit::title)
                .contains("Open Gym")
                .doesNotContain("Crew Lifts");
    }

    @Test
    void fsSrch06_blockedOrganizerEventNeverAppears() {
        catalog.events.add(event(
                "Blocked Session", "hiit", "Lyon", 4, SearchEventVisibility.PUBLIC, blocked, NOW.plusSeconds(3600)));

        EventSearchList page = search.searchEvents(
                alex.id(), "Blocked", null, null, null, null, null, null, "relevance", false, null, 20);

        assertThat(page.data()).isEmpty();
    }

    @Test
    void fsSrch08_unknownCallerIsUnauthenticated() {
        assertThatThrownBy(() -> search.searchPeople(
                        UUID.randomUUID(), null, List.of(), null, null, null, null, null, false, null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void fsSrchRadiusOutOfRangeIsValidation() {
        assertThatThrownBy(() ->
                        search.searchPeople(alex.id(), null, List.of(), null, null, 80, null, null, false, null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsSrchUnknownSortIsValidation() {
        assertThatThrownBy(() -> search.searchPeople(
                        alex.id(), null, List.of(), null, null, null, null, "popularity", false, null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    private User member(String handle) {
        User user = new User(
                UUID.randomUUID(), handle + "@example.com", handle, "hashed", UserRole.MEMBER, UserStatus.ACTIVE, NOW);
        users.save(user);
        return user;
    }

    private void profile(
            User user,
            String displayName,
            ProfileVisibility visibility,
            List<String> sports,
            ExperienceLevel experience,
            String city,
            double lat,
            double lng) {
        Profile profile = new Profile(
                user.id(),
                displayName,
                displayName + " bio",
                visibility,
                sports,
                experience,
                city,
                lat,
                lng,
                List.of(),
                null);
        profiles.save(profile);
        catalog.people.add(new PersonCandidate(user, profile, user.createdAt()));
    }

    private EventCandidate event(
            String title,
            String activity,
            String place,
            int remaining,
            SearchEventVisibility visibility,
            User organizer,
            Instant startsAt) {
        Profile organizerProfile =
                profiles.findByUserId(organizer.id()).orElse(Profile.created(organizer.id(), organizer.handle()));
        return new EventCandidate(
                UUID.randomUUID(),
                organizer,
                organizerProfile,
                title,
                title + " description",
                activity,
                place,
                LYON_LAT,
                LYON_LNG,
                startsAt,
                remaining,
                Math.max(remaining, 1),
                visibility,
                Set.of(),
                Set.of(),
                false,
                false);
    }

    private static final class InMemoryCatalog implements SearchCatalog {
        private final List<PersonCandidate> people = new ArrayList<>();
        private final List<EventCandidate> events = new ArrayList<>();

        @Override
        public List<PersonCandidate> people() {
            return List.copyOf(people);
        }

        @Override
        public List<EventCandidate> events() {
            return List.copyOf(events);
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<UUID, User> store = new LinkedHashMap<>();

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream()
                    .filter(user -> user.email().equalsIgnoreCase(email))
                    .findFirst();
        }

        @Override
        public Optional<User> findByHandle(String handle) {
            return store.values().stream()
                    .filter(user -> user.handle().equalsIgnoreCase(handle))
                    .findFirst();
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public void save(User user) {
            store.put(user.id(), user);
        }

        @Override
        public void update(User user) {
            store.put(user.id(), user);
        }
    }

    private static final class InMemoryProfiles implements ProfileRepository {
        private final Map<UUID, Profile> store = new LinkedHashMap<>();

        @Override
        public void save(Profile profile) {
            store.put(profile.userId(), profile);
        }

        @Override
        public void update(Profile profile) {
            store.put(profile.userId(), profile);
        }

        @Override
        public Optional<Profile> findByUserId(UUID userId) {
            return Optional.ofNullable(store.get(userId));
        }
    }

    private static final class InMemoryFriendships implements FriendshipRepository {
        private final Map<UUID, Friendship> store = new HashMap<>();

        void accept(UUID left, UUID right) {
            Friendship row = new Friendship(UUID.randomUUID(), left, right, FriendshipStatus.ACCEPTED, NOW, NOW);
            store.put(row.id(), row);
        }

        void block(UUID blocker, UUID target) {
            Friendship row = new Friendship(UUID.randomUUID(), blocker, target, FriendshipStatus.BLOCKED, NOW, NOW);
            store.put(row.id(), row);
        }

        @Override
        public void save(Friendship friendship) {
            store.put(friendship.id(), friendship);
        }

        @Override
        public void update(Friendship friendship) {
            store.put(friendship.id(), friendship);
        }

        @Override
        public void delete(UUID id) {
            store.remove(id);
        }

        @Override
        public Optional<Friendship> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Friendship> findPair(UUID left, UUID right) {
            return store.values().stream()
                    .filter(row -> row.involves(left) && row.involves(right))
                    .findFirst();
        }

        @Override
        public List<Friendship> listAccepted(UUID userId, InstantIdCursor after, int limit) {
            return store.values().stream()
                    .filter(row -> row.status() == FriendshipStatus.ACCEPTED && row.involves(userId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Friendship> listIncoming(UUID userId, InstantIdCursor after, int limit) {
            return List.of();
        }

        @Override
        public List<Friendship> listOutgoing(UUID userId, InstantIdCursor after, int limit) {
            return List.of();
        }

        @Override
        public boolean areAcceptedFriends(UUID left, UUID right) {
            return findPair(left, right)
                    .filter(row -> row.status() == FriendshipStatus.ACCEPTED)
                    .isPresent();
        }

        @Override
        public int acceptedCount(UUID userId) {
            return listAccepted(userId, null, Integer.MAX_VALUE).size();
        }

        @Override
        public boolean isBlockedEitherWay(UUID left, UUID right) {
            return findPair(left, right)
                    .filter(row -> row.status() == FriendshipStatus.BLOCKED)
                    .isPresent();
        }
    }
}
