package fr.projetcompensation.gymbuddy.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.comments.Comment;
import fr.projetcompensation.gymbuddy.comments.CommentRepository;
import fr.projetcompensation.gymbuddy.events.Event;
import fr.projetcompensation.gymbuddy.events.EventApplication;
import fr.projetcompensation.gymbuddy.events.EventOccurrence;
import fr.projetcompensation.gymbuddy.events.EventRepository;
import fr.projetcompensation.gymbuddy.fixtures.FixtureGenerator;
import fr.projetcompensation.gymbuddy.fixtures.FixtureMagnitude;
import fr.projetcompensation.gymbuddy.fixtures.FixtureReport;
import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.posts.PostService;
import fr.projetcompensation.gymbuddy.posts.PostVisibility;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T16:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryPosts posts;
    private InMemoryComments comments;
    private InMemoryEvents events;
    private InMemoryMedia media;
    private InMemoryFriendships friendships;
    private InMemoryReports reports;
    private InMemoryAudit audit;
    private InMemoryCatalog catalog;
    private RecordingFixtures fixtures;
    private AdminService admin;
    private PostService postService;
    private User owner;
    private User member;
    private User moderator;
    private User administrator;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        posts = new InMemoryPosts();
        comments = new InMemoryComments();
        events = new InMemoryEvents();
        media = new InMemoryMedia();
        friendships = new InMemoryFriendships();
        reports = new InMemoryReports();
        audit = new InMemoryAudit();
        catalog = new InMemoryCatalog();
        fixtures = new RecordingFixtures();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        admin = new AdminService(
                users,
                profiles,
                posts,
                comments,
                events,
                media,
                friendships,
                reports,
                audit,
                catalog,
                clock,
                false,
                fixtures);
        postService = new PostService(posts, media, friendships, users, profiles, clock);
        owner = user("owner", UserRole.MEMBER);
        member = user("member", UserRole.MEMBER);
        moderator = user("mod", UserRole.MODERATOR);
        administrator = user("admin", UserRole.ADMIN);
    }

    @Test
    void listContentMemberIsNotFound() {
        assertThatThrownBy(() -> admin.listContent(member.id(), "post", null, null, null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(admin.listContent(moderator.id(), "post", null, null, null, 20)
                        .data())
                .isEmpty();
    }

    @Test
    void fsAdm02_moderatorRolePatchIsForbidden() {
        assertThatThrownBy(() -> admin.changeRole(moderator.id(), member.id(), "moderator", "promo"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(users.findById(member.id()).orElseThrow().role()).isEqualTo(UserRole.MEMBER);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void fsAdm03_hidePostMemberNotFoundAndAuditRow() {
        Post post = new Post(
                UUID.randomUUID(),
                owner.id(),
                "spam shake",
                PostVisibility.PUBLIC,
                NOW.minusSeconds(60),
                null,
                null,
                null,
                null);
        posts.save(post, List.of());

        admin.hide(moderator.id(), "post", post.id(), "spam / solicitation");

        assertThatThrownBy(() -> postService.get(member.id(), post.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(posts.findById(post.id()).orElseThrow().hidden()).isTrue();
        assertThat(audit.events).anySatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditEvent.HIDE_CONTENT);
            assertThat(event.targetId()).isEqualTo(post.id());
            assertThat(event.actorId()).isEqualTo(moderator.id());
            assertThat(event.reason()).isEqualTo("spam / solicitation");
        });
    }

    @Test
    void fsAcct09_lastAdminDemoteIsConflict() {
        assertThatThrownBy(() -> admin.changeRole(administrator.id(), administrator.id(), "member", "step down"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(users.findById(administrator.id()).orElseThrow().role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void fsAdm_memberAdminUsersIsNotFound() {
        assertThatThrownBy(() -> admin.listUsers(member.id(), null, null, null, null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsAdm05_prodFixturesAreForbidden() {
        AdminService prod = new AdminService(
                users,
                profiles,
                posts,
                comments,
                events,
                media,
                friendships,
                reports,
                audit,
                catalog,
                Clock.fixed(NOW, ZoneOffset.UTC),
                true,
                fixtures);
        assertThatThrownBy(() -> prod.generateFixtures(administrator.id(), FixtureMagnitude.tiny()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> prod.resetFixtures(administrator.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(audit.events).isEmpty();
        assertThat(fixtures.generated).isEmpty();
        assertThat(fixtures.resets).isEmpty();
    }

    @Test
    void fsAdm03_missingHideReasonIsValidation() {
        Post post = new Post(UUID.randomUUID(), owner.id(), "body", PostVisibility.PUBLIC, NOW, null, null, null, null);
        posts.save(post, List.of());
        assertThatThrownBy(() -> admin.hide(administrator.id(), "post", post.id(), "  "))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void fsAdm03_hideEventIsNotFound() {
        assertThatThrownBy(() -> admin.hide(administrator.id(), "event", UUID.randomUUID(), "policy"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsAcct08_staffCanLockMember() {
        ListedAdminUser locked = admin.lock(moderator.id(), member.id(), "policy abuse");
        assertThat(locked.user().status()).isEqualTo(UserStatus.LOCKED);
        assertThat(audit.events).extracting(AuditEvent::action).contains(AuditEvent.LOCK_USER);
    }

    @Test
    void fsAdm07_memberCanReportVisiblePost() {
        Post post = new Post(UUID.randomUUID(), owner.id(), "body", PostVisibility.PUBLIC, NOW, null, null, null, null);
        posts.save(post, List.of());
        Report created = admin.createReport(member.id(), "post", post.id(), "harassment");
        assertThat(created.status()).isEqualTo(Report.OPEN);
        assertThatThrownBy(() -> admin.createReport(member.id(), "post", post.id(), "again"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void fsAdm05_nonProdFixturesWriteAuditAndGenerate() {
        admin.generateFixtures(administrator.id(), FixtureMagnitude.tiny());
        assertThat(fixtures.generated).containsExactly(FixtureMagnitude.tiny());
        assertThat(audit.events)
                .anySatisfy(event -> assertThat(event.action()).isEqualTo(AuditEvent.GENERATE_FIXTURES));
    }

    private static final class RecordingFixtures implements FixtureGenerator {
        private final List<FixtureMagnitude> generated = new ArrayList<>();
        private final List<UUID> resets = new ArrayList<>();

        @Override
        public FixtureReport generate(FixtureMagnitude magnitude) {
            generated.add(magnitude);
            return new FixtureReport(magnitude.users(), 0, 0, 0, 0, 0, 0, 0, 0);
        }

        @Override
        public void reset(UUID preserveUserId) {
            resets.add(preserveUserId);
        }
    }

    private User user(String handle, UserRole role) {
        User row = new User(
                UUID.randomUUID(),
                handle + "@gym.test",
                handle,
                "hash",
                role,
                UserStatus.ACTIVE,
                NOW.minusSeconds(handle.length()));
        users.save(row);
        profiles.save(Profile.created(row.id(), handle));
        catalog.users.add(row);
        return row;
    }

    private final class InMemoryCatalog implements AdminCatalog {
        private final List<User> users = new ArrayList<>();

        @Override
        public List<ListedAdminUser> listUsers(String q, String role, String status, InstantIdCursor after, int limit) {
            return users.stream()
                    .sorted(Comparator.comparing(User::createdAt).reversed().thenComparing(User::id))
                    .map(user -> new ListedAdminUser(
                            user, user.handle(), user.role() == UserRole.ADMIN && countAdmins() <= 1))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countAdmins() {
            return users.stream().filter(user -> user.role() == UserRole.ADMIN).count();
        }

        @Override
        public List<ListedAdminMedia> listMedia(String q, InstantIdCursor after, int limit) {
            return List.of();
        }

        @Override
        public List<ListedAdminContent> listContent(
                String type, String q, Boolean hidden, InstantIdCursor after, int limit) {
            return List.of();
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<UUID, User> store = new HashMap<>();

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
        private final Map<UUID, Profile> store = new HashMap<>();

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

    private static final class InMemoryPosts implements PostRepository {
        private final Map<UUID, Post> store = new HashMap<>();

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
            return true;
        }

        @Override
        public boolean deleteLike(UUID userId, UUID postId) {
            return true;
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
            return true;
        }

        @Override
        public boolean deleteRepost(UUID userId, UUID postId) {
            return true;
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
        private final Map<UUID, Comment> store = new HashMap<>();

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
            return List.of();
        }

        @Override
        public List<Comment> listReplies(UUID parentId, InstantIdCursor before, int limit) {
            return List.of();
        }

        @Override
        public long replyCount(UUID parentId) {
            return 0;
        }

        @Override
        public boolean insertLike(UUID userId, UUID commentId, Instant at) {
            return true;
        }

        @Override
        public boolean deleteLike(UUID userId, UUID commentId) {
            return true;
        }

        @Override
        public boolean liked(UUID userId, UUID commentId) {
            return false;
        }

        @Override
        public long likeCount(UUID commentId) {
            return 0;
        }
    }

    private static final class InMemoryEvents implements EventRepository {
        @Override
        public void save(Event event, List<EventOccurrence> occurrences, List<UUID> inviteeIds) {}

        @Override
        public void update(Event event) {}

        @Override
        public void replaceInvitees(UUID eventId, List<UUID> inviteeIds) {}

        @Override
        public void saveOccurrences(List<EventOccurrence> occurrences) {}

        @Override
        public void updateOccurrence(EventOccurrence occurrence) {}

        @Override
        public Optional<Event> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<Event> findByCoverMediaId(UUID mediaId) {
            return Optional.empty();
        }

        @Override
        public List<EventOccurrence> occurrences(UUID eventId) {
            return List.of();
        }

        @Override
        public Optional<EventOccurrence> findOccurrence(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<EventOccurrence> lockOccurrence(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<UUID> inviteeIds(UUID eventId) {
            return List.of();
        }

        @Override
        public boolean isInvitee(UUID eventId, UUID userId) {
            return false;
        }

        @Override
        public void saveApplication(EventApplication application) {}

        @Override
        public void updateApplication(EventApplication application) {}

        @Override
        public Optional<EventApplication> findApplication(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<EventApplication> findApplication(UUID occurrenceId, UUID applicantId) {
            return Optional.empty();
        }

        @Override
        public List<EventApplication> applicationsForEvent(UUID eventId) {
            return List.of();
        }

        @Override
        public List<EventApplication> pendingForOccurrence(UUID occurrenceId) {
            return List.of();
        }

        @Override
        public boolean hasAccepted(UUID eventId, UUID userId) {
            return false;
        }

        @Override
        public int countAccepted(UUID occurrenceId) {
            return 0;
        }

        @Override
        public int countAcceptedCoAttendance(UUID organizerId, UUID applicantId) {
            return 0;
        }

        @Override
        public List<Event> listVisible(
                UUID viewerId, String kind, Instant from, Instant until, InstantIdCursor after, int limit) {
            return List.of();
        }
    }

    private static final class InMemoryMedia implements MediaRepository {
        private final Map<UUID, Media> store = new HashMap<>();

        @Override
        public void save(Media media) {
            store.put(media.id(), media);
        }

        @Override
        public void update(Media media) {
            store.put(media.id(), media);
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

    private static final class InMemoryFriendships implements FriendshipRepository {
        @Override
        public void save(Friendship friendship) {}

        @Override
        public void update(Friendship friendship) {}

        @Override
        public void delete(UUID id) {}

        @Override
        public Optional<Friendship> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<Friendship> findPair(UUID left, UUID right) {
            return Optional.empty();
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
            return false;
        }

        @Override
        public int acceptedCount(UUID userId) {
            return 0;
        }

        @Override
        public boolean isBlockedEitherWay(UUID left, UUID right) {
            return false;
        }
    }

    private static final class InMemoryReports implements ReportRepository {
        private final Map<UUID, Report> store = new HashMap<>();

        @Override
        public void save(Report report) {
            store.put(report.id(), report);
        }

        @Override
        public void update(Report report) {
            store.put(report.id(), report);
        }

        @Override
        public Optional<Report> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Report> findOpen(UUID reporterId, String targetType, UUID targetId) {
            return store.values().stream()
                    .filter(row -> row.open()
                            && row.reporterId().equals(reporterId)
                            && row.targetType().equals(targetType)
                            && row.targetId().equals(targetId))
                    .findFirst();
        }

        @Override
        public List<Report> list(String status, String q, InstantIdCursor after, int limit) {
            return store.values().stream()
                    .filter(row -> row.status().equals(status))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class InMemoryAudit implements AuditEventRepository {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void save(AuditEvent event) {
            events.add(event);
        }

        @Override
        public List<AuditEvent> list(
                UUID actorId, boolean contentOnly, String q, String action, InstantIdCursor after, int limit) {
            return events.stream().limit(limit).toList();
        }
    }
}
