package fr.projetcompensation.gymbuddy.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriends friends;
    private ProfileService service;
    private User owner;
    private User stranger;
    private User buddy;
    private User staff;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friends = new InMemoryFriends();
        service = new ProfileService(users, profiles, friends);
        owner = member("blake", UserRole.MEMBER);
        stranger = member("alex", UserRole.MEMBER);
        buddy = member("casey", UserRole.MEMBER);
        staff = member("mod", UserRole.MODERATOR);
        profiles.save(new Profile(
                owner.id(),
                "Blake",
                "Looking for a spotter",
                ProfileVisibility.PRIVATE,
                java.util.List.of("running"),
                ExperienceLevel.ADVANCED,
                "Austin, TX",
                30.2672,
                -97.7431,
                java.util.List.of(new PreferredWindow(1, "06:00", "08:00")),
                null));
        friends.accept(owner.id(), buddy.id());
    }

    @Test
    void fsProf04_strangerOnPrivateProfileSeesStubWithoutBioSportsOrLocation() {
        VisibleProfile view = service.byHandle(stranger.id(), "blake");

        assertThat(view.view()).isEqualTo(VisibleProfile.View.STUB);
        assertThat(view.profile().bio()).isEqualTo("Looking for a spotter");
        assertThat(view.full()).isFalse();
        assertThat(view.owner().handle()).isEqualTo("blake");
        assertThat(view.profile().visibility()).isEqualTo(ProfileVisibility.PRIVATE);
        assertThat(view.friendCount()).isZero();
    }

    @Test
    void fsProf04_acceptedFriendSeesFullPrivateProfile() {
        VisibleProfile view = service.byHandle(buddy.id(), "blake");

        assertThat(view.full()).isTrue();
        assertThat(view.profile().bio()).isEqualTo("Looking for a spotter");
        assertThat(view.profile().sports()).containsExactly("running");
        assertThat(view.profile().city()).isEqualTo("Austin, TX");
        assertThat(view.friendCount()).isEqualTo(1);
    }

    @Test
    void fsProf04_ownerAndStaffSeeFullPrivateProfile() {
        assertThat(service.byHandle(owner.id(), "blake").full()).isTrue();
        assertThat(service.byHandle(staff.id(), "blake").full()).isTrue();
        assertThat(service.me(owner.id()).full()).isTrue();
    }

    @Test
    void fsProf05_closedHandleIsNotFound() {
        users.replace(owner.withStatus(UserStatus.CLOSED));

        assertThatThrownBy(() -> service.byHandle(stranger.id(), "blake"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void patchRejectsHandleThatIsAnEmail() {
        assertThatThrownBy(() -> service.patchMe(
                        owner.id(),
                        new ProfilePatch(
                                "blake@example.com",
                                null,
                                null,
                                false,
                                null,
                                java.util.List.of(),
                                false,
                                null,
                                false,
                                null,
                                false,
                                null,
                                false,
                                null,
                                false,
                                java.util.List.of(),
                                false,
                                null,
                                false)))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.code()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(authEx.details()).contains(new FieldIssue("handle", "format"));
                });
        assertThat(users.findById(owner.id()).orElseThrow().handle()).isEqualTo("blake");
    }

    @Test
    void patchRejectsTakenHandle() {
        profiles.save(Profile.created(stranger.id(), "Alex"));

        assertThatThrownBy(() -> service.patchMe(
                        owner.id(),
                        new ProfilePatch(
                                "alex",
                                null,
                                null,
                                false,
                                null,
                                java.util.List.of(),
                                false,
                                null,
                                false,
                                null,
                                false,
                                null,
                                false,
                                null,
                                false,
                                java.util.List.of(),
                                false,
                                null,
                                false)))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.CONFLICT));
    }

    private User member(String handle, UserRole role) {
        User user =
                new User(UUID.randomUUID(), handle + "@example.com", handle, "hashed", role, UserStatus.ACTIVE, NOW);
        users.save(user);
        return user;
    }

    private static final class InMemoryUsers implements fr.projetcompensation.gymbuddy.users.UserRepository {
        private final java.util.Map<UUID, User> store = new java.util.LinkedHashMap<>();

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

        void replace(User user) {
            store.put(user.id(), user);
        }
    }

    private static final class InMemoryProfiles implements ProfileRepository {
        private final java.util.Map<UUID, Profile> store = new java.util.LinkedHashMap<>();

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

    private static final class InMemoryFriends implements FriendshipQueries {
        private final Set<String> pairs = new HashSet<>();

        void accept(UUID left, UUID right) {
            pairs.add(key(left, right));
        }

        @Override
        public boolean areAcceptedFriends(UUID left, UUID right) {
            return pairs.contains(key(left, right));
        }

        @Override
        public int acceptedCount(UUID userId) {
            return (int) pairs.stream()
                    .filter(pair -> pair.contains(userId.toString()))
                    .count();
        }

        private static String key(UUID left, UUID right) {
            return left.compareTo(right) < 0 ? left + ":" + right : right + ":" + left;
        }
    }
}
