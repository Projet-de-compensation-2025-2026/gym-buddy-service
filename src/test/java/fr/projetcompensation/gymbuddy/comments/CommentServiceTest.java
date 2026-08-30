package fr.projetcompensation.gymbuddy.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.FriendshipStatus;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.posts.PostVisibility;
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
import java.util.Comparator;
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

class CommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriendships friendships;
    private InMemoryPosts posts;
    private InMemoryComments comments;
    private MutableClock clock;
    private CommentService service;
    private User alex;
    private User blake;
    private User casey;
    private Post publicPost;
    private Post friendsPost;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friendships = new InMemoryFriendships();
        posts = new InMemoryPosts();
        comments = new InMemoryComments();
        clock = new MutableClock(NOW);
        service = new CommentService(comments, posts, friendships, users, profiles, clock);
        alex = member("alex");
        blake = member("blake");
        casey = member("casey");
        friendships.accept(alex.id(), blake.id());
        publicPost = post(alex, PostVisibility.PUBLIC, "pr");
        friendsPost = post(alex, PostVisibility.FRIENDS, "secret");
    }

    @Test
    void fsCmt01_rootCommentOnVisiblePost() {
        VisibleComment created = service.create(blake.id(), publicPost.id(), "Huge milestone!", null);

        assertThat(created.comment().depth()).isZero();
        assertThat(created.comment().parentId()).isNull();
        assertThat(created.comment().body()).isEqualTo("Huge milestone!");
        assertThat(created.author().handle()).isEqualTo("blake");
        assertThat(service.listRoots(alex.id(), publicPost.id(), null, 20).data())
                .hasSize(1);
    }

    @Test
    void fsCmt01_cannotCommentOnUnviewablePost() {
        assertThatThrownBy(() -> service.create(casey.id(), friendsPost.id(), "nope", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsCmt02_replyIncrementsDepth() {
        VisibleComment root = service.create(blake.id(), publicPost.id(), "root", null);
        VisibleComment reply = service.create(
                alex.id(), publicPost.id(), "thanks", root.comment().id());

        assertThat(reply.comment().depth()).isEqualTo(1);
        assertThat(reply.comment().parentId()).isEqualTo(root.comment().id());
        assertThat(service.listReplies(alex.id(), root.comment().id(), null, 20).data())
                .extracting(row -> row.comment().id())
                .containsExactly(reply.comment().id());
    }

    @Test
    void fsCmt03_depth3ReplyStoresDepth4() {
        UUID parent = chain(publicPost.id(), 3);
        VisibleComment leaf = service.create(blake.id(), publicPost.id(), "depth 4", parent);

        assertThat(leaf.comment().depth()).isEqualTo(4);
    }

    @Test
    void fsCmt03_replyBeyondDepth4IsValidation() {
        UUID depth4 = chain(publicPost.id(), 4);
        assertThatThrownBy(() -> service.create(blake.id(), publicPost.id(), "too deep", depth4))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException error = (AuthException) ex;
                    assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(error.details())
                            .extracting(issue -> issue.path())
                            .contains("parentId");
                });
    }

    @Test
    void fsCmt04_emptyAndTooLongBodyAreValidation() {
        assertThatThrownBy(() -> service.create(alex.id(), publicPost.id(), "  ", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
        assertThatThrownBy(() -> service.create(alex.id(), publicPost.id(), "x".repeat(1001), null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsCmt05_authorDeleteTombstonesBodyChildrenRemain() {
        VisibleComment parent = service.create(blake.id(), publicPost.id(), "keep secret", null);
        VisibleComment child = service.create(
                alex.id(), publicPost.id(), "child stays", parent.comment().id());

        service.delete(blake.id(), parent.comment().id());

        CommentList roots = service.listRoots(alex.id(), publicPost.id(), null, 20);
        assertThat(roots.data()).hasSize(1);
        VisibleComment tombstone = roots.data().getFirst();
        assertThat(tombstone.comment().tombstoned()).isTrue();
        assertThat(tombstone.comment().visibleBody()).isEqualTo(Comment.TOMBSTONE);
        assertThat(tombstone.comment().body()).isEqualTo(Comment.TOMBSTONE);

        CommentList replies = service.listReplies(alex.id(), parent.comment().id(), null, 20);
        assertThat(replies.data()).hasSize(1);
        assertThat(replies.data().getFirst().comment().body()).isEqualTo("child stays");
        assertThat(replies.data().getFirst().comment().id())
                .isEqualTo(child.comment().id());
    }

    @Test
    void fsCmt05_nonAuthorDeleteIsForbidden() {
        VisibleComment created = service.create(blake.id(), publicPost.id(), "mine", null);
        assertThatThrownBy(() -> service.delete(alex.id(), created.comment().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(service.listRoots(alex.id(), publicPost.id(), null, 20)
                        .data()
                        .getFirst()
                        .comment()
                        .deleted())
                .isFalse();
    }

    @Test
    void fsCmt06_pageRootsNewestFirstAndRepliesOnDemand() {
        VisibleComment older = service.create(blake.id(), publicPost.id(), "older root", null);
        clock.set(NOW.plus(Duration.ofMinutes(5)));
        VisibleComment newer = service.create(alex.id(), publicPost.id(), "newer root", null);
        service.create(casey.id(), publicPost.id(), "reply", newer.comment().id());

        CommentList roots = service.listRoots(alex.id(), publicPost.id(), null, 20);
        assertThat(roots.data())
                .extracting(row -> row.comment().id())
                .containsExactly(newer.comment().id(), older.comment().id());
        assertThat(roots.data().getFirst().replyCount()).isEqualTo(1);
        assertThat(roots.data().getFirst().comment().body()).isEqualTo("newer root");

        CommentList replies = service.listReplies(alex.id(), newer.comment().id(), null, 20);
        assertThat(replies.data()).extracting(row -> row.comment().body()).containsExactly("reply");
    }

    @Test
    void fsCmt06_rootPageSizeTwenty() {
        for (int i = 0; i < 21; i++) {
            service.create(alex.id(), publicPost.id(), "root " + i, null);
        }
        CommentList first = service.listRoots(alex.id(), publicPost.id(), null, 20);
        assertThat(first.data()).hasSize(20);
        assertThat(first.next()).isNotNull();
        CommentList second = service.listRoots(alex.id(), publicPost.id(), first.next(), 20);
        assertThat(second.data()).hasSize(1);
        assertThat(second.next()).isNull();
    }

    @Test
    void fsCmt07_likeUnlikeDoesNotDoubleCount() {
        VisibleComment created = service.create(blake.id(), publicPost.id(), "like me", null);
        service.like(alex.id(), created.comment().id());
        service.like(alex.id(), created.comment().id());
        assertThat(service.listRoots(alex.id(), publicPost.id(), null, 20)
                        .data()
                        .getFirst()
                        .likeCount())
                .isEqualTo(1);
        assertThat(service.listRoots(alex.id(), publicPost.id(), null, 20)
                        .data()
                        .getFirst()
                        .liked())
                .isTrue();

        service.unlike(alex.id(), created.comment().id());
        assertThat(service.listRoots(alex.id(), publicPost.id(), null, 20)
                        .data()
                        .getFirst()
                        .likeCount())
                .isZero();
    }

    @Test
    void fsCmt07_strangerLikeFriendsOnlyIsNotFound() {
        VisibleComment created = service.create(blake.id(), friendsPost.id(), "hidden like", null);
        assertThatThrownBy(() -> service.like(casey.id(), created.comment().id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void unknownParentIsNotFound() {
        assertThatThrownBy(() -> service.create(alex.id(), publicPost.id(), "orphan", UUID.randomUUID()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private UUID chain(UUID postId, int depth) {
        UUID parentId = null;
        UUID last = null;
        for (int i = 0; i <= depth; i++) {
            last = service.create(alex.id(), postId, "d" + i, parentId)
                    .comment()
                    .id();
            parentId = last;
        }
        return last;
    }

    private Post post(User author, PostVisibility visibility, String body) {
        Post row = new Post(UUID.randomUUID(), author.id(), body, visibility, NOW, null, null, null, null);
        posts.save(row, List.of());
        return row;
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

    private static final class InMemoryPosts implements PostRepository {
        private final Map<UUID, Post> store = new LinkedHashMap<>();

        @Override
        public void save(Post post, List<UUID> mediaIds) {
            store.put(post.id(), post);
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
            return Optional.empty();
        }

        @Override
        public List<UUID> mediaIds(UUID postId) {
            return List.of();
        }

        @Override
        public boolean insertLike(UUID userId, UUID postId, Instant at) {
            return false;
        }

        @Override
        public boolean deleteLike(UUID userId, UUID postId) {
            return false;
        }

        @Override
        public boolean liked(UUID userId, UUID postId) {
            return false;
        }

        @Override
        public long likeCount(UUID postId) {
            return 0;
        }

        @Override
        public List<fr.projetcompensation.gymbuddy.posts.LikeRow> listLikes(
                UUID postId, InstantIdCursor after, int limit) {
            return List.of();
        }

        @Override
        public boolean insertRepost(UUID userId, UUID postId, Instant at) {
            return false;
        }

        @Override
        public boolean deleteRepost(UUID userId, UUID postId) {
            return false;
        }

        @Override
        public boolean reposted(UUID userId, UUID postId) {
            return false;
        }

        @Override
        public long repostCount(UUID postId) {
            return 0;
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

    private static final class InMemoryComments implements CommentRepository {
        private final Map<UUID, Comment> store = new LinkedHashMap<>();
        private final Map<UUID, Set<UUID>> likes = new HashMap<>();

        @Override
        public void save(Comment comment) {
            store.put(comment.id(), comment);
        }

        @Override
        public void update(Comment comment) {
            store.put(comment.id(), comment);
        }

        @Override
        public Optional<Comment> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Comment> listRoots(UUID postId, InstantIdCursor before, int limit) {
            return store.values().stream()
                    .filter(row -> row.postId().equals(postId) && row.parentId() == null)
                    .sorted(Comparator.comparing(Comment::createdAt)
                            .thenComparing(Comment::id)
                            .reversed())
                    .filter(row -> before == null
                            || row.createdAt().isBefore(before.at())
                            || (row.createdAt().equals(before.at()) && row.id().compareTo(before.id()) < 0))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Comment> listReplies(UUID parentId, InstantIdCursor before, int limit) {
            return store.values().stream()
                    .filter(row -> parentId.equals(row.parentId()))
                    .sorted(Comparator.comparing(Comment::createdAt).thenComparing(Comment::id))
                    .filter(row -> before == null
                            || row.createdAt().isAfter(before.at())
                            || (row.createdAt().equals(before.at()) && row.id().compareTo(before.id()) > 0))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long replyCount(UUID parentId) {
            return store.values().stream()
                    .filter(row -> parentId.equals(row.parentId()))
                    .count();
        }

        @Override
        public boolean insertLike(UUID userId, UUID commentId, Instant at) {
            return likes.computeIfAbsent(commentId, id -> new HashSet<>()).add(userId);
        }

        @Override
        public boolean deleteLike(UUID userId, UUID commentId) {
            Set<UUID> byComment = likes.get(commentId);
            return byComment != null && byComment.remove(userId);
        }

        @Override
        public boolean liked(UUID userId, UUID commentId) {
            return likes.getOrDefault(commentId, Set.of()).contains(userId);
        }

        @Override
        public long likeCount(UUID commentId) {
            return likes.getOrDefault(commentId, Set.of()).size();
        }
    }
}
