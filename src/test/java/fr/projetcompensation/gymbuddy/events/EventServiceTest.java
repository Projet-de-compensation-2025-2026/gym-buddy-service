package fr.projetcompensation.gymbuddy.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.auth.TransactionRunner;
import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.FriendshipStatus;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T18:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriendships friendships;
    private InMemoryMedia media;
    private InMemoryEvents events;
    private EventService service;
    private User alex;
    private User blake;
    private User casey;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friendships = new InMemoryFriendships();
        media = new InMemoryMedia();
        events = new InMemoryEvents();
        events.users = users;
        events.friendships = friendships;
        TransactionRunner transactions = new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                synchronized (events) {
                    return work.get();
                }
            }
        };
        service = new EventService(
                events, media, friendships, users, profiles, transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        alex = member("alex");
        blake = member("blake");
        casey = member("casey");
        friendships.accept(alex.id(), blake.id());
    }

    @Test
    void fsEvt01And02_createInstantEvent() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 3));

        assertThat(created.event().title()).isEqualTo("Morning Sprint Intervals");
        assertThat(created.event().instant()).isTrue();
        assertThat(created.event().capacity()).isEqualTo(3);
        assertThat(created.occurrences()).hasSize(1);
        assertThat(created.occurrences().getFirst().remainingSeats()).isEqualTo(3);
        assertThat(created.remainingSeats()).isEqualTo(3);
    }

    @Test
    void fsEvt01_startInThePastIsValidation() {
        assertThatThrownBy(() -> service.create(
                        alex.id(),
                        new EventDraft(
                                "Past",
                                null,
                                "Running",
                                "Park",
                                null,
                                null,
                                NOW.minusSeconds(60),
                                60,
                                "public",
                                3,
                                null,
                                List.of(),
                                null,
                                List.of())))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsEvt03_recurringListsMaterialisedOccurrences() {
        VisibleEvent created =
                service.create(alex.id(), draft("FREQ=WEEKLY;BYDAY=TU", EventVisibility.FRIENDS.wireValue(), 5));

        assertThat(created.event().instant()).isFalse();
        assertThat(created.occurrences()).isNotEmpty();
        assertThat(created.occurrences().getFirst().occurrence().startsAt()).isEqualTo(START);
        Instant windowEnd = WeeklyRrule.defaultWindowEnd(START);
        assertThat(created.occurrences().getLast().occurrence().startsAt()).isBeforeOrEqualTo(windowEnd);
        assertThat(created.occurrences().size()).isGreaterThan(8);
    }

    @Test
    void listIncludesOrganizerOwnSessionAndHidesStrangerFriendsOnly() {
        VisibleEvent friendsOnly = service.create(alex.id(), draft(null, EventVisibility.FRIENDS.wireValue(), 3));
        VisibleEvent publicEvent = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 3));

        EventList own = service.list(alex.id(), null, null, null, null, 50);
        assertThat(own.data())
                .extracting(row -> row.event().id())
                .contains(friendsOnly.event().id(), publicEvent.event().id());

        EventList friend = service.list(blake.id(), null, null, null, null, 50);
        assertThat(friend.data())
                .extracting(row -> row.event().id())
                .contains(friendsOnly.event().id(), publicEvent.event().id());

        EventList stranger = service.list(casey.id(), null, null, null, null, 50);
        assertThat(stranger.data())
                .extracting(row -> row.event().id())
                .contains(publicEvent.event().id())
                .doesNotContain(friendsOnly.event().id());
    }

    @Test
    void fsEvt04_strangerOnFriendsOnlyIsNotFound() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.FRIENDS.wireValue(), 3));

        assertThatThrownBy(() -> service.get(casey.id(), created.event().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.apply(casey.id(), created.event().id(), null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(service.get(blake.id(), created.event().id()).event().id())
                .isEqualTo(created.event().id());
    }

    @Test
    void fsEvt04_privateIsInviteOnly() {
        VisibleEvent created = service.create(
                alex.id(),
                new EventDraft(
                        "Private session",
                        null,
                        "Yoga",
                        "Studio",
                        null,
                        null,
                        START,
                        60,
                        "private",
                        2,
                        null,
                        List.of(),
                        null,
                        List.of(blake.id())));

        assertThat(service.get(blake.id(), created.event().id()).event().id())
                .isEqualTo(created.event().id());
        assertThatThrownBy(() -> service.get(casey.id(), created.event().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsEvt05And11_applyOnceAndOrganizerForbidden() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 2));
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();

        VisibleApplication pending = service.apply(blake.id(), created.event().id(), occurrenceId);
        assertThat(pending.application().status()).isEqualTo(EventApplicationStatus.PENDING);

        assertThatThrownBy(() -> service.apply(blake.id(), created.event().id(), occurrenceId))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> service.apply(alex.id(), created.event().id(), occurrenceId))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void fsEvt06And07_acceptFillsSeatsThenConflicts() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 1));
        UUID eventId = created.event().id();
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();
        VisibleApplication blakeApp = service.apply(blake.id(), eventId, occurrenceId);
        VisibleApplication caseyApp = service.apply(casey.id(), eventId, occurrenceId);

        VisibleApplication accepted =
                service.accept(alex.id(), blakeApp.application().id());
        assertThat(accepted.application().status()).isEqualTo(EventApplicationStatus.ACCEPTED);
        assertThat(service.get(alex.id(), eventId).remainingSeats()).isZero();

        assertThatThrownBy(
                        () -> service.accept(alex.id(), caseyApp.application().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.CONFLICT));

        service.decline(alex.id(), caseyApp.application().id());
        assertThat(events.findApplication(caseyApp.application().id()))
                .hasValueSatisfying(row -> assertThat(row.status()).isEqualTo(EventApplicationStatus.DECLINED));
    }

    @Test
    void fsEvt07_concurrentLastSeatAcceptsExactlyOne() throws Exception {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 1));
        UUID eventId = created.event().id();
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();
        UUID blakeApp =
                service.apply(blake.id(), eventId, occurrenceId).application().id();
        UUID caseyApp =
                service.apply(casey.id(), eventId, occurrenceId).application().id();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        try {
            Future<?> first = pool.submit(() -> raceAccept(blakeApp, ready, go, accepted, conflicts));
            Future<?> second = pool.submit(() -> raceAccept(caseyApp, ready, go, accepted, conflicts));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(events.countAccepted(occurrenceId)).isEqualTo(1);
    }

    @Test
    void fsEvt08_cancelOccurrenceMarksApplicationsCancelled() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 2));
        UUID eventId = created.event().id();
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();
        VisibleApplication application = service.apply(blake.id(), eventId, occurrenceId);

        service.cancel(alex.id(), eventId, occurrenceId);

        assertThat(events.findApplication(application.application().id()))
                .hasValueSatisfying(row -> assertThat(row.status()).isEqualTo(EventApplicationStatus.CANCELLED));
        assertThat(events.findOccurrence(occurrenceId)).hasValueSatisfying(EventOccurrence::cancelled);
        VisibleEvent applicantView = service.get(blake.id(), eventId);
        assertThat(applicantView.remainingSeats()).isZero();
        assertThat(applicantView.viewerApplication()).isNotNull();
        assertThat(applicantView.viewerApplication().application().status())
                .isEqualTo(EventApplicationStatus.CANCELLED);
        assertThat(applicantView.occurrences()).allSatisfy(row -> {
            assertThat(row.occurrence().cancelled()).isTrue();
            assertThat(row.remainingSeats()).isZero();
        });
    }

    @Test
    void fsEvt08_cancelSeriesReturnsCancelledViewerApplicationOnApplicantGet() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 2));
        UUID eventId = created.event().id();
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();
        service.apply(blake.id(), eventId, occurrenceId);

        VisibleEvent cancelled = service.cancel(alex.id(), eventId, null);

        assertThat(cancelled.event().cancelled()).isTrue();
        assertThat(cancelled.remainingSeats()).isZero();
        VisibleEvent applicantView = service.get(blake.id(), eventId);
        assertThat(applicantView.event().cancelledAt()).isNotNull();
        assertThat(applicantView.remainingSeats()).isZero();
        assertThat(applicantView.viewerApplication()).isNotNull();
        assertThat(applicantView.viewerApplication().application().status())
                .isEqualTo(EventApplicationStatus.CANCELLED);
        assertThat(applicantView.occurrences()).allSatisfy(row -> {
            assertThat(row.occurrence().cancelled()).isTrue();
            assertThat(row.remainingSeats()).isZero();
        });
        VisibleEvent organizerView = service.get(alex.id(), eventId);
        assertThat(organizerView.event().cancelled()).isTrue();
        assertThat(organizerView.remainingSeats()).isZero();
        assertThat(organizerView.occurrences())
                .allSatisfy(row -> assertThat(row.remainingSeats()).isZero());
    }

    @Test
    void fsEvt09_updateAfterAcceptSetsFlag() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 2));
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();
        VisibleApplication application =
                service.apply(blake.id(), created.event().id(), occurrenceId);
        service.accept(alex.id(), application.application().id());

        VisibleEvent updated = service.patch(
                alex.id(),
                created.event().id(),
                new EventDraft(
                        null, null, null, "New gym", null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.event().place()).isEqualTo("New gym");
        assertThat(updated.event().updatedAfterAccept()).isTrue();
    }

    @Test
    void fsEvt10_withdrawFreesSeat() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 1));
        UUID eventId = created.event().id();
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();
        VisibleApplication blakeApp = service.apply(blake.id(), eventId, occurrenceId);
        service.accept(alex.id(), blakeApp.application().id());
        assertThat(service.get(alex.id(), eventId).remainingSeats()).isZero();

        service.withdraw(blake.id(), blakeApp.application().id());
        assertThat(service.get(alex.id(), eventId).remainingSeats()).isEqualTo(1);
        VisibleApplication caseyApp = service.apply(casey.id(), eventId, occurrenceId);
        service.accept(alex.id(), caseyApp.application().id());
        assertThat(service.get(alex.id(), eventId).remainingSeats()).isZero();
    }

    @Test
    void fsEvt12_pastOccurrenceRejectsApply() {
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 2));
        EventOccurrence occurrence = created.occurrences().getFirst().occurrence();
        events.forceStart(occurrence.id(), NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.apply(blake.id(), created.event().id(), occurrence.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
        assertThat(service.get(alex.id(), created.event().id()).event().id())
                .isEqualTo(created.event().id());
    }

    @Test
    void fsEvt13_pendingApplicantsOrderedByMatchingScore() {
        profiles.update(new Profile(
                blake.id(),
                "Blake",
                null,
                fr.projetcompensation.gymbuddy.profiles.ProfileVisibility.PUBLIC,
                List.of("Track & Field"),
                null,
                null,
                null,
                null,
                List.of(),
                null));
        profiles.update(new Profile(
                casey.id(),
                "Casey",
                null,
                fr.projetcompensation.gymbuddy.profiles.ProfileVisibility.PUBLIC,
                List.of("Yoga"),
                null,
                null,
                null,
                null,
                List.of(),
                null));
        VisibleEvent created = service.create(alex.id(), draft(null, EventVisibility.PUBLIC.wireValue(), 2));
        UUID eventId = created.event().id();
        UUID occurrenceId = created.occurrences().getFirst().occurrence().id();
        service.apply(casey.id(), eventId, occurrenceId);
        service.apply(blake.id(), eventId, occurrenceId);

        List<VisibleApplicant> pending = service.get(alex.id(), eventId).pendingApplicants();
        assertThat(pending).hasSize(2);
        assertThat(pending.getFirst().applicant().handle()).isEqualTo("blake");
        assertThat(pending.getFirst().matchingScore())
                .isGreaterThan(pending.getLast().matchingScore());
        assertThat(service.get(blake.id(), eventId).pendingApplicants()).isEmpty();
    }

    private void raceAccept(
            UUID applicationId,
            CountDownLatch ready,
            CountDownLatch go,
            AtomicInteger accepted,
            AtomicInteger conflicts) {
        ready.countDown();
        try {
            go.await(5, TimeUnit.SECONDS);
            service.accept(alex.id(), applicationId);
            accepted.incrementAndGet();
        } catch (AuthException ex) {
            if (ex.code() == ErrorCode.CONFLICT) {
                conflicts.incrementAndGet();
            } else {
                throw ex;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private EventDraft draft(String recurrence, String visibility, int capacity) {
        return new EventDraft(
                "Morning Sprint Intervals",
                "Intervals on the track",
                "Track & Field",
                "Downtown Stadium",
                null,
                null,
                START,
                45,
                visibility,
                capacity,
                recurrence,
                List.of(),
                null,
                List.of());
    }

    private User member(String handle) {
        User user = new User(
                UUID.randomUUID(), handle + "@example.com", handle, "hash", UserRole.MEMBER, UserStatus.ACTIVE, NOW);
        users.save(user);
        profiles.save(Profile.created(user.id(), handle));
        return user;
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
        private final Map<UUID, Friendship> store = new LinkedHashMap<>();

        void accept(UUID left, UUID right) {
            save(new Friendship(UUID.randomUUID(), left, right, FriendshipStatus.ACCEPTED, NOW, NOW));
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
            return List.of();
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
            return (int) store.values().stream()
                    .filter(row -> row.status() == FriendshipStatus.ACCEPTED && row.involves(userId))
                    .count();
        }

        @Override
        public boolean isBlockedEitherWay(UUID left, UUID right) {
            return findPair(left, right)
                    .filter(row -> row.status() == FriendshipStatus.BLOCKED)
                    .isPresent();
        }
    }

    private static final class InMemoryMedia implements MediaRepository {
        private final Map<UUID, Media> store = new LinkedHashMap<>();

        @Override
        public void save(Media row) {
            store.put(row.id(), row);
        }

        @Override
        public void update(Media row) {
            store.put(row.id(), row);
        }

        @Override
        public void delete(UUID id) {
            store.remove(id);
        }

        @Override
        public Optional<Media> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public long usedBytes(UUID ownerId) {
            return 0;
        }

        @Override
        public List<Media> findPendingCreatedBefore(Instant cutoff) {
            return List.of();
        }

        @Override
        public List<Media> findPending() {
            return List.of();
        }

        @Override
        public List<Media> findDeletedBefore(Instant cutoff) {
            return List.of();
        }
    }

    private static final class InMemoryEvents implements EventRepository {
        private final Map<UUID, Event> store = new LinkedHashMap<>();
        private final Map<UUID, EventOccurrence> occurrences = new LinkedHashMap<>();
        private final Map<UUID, EventApplication> applications = new LinkedHashMap<>();
        private final Map<UUID, List<UUID>> invitees = new HashMap<>();
        private InMemoryUsers users;
        private InMemoryFriendships friendships;

        synchronized void forceStart(UUID occurrenceId, Instant startsAt) {
            EventOccurrence occurrence = occurrences.get(occurrenceId);
            occurrences.put(
                    occurrenceId,
                    new EventOccurrence(occurrence.id(), occurrence.eventId(), startsAt, occurrence.cancelledAt()));
        }

        @Override
        public synchronized void save(Event event, List<EventOccurrence> rows, List<UUID> inviteeIds) {
            store.put(event.id(), event);
            for (EventOccurrence occurrence : rows) {
                occurrences.put(occurrence.id(), occurrence);
            }
            invitees.put(event.id(), List.copyOf(inviteeIds));
        }

        @Override
        public synchronized void update(Event event) {
            store.put(event.id(), event);
        }

        @Override
        public synchronized void replaceInvitees(UUID eventId, List<UUID> inviteeIds) {
            invitees.put(eventId, List.copyOf(inviteeIds));
        }

        @Override
        public synchronized void saveOccurrences(List<EventOccurrence> rows) {
            for (EventOccurrence occurrence : rows) {
                occurrences.put(occurrence.id(), occurrence);
            }
        }

        @Override
        public synchronized void updateOccurrence(EventOccurrence occurrence) {
            occurrences.put(occurrence.id(), occurrence);
        }

        @Override
        public synchronized Optional<Event> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public synchronized Optional<Event> findByCoverMediaId(UUID mediaId) {
            return store.values().stream()
                    .filter(event -> mediaId.equals(event.coverMediaId()))
                    .findFirst();
        }

        @Override
        public synchronized List<EventOccurrence> occurrences(UUID eventId) {
            return occurrences.values().stream()
                    .filter(occurrence -> occurrence.eventId().equals(eventId))
                    .sorted(Comparator.comparing(EventOccurrence::startsAt).thenComparing(EventOccurrence::id))
                    .toList();
        }

        @Override
        public synchronized Optional<EventOccurrence> findOccurrence(UUID id) {
            return Optional.ofNullable(occurrences.get(id));
        }

        @Override
        public synchronized Optional<EventOccurrence> lockOccurrence(UUID id) {
            return Optional.ofNullable(occurrences.get(id));
        }

        @Override
        public synchronized List<UUID> inviteeIds(UUID eventId) {
            return invitees.getOrDefault(eventId, List.of());
        }

        @Override
        public synchronized boolean isInvitee(UUID eventId, UUID userId) {
            return invitees.getOrDefault(eventId, List.of()).contains(userId);
        }

        @Override
        public synchronized void saveApplication(EventApplication application) {
            applications.put(application.id(), application);
        }

        @Override
        public synchronized void updateApplication(EventApplication application) {
            applications.put(application.id(), application);
        }

        @Override
        public synchronized Optional<EventApplication> findApplication(UUID id) {
            return Optional.ofNullable(applications.get(id));
        }

        @Override
        public synchronized Optional<EventApplication> findApplication(UUID occurrenceId, UUID applicantId) {
            return applications.values().stream()
                    .filter(row -> row.occurrenceId().equals(occurrenceId)
                            && row.applicantId().equals(applicantId))
                    .findFirst();
        }

        @Override
        public synchronized List<EventApplication> applicationsForEvent(UUID eventId) {
            return applications.values().stream()
                    .filter(row -> row.eventId().equals(eventId))
                    .toList();
        }

        @Override
        public synchronized List<EventApplication> pendingForOccurrence(UUID occurrenceId) {
            return applications.values().stream()
                    .filter(row ->
                            row.occurrenceId().equals(occurrenceId) && row.status() == EventApplicationStatus.PENDING)
                    .toList();
        }

        @Override
        public synchronized boolean hasAccepted(UUID eventId, UUID userId) {
            return applications.values().stream()
                    .anyMatch(row -> row.eventId().equals(eventId)
                            && row.applicantId().equals(userId)
                            && row.status() == EventApplicationStatus.ACCEPTED);
        }

        @Override
        public synchronized int countAccepted(UUID occurrenceId) {
            return (int) applications.values().stream()
                    .filter(row ->
                            row.occurrenceId().equals(occurrenceId) && row.status() == EventApplicationStatus.ACCEPTED)
                    .count();
        }

        @Override
        public synchronized int countAcceptedCoAttendance(UUID organizerId, UUID applicantId) {
            return (int) applications.values().stream()
                    .filter(row ->
                            row.applicantId().equals(applicantId) && row.status() == EventApplicationStatus.ACCEPTED)
                    .filter(row -> {
                        Event event = store.get(row.eventId());
                        return event != null && event.organizerId().equals(organizerId);
                    })
                    .count();
        }

        @Override
        public synchronized List<Event> listVisible(
                UUID viewerId, String kind, Instant from, Instant until, InstantIdCursor after, int limit) {
            List<Event> rows = new ArrayList<>();
            for (Event event : store.values()) {
                if (event.hidden() || event.cancelled()) {
                    continue;
                }
                if ("instant".equals(kind) && !event.instant()) {
                    continue;
                }
                if ("recurring".equals(kind) && event.instant()) {
                    continue;
                }
                boolean inWindow = occurrences(event.id()).stream()
                        .anyMatch(occurrence -> !occurrence.cancelled()
                                && !occurrence.startsAt().isBefore(from)
                                && occurrence.startsAt().isBefore(until));
                if (!inWindow) {
                    continue;
                }
                User viewer = users.findById(viewerId).orElse(null);
                if (viewer == null || !EventAccess.canView(event, viewer, friendships, users, this)) {
                    continue;
                }
                if (after != null) {
                    int cmp = event.startsAt().compareTo(after.at());
                    if (cmp < 0 || (cmp == 0 && event.id().compareTo(after.id()) <= 0)) {
                        continue;
                    }
                }
                rows.add(event);
            }
            rows.sort(Comparator.comparing(Event::startsAt).thenComparing(Event::id));
            return rows.stream().limit(limit).toList();
        }
    }
}
