package fr.projetcompensation.gymbuddy.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.FriendshipStatus;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaKind;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.media.MediaStatus;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriendships friendships;
    private InMemoryMedia media;
    private InMemoryPosts posts;
    private MutableClock clock;
    private PostService service;
    private User alex;
    private User blake;
    private User casey;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friendships = new InMemoryFriendships();
        media = new InMemoryMedia();
        posts = new InMemoryPosts();
        clock = new MutableClock(NOW);
        service = new PostService(posts, media, friendships, users, profiles, clock);
        alex = member("alex");
        blake = member("blake");
        casey = member("casey");
        friendships.accept(alex.id(), blake.id());
    }

    @Test
    void fsPost01_createTextPost() {
        VisiblePost created = service.create(alex.id(), "Crushed leg day.", "friends", List.of());

        assertThat(created.post().body()).isEqualTo("Crushed leg day.");
        assertThat(created.post().visibility()).isEqualTo(PostVisibility.FRIENDS);
        assertThat(created.mediaIds()).isEmpty();
        assertThat(created.author().handle()).isEqualTo("alex");
    }

    @Test
    void fsPost01_emptyBodyAndNoMediaIsValidation() {
        assertThatThrownBy(() -> service.create(alex.id(), "  ", null, List.of()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsPost01_emptyBodyWithImagesIsOk() {
        Media image = readyPostImage(alex);
        VisiblePost created = service.create(alex.id(), "", "public", List.of(image.id()));

        assertThat(created.post().body()).isNull();
        assertThat(created.post().visibility()).isEqualTo(PostVisibility.PUBLIC);
        assertThat(created.mediaIds()).containsExactly(image.id());
    }

    @Test
    void fsPost01_tooManyImagesIsValidation() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(readyPostImage(alex).id());
        }
        assertThatThrownBy(() -> service.create(alex.id(), "too many", "friends", ids))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsPost01_wrongKindOrNotReadyIsValidation() {
        Media avatar = new Media(
                UUID.randomUUID(),
                alex.id(),
                MediaKind.AVATAR,
                "image/jpeg",
                12,
                0,
                MediaStatus.READY,
                "original/a",
                NOW,
                null);
        media.save(avatar);
        assertThatThrownBy(() -> service.create(alex.id(), null, "friends", List.of(avatar.id())))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsPost02_friendsOnlyHiddenFromStranger() {
        VisiblePost created = service.create(alex.id(), "friends only", "friends", List.of());

        assertThat(service.get(blake.id(), created.post().id()).post().id())
                .isEqualTo(created.post().id());
        assertThatThrownBy(() -> service.get(casey.id(), created.post().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsPost02_publicReadableByStranger() {
        VisiblePost created = service.create(alex.id(), "public pr", "public", List.of());
        assertThat(service.get(casey.id(), created.post().id()).post().body()).isEqualTo("public pr");
    }

    @Test
    void fsPost03_editWithin15MinutesSetsEditedAt() {
        VisiblePost created = service.create(alex.id(), "first", "friends", List.of());
        clock.set(NOW.plus(Duration.ofMinutes(14)));
        VisiblePost edited = service.edit(alex.id(), created.post().id(), "second");

        assertThat(edited.post().body()).isEqualTo("second");
        assertThat(edited.post().editedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(14)));
    }

    @Test
    void fsPost03_editAfterWindowIsForbidden() {
        VisiblePost created = service.create(alex.id(), "first", "friends", List.of());
        clock.set(NOW.plus(Duration.ofMinutes(15)).plusSeconds(1));
        assertThatThrownBy(() -> service.edit(alex.id(), created.post().id(), "late"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void fsPost04_softDeleteReturnsNotFound() {
        VisiblePost created = service.create(alex.id(), "gone", "public", List.of());
        service.delete(alex.id(), created.post().id());
        assertThatThrownBy(() -> service.get(alex.id(), created.post().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(blake.id(), created.post().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsPost05And06_viewerCanRepostOnceAndUndo() {
        VisiblePost created = service.create(alex.id(), "pr", "public", List.of());
        VisiblePost reposted = service.repost(blake.id(), created.post().id());
        assertThat(reposted.reposted()).isTrue();
        assertThat(reposted.repostCount()).isEqualTo(1);

        assertThatThrownBy(() -> service.repost(blake.id(), created.post().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.CONFLICT));

        service.unrepost(blake.id(), created.post().id());
        assertThat(service.get(blake.id(), created.post().id()).reposted()).isFalse();
        assertThat(service.get(blake.id(), created.post().id()).repostCount()).isZero();
    }

    @Test
    void fsPost07_likeUnlikeToggleDoesNotDoubleCount() {
        VisiblePost created = service.create(alex.id(), "likes", "public", List.of());
        service.like(blake.id(), created.post().id());
        service.like(blake.id(), created.post().id());
        assertThat(service.get(blake.id(), created.post().id()).likeCount()).isEqualTo(1);
        assertThat(service.get(blake.id(), created.post().id()).liked()).isTrue();

        service.unlike(blake.id(), created.post().id());
        assertThat(service.get(blake.id(), created.post().id()).likeCount()).isZero();
        assertThat(service.get(blake.id(), created.post().id()).liked()).isFalse();
    }

    @Test
    void fsPost07_strangerLikeFriendsOnlyIsNotFound() {
        VisiblePost created = service.create(alex.id(), "secret", "friends", List.of());
        assertThatThrownBy(() -> service.like(casey.id(), created.post().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(service.get(alex.id(), created.post().id()).likeCount()).isZero();
    }

    @Test
    void fsPost08_nominativeListIsAuthorOnly() {
        VisiblePost created = service.create(alex.id(), "list", "public", List.of());
        service.like(blake.id(), created.post().id());
        service.like(casey.id(), created.post().id());

        PostLikerList authorView = service.likes(alex.id(), created.post().id(), null, 20);
        assertThat(authorView.data()).hasSize(2);
        assertThatThrownBy(() -> service.likes(blake.id(), created.post().id(), null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsPost_attachedImageReadableByFriendNotStranger() {
        Media image = readyPostImage(alex);
        service.create(alex.id(), "with photo", "friends", List.of(image.id()));
        PostAttachedMediaAccess access = new PostAttachedMediaAccess(posts, friendships, users);
        assertThat(access.canRead(blake.id(), image)).isTrue();
        assertThat(access.canRead(casey.id(), image)).isFalse();
    }

    private Media readyPostImage(User owner) {
        Media row = new Media(
                UUID.randomUUID(),
                owner.id(),
                MediaKind.POST,
                "image/jpeg",
                24,
                0,
                MediaStatus.READY,
                "original/" + owner.id() + "/" + UUID.randomUUID(),
                NOW,
                null);
        media.save(row);
        return row;
    }

    private User member(String handle) {
        User user = new User(
                UUID.randomUUID(), handle + "@example.com", handle, "hash", UserRole.MEMBER, UserStatus.ACTIVE, NOW);
        users.save(user);
        profiles.save(Profile.created(user.id(), handle));
        return user;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
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
        private final Map<UUID, Friendship> store = new LinkedHashMap<>();

        void accept(UUID left, UUID right) {
            Friendship row = new Friendship(UUID.randomUUID(), left, right, FriendshipStatus.ACCEPTED, NOW, NOW);
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

    private static final class InMemoryPosts implements PostRepository {
        private final Map<UUID, Post> store = new LinkedHashMap<>();
        private final Map<UUID, List<UUID>> mediaByPost = new HashMap<>();
        private final Map<UUID, Map<UUID, Instant>> likes = new HashMap<>();
        private final Map<UUID, Map<UUID, Instant>> reposts = new HashMap<>();

        @Override
        public void save(Post post, List<UUID> mediaIds) {
            store.put(post.id(), post);
            mediaByPost.put(post.id(), List.copyOf(mediaIds));
        }

        @Override
        public void update(Post post) {
            store.put(post.id(), post);
        }

        @Override
        public Optional<Post> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Post> findByMediaId(UUID mediaId) {
            return mediaByPost.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(mediaId))
                    .map(entry -> store.get(entry.getKey()))
                    .findFirst();
        }

        @Override
        public List<UUID> mediaIds(UUID postId) {
            return mediaByPost.getOrDefault(postId, List.of());
        }

        @Override
        public boolean insertLike(UUID userId, UUID postId, Instant at) {
            Map<UUID, Instant> byUser = likes.computeIfAbsent(postId, id -> new LinkedHashMap<>());
            if (byUser.containsKey(userId)) {
                return false;
            }
            byUser.put(userId, at);
            return true;
        }

        @Override
        public boolean deleteLike(UUID userId, UUID postId) {
            Map<UUID, Instant> byUser = likes.get(postId);
            if (byUser == null || !byUser.containsKey(userId)) {
                return false;
            }
            byUser.remove(userId);
            return true;
        }

        @Override
        public boolean liked(UUID userId, UUID postId) {
            return likes.getOrDefault(postId, Map.of()).containsKey(userId);
        }

        @Override
        public long likeCount(UUID postId) {
            return likes.getOrDefault(postId, Map.of()).size();
        }

        @Override
        public List<LikeRow> listLikes(UUID postId, InstantIdCursor after, int limit) {
            return likes.getOrDefault(postId, Map.of()).entrySet().stream()
                    .map(entry -> new LikeRow(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparing(LikeRow::likedAt)
                            .thenComparing(LikeRow::userId)
                            .reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean insertRepost(UUID userId, UUID postId, Instant at) {
            Map<UUID, Instant> byUser = reposts.computeIfAbsent(postId, id -> new LinkedHashMap<>());
            if (byUser.containsKey(userId)) {
                return false;
            }
            byUser.put(userId, at);
            return true;
        }

        @Override
        public boolean deleteRepost(UUID userId, UUID postId) {
            Map<UUID, Instant> byUser = reposts.get(postId);
            if (byUser == null || !byUser.containsKey(userId)) {
                return false;
            }
            byUser.remove(userId);
            return true;
        }

        @Override
        public boolean reposted(UUID userId, UUID postId) {
            return reposts.getOrDefault(postId, Map.of()).containsKey(userId);
        }

        @Override
        public long repostCount(UUID postId) {
            return reposts.getOrDefault(postId, Map.of()).size();
        }

        @Override
        public long commentCount(UUID postId) {
            return 0;
        }

        @Override
        public List<fr.projetcompensation.gymbuddy.feed.FeedActivity> listFeed(
                UUID viewerId, InstantIdCursor before, int limit) {
            return List.of();
        }
    }
}
