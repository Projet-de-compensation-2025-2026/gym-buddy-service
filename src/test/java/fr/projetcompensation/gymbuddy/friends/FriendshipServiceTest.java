package fr.projetcompensation.gymbuddy.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FriendshipServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriendships friendships;
    private FriendshipService service;
    private User alex;
    private User blake;
    private User casey;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friendships = new InMemoryFriendships();
        service = new FriendshipService(friendships, users, profiles, Clock.fixed(NOW, ZoneOffset.UTC));
        alex = member("alex");
        blake = member("blake");
        casey = member("casey");
    }

    @Test
    void fsFrnd01And02_requestThenAcceptIsSymmetric() {
        ListedFriendship pending = service.request(alex.id(), "blake", null);

        assertThat(pending.friendship().status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(friendships.areAcceptedFriends(alex.id(), blake.id())).isFalse();

        ListedFriendship accepted =
                service.accept(blake.id(), pending.friendship().id());

        assertThat(accepted.friendship().status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(friendships.areAcceptedFriends(alex.id(), blake.id())).isTrue();
        assertThat(friendships.areAcceptedFriends(blake.id(), alex.id())).isTrue();
        assertThat(friendships.acceptedCount(alex.id())).isEqualTo(1);
    }

    @Test
    void fsFrnd06_selfFriendIsValidation() {
        assertThatThrownBy(() -> service.request(alex.id(), "alex", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsFrnd06_duplicatePendingIsConflict() {
        service.request(alex.id(), "blake", null);

        assertThatThrownBy(() -> service.request(alex.id(), "blake", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void fsFrnd05_blockHidesTargetAndRejectsNewRequest() {
        ListedFriendship pending = service.request(alex.id(), "blake", null);
        service.block(blake.id(), alex.id());

        assertThat(friendships.findById(pending.friendship().id()))
                .hasValueSatisfying(row -> assertThat(row.status()).isEqualTo(FriendshipStatus.BLOCKED));
        assertThatThrownBy(() -> service.request(alex.id(), "blake", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsFrnd03_requesterCanCancelPending() {
        ListedFriendship pending = service.request(alex.id(), "blake", null);
        service.remove(alex.id(), pending.friendship().id());
        assertThat(friendships.findById(pending.friendship().id())).isEmpty();
    }

    @Test
    void fsFrnd04_eitherSideCanUnfriend() {
        ListedFriendship pending = service.request(alex.id(), "blake", null);
        service.accept(blake.id(), pending.friendship().id());
        service.remove(blake.id(), pending.friendship().id());
        assertThat(friendships.areAcceptedFriends(alex.id(), blake.id())).isFalse();
        service.request(blake.id(), "alex", null);
        assertThat(friendships.findPair(alex.id(), blake.id()))
                .hasValueSatisfying(row -> assertThat(row.status()).isEqualTo(FriendshipStatus.PENDING));
    }

    @Test
    void fsFrnd02_onlyAddresseeCanAccept() {
        ListedFriendship pending = service.request(alex.id(), "blake", null);
        assertThatThrownBy(() -> service.accept(alex.id(), pending.friendship().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.accept(casey.id(), pending.friendship().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsFrnd07_strangerCannotListAcceptedFriends() {
        ListedFriendship pending = service.request(alex.id(), "blake", null);
        service.accept(blake.id(), pending.friendship().id());
        assertThatThrownBy(() -> service.list(casey.id(), "accepted", "alex", null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        FriendshipList own = service.list(alex.id(), "accepted", null, null, 20);
        assertThat(own.data()).hasSize(1);
        assertThat(own.data().getFirst().peer().handle()).isEqualTo("blake");
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
            return page(
                    store.values().stream()
                            .filter(row -> row.status() == FriendshipStatus.ACCEPTED && row.involves(userId))
                            .toList(),
                    after,
                    limit);
        }

        @Override
        public List<Friendship> listIncoming(UUID userId, InstantIdCursor after, int limit) {
            return page(
                    store.values().stream()
                            .filter(row -> row.status() == FriendshipStatus.PENDING
                                    && row.addresseeId().equals(userId))
                            .toList(),
                    after,
                    limit);
        }

        @Override
        public List<Friendship> listOutgoing(UUID userId, InstantIdCursor after, int limit) {
            return page(
                    store.values().stream()
                            .filter(row -> row.status() == FriendshipStatus.PENDING
                                    && row.requesterId().equals(userId))
                            .toList(),
                    after,
                    limit);
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

        private static List<Friendship> page(List<Friendship> rows, InstantIdCursor after, int limit) {
            List<Friendship> sorted = new ArrayList<>(rows);
            sorted.sort(Comparator.comparing(Friendship::createdAt)
                    .thenComparing(Friendship::id)
                    .reversed());
            return sorted.stream().limit(limit).toList();
        }
    }
}
