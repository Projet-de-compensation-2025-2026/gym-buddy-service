package fr.projetcompensation.gymbuddy.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.FriendshipStatus;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.posts.LikeRow;
import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.posts.PostAccess;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.posts.PostService;
import fr.projetcompensation.gymbuddy.posts.VisiblePost;
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

class FeedServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriendships friendships;
    private InMemoryMedia media;
    private InMemoryPosts posts;
    private MutableClock clock;
    private PostService postService;
    private FeedService feed;
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
        postService = new PostService(posts, media, friendships, users, profiles, clock);
        feed = new FeedService(posts, friendships, users, profiles);
        alex = member("alex");
        blake = member("blake");
        casey = member("casey");
        friendships.accept(alex.id(), blake.id());
    }

    @Test
    void fsFeed01_includesOwnAndFriendPostsAndReposts() {
        VisiblePost own = postService.create(alex.id(), "own lift", "friends", List.of());
        clock.set(NOW.plusSeconds(10));
        VisiblePost friendPost = postService.create(blake.id(), "blake pr", "friends", List.of());
        clock.set(NOW.plusSeconds(20));
        VisiblePost strangerPublic = postService.create(casey.id(), "casey public", "public", List.of());
        clock.set(NOW.plusSeconds(30));
        postService.repost(blake.id(), strangerPublic.post().id());

        FeedList page = feed.list(alex.id(), null, 20);

        assertThat(page.data()).hasSize(3);
        assertThat(page.data().get(0).activity().kind()).isEqualTo(FeedKind.REPOST);
        assertThat(page.data().get(0).actor().id()).isEqualTo(blake.id());
        assertThat(page.data().get(0).post().post().id())
                .isEqualTo(strangerPublic.post().id());
        assertThat(page.data().get(1).post().post().id())
                .isEqualTo(friendPost.post().id());
        assertThat(page.data().get(2).post().post().id()).isEqualTo(own.post().id());
    }

    @Test
    void fsFeed02_ordersByActivityTimeNewestFirst() {
        clock.set(NOW);
        VisiblePost older = postService.create(alex.id(), "older", "friends", List.of());
        clock.set(NOW.plus(Duration.ofMinutes(5)));
        VisiblePost newer = postService.create(blake.id(), "newer", "friends", List.of());

        FeedList page = feed.list(alex.id(), null, 20);

        assertThat(page.data())
                .extracting(item -> item.post().post().id())
                .containsExactly(newer.post().id(), older.post().id());
        assertThat(page.data().get(0).activity().activityAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void fsFeed03_cursorBeforeAndSizeCap() {
        clock.set(NOW);
        postService.create(alex.id(), "one", "friends", List.of());
        clock.set(NOW.plusSeconds(10));
        postService.create(alex.id(), "two", "friends", List.of());
        clock.set(NOW.plusSeconds(20));
        postService.create(alex.id(), "three", "friends", List.of());

        FeedList first = feed.list(alex.id(), null, 2);
        assertThat(first.data()).hasSize(2);
        assertThat(first.data().get(0).post().post().body()).isEqualTo("three");
        assertThat(first.data().get(1).post().post().body()).isEqualTo("two");
        assertThat(first.next()).isNotNull();

        FeedList second = feed.list(alex.id(), first.next(), 2);
        assertThat(second.data()).hasSize(1);
        assertThat(second.data().get(0).post().post().body()).isEqualTo("one");
        assertThat(second.next()).isNull();

        assertThatThrownBy(() -> feed.list(alex.id(), null, 51))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsFeed04_hiddenAndDeletedAreOmitted() {
        VisiblePost live = postService.create(blake.id(), "live", "friends", List.of());
        clock.set(NOW.plusSeconds(10));
        VisiblePost gone = postService.create(blake.id(), "gone", "friends", List.of());
        postService.delete(blake.id(), gone.post().id());
        clock.set(NOW.plusSeconds(20));
        VisiblePost hidden = postService.create(blake.id(), "hidden", "friends", List.of());
        hide(hidden.post(), NOW.plusSeconds(21));

        FeedList page = feed.list(alex.id(), null, 20);

        assertThat(page.data())
                .extracting(item -> item.post().post().id())
                .containsExactly(live.post().id());
        assertThat(page.data())
                .noneMatch(item -> "deleted".equalsIgnoreCase(item.post().post().body()));
    }

    @Test
    void fsFeed05_publicStrangerPostsStayOffTheFriendsFeed() {
        VisiblePost stranger = postService.create(casey.id(), "public pr", "public", List.of());

        FeedList alexFeed = feed.list(alex.id(), null, 20);
        FeedList caseyFeed = feed.list(casey.id(), null, 20);

        assertThat(alexFeed.data()).isEmpty();
        assertThat(caseyFeed.data())
                .extracting(item -> item.post().post().id())
                .containsExactly(stranger.post().id());
    }

    @Test
    void fsFeed06_itemExposesAuthorTimeBodyCountsAndViewerLiked() {
        VisiblePost created = postService.create(blake.id(), "squats", "friends", List.of());
        postService.like(alex.id(), created.post().id());

        VisibleFeedItem item = feed.list(alex.id(), null, 20).data().get(0);

        assertThat(item.actor().handle()).isEqualTo("blake");
        assertThat(item.activity().activityAt()).isEqualTo(NOW);
        assertThat(item.post().post().body()).isEqualTo("squats");
        assertThat(item.post().likeCount()).isEqualTo(1);
        assertThat(item.post().commentCount()).isZero();
        assertThat(item.post().liked()).isTrue();
    }

    @Test
    void fsFeed_friendsOnlyVisibleToFriendNotStranger() {
        postService.create(blake.id(), "friends only", "friends", List.of());

        assertThat(feed.list(alex.id(), null, 20).data()).hasSize(1);
        assertThat(feed.list(casey.id(), null, 20).data()).isEmpty();
    }

    @Test
    void fsFeed_repostOfDeletedOriginalIsOmitted() {
        VisiblePost original = postService.create(casey.id(), "public", "public", List.of());
        clock.set(NOW.plusSeconds(5));
        postService.repost(blake.id(), original.post().id());
        postService.delete(casey.id(), original.post().id());

        assertThat(feed.list(alex.id(), null, 20).data()).isEmpty();
    }

    @Test
    void fsFeed_unauthenticatedWhenCallerMissing() {
        assertThatThrownBy(() -> feed.list(UUID.randomUUID(), null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private void hide(Post row, Instant at) {
        posts.update(new Post(
                row.id(),
                row.authorId(),
                row.body(),
                row.visibility(),
                row.createdAt(),
                row.editedAt(),
                row.deletedAt(),
                at,
                "hidden"));
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

        List<Friendship> acceptedInvolving(UUID userId) {
            return store.values().stream()
                    .filter(row -> row.status() == FriendshipStatus.ACCEPTED && row.involves(userId))
                    .toList();
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

    private final class InMemoryPosts implements PostRepository {
        private final Map<UUID, Post> store = new LinkedHashMap<>();
        private final Map<UUID, List<UUID>> mediaByPost = new HashMap<>();
        private final Map<UUID, Map<UUID, Instant>> likes = new HashMap<>();
        private final Map<String, StoredRepost> reposts = new LinkedHashMap<>();

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
            return List.of();
        }

        @Override
        public boolean insertRepost(UUID userId, UUID postId, Instant at) {
            String key = userId + ":" + postId;
            if (reposts.containsKey(key)) {
                return false;
            }
            reposts.put(key, new StoredRepost(UUID.randomUUID(), userId, postId, at));
            return true;
        }

        @Override
        public boolean deleteRepost(UUID userId, UUID postId) {
            return reposts.remove(userId + ":" + postId) != null;
        }

        @Override
        public boolean reposted(UUID userId, UUID postId) {
            return reposts.containsKey(userId + ":" + postId);
        }

        @Override
        public long repostCount(UUID postId) {
            return reposts.values().stream()
                    .filter(row -> row.postId().equals(postId))
                    .count();
        }

        @Override
        public long commentCount(UUID postId) {
            return 0;
        }

        @Override
        public List<FeedActivity> listFeed(UUID viewerId, InstantIdCursor before, int limit) {
            User viewer = users.findById(viewerId).orElse(null);
            if (viewer == null || !viewer.active()) {
                return List.of();
            }
            java.util.Set<UUID> actors = new java.util.HashSet<>();
            actors.add(viewerId);
            for (Friendship row : friendships.acceptedInvolving(viewerId)) {
                actors.add(row.other(viewerId));
            }
            List<FeedActivity> out = new ArrayList<>();
            for (Post post : store.values()) {
                if (!actors.contains(post.authorId())) {
                    continue;
                }
                if (!PostAccess.canView(post, viewer, friendships, users)) {
                    continue;
                }
                out.add(new FeedActivity(post.id(), FeedKind.POST, post.authorId(), post.id(), post.createdAt()));
            }
            for (StoredRepost repost : reposts.values()) {
                if (!actors.contains(repost.userId())) {
                    continue;
                }
                User actor = users.findById(repost.userId()).orElse(null);
                if (actor == null || !actor.active()) {
                    continue;
                }
                Post original = store.get(repost.postId());
                if (original == null || !PostAccess.canView(original, viewer, friendships, users)) {
                    continue;
                }
                out.add(new FeedActivity(repost.id(), FeedKind.REPOST, repost.userId(), repost.postId(), repost.at()));
            }
            out.sort(Comparator.comparing(FeedActivity::activityAt)
                    .thenComparing(FeedActivity::id)
                    .reversed());
            if (before != null) {
                out = out.stream()
                        .filter(activity -> activity.activityAt().isBefore(before.at())
                                || (activity.activityAt().equals(before.at())
                                        && activity.id().compareTo(before.id()) < 0))
                        .toList();
            }
            return out.stream().limit(limit).toList();
        }
    }

    private record StoredRepost(UUID id, UUID userId, UUID postId, Instant at) {}
}
