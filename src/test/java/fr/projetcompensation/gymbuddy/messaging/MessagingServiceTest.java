package fr.projetcompensation.gymbuddy.messaging;

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
import java.time.Instant;
import java.time.ZoneId;
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

class MessagingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private InMemoryUsers users;
    private InMemoryProfiles profiles;
    private InMemoryFriendships friendships;
    private InMemoryConversations conversations;
    private InMemoryMessages messages;
    private InMemoryMedia media;
    private RecordingGateway gateway;
    private MutableClock clock;
    private MessagingService service;
    private User alex;
    private User blake;
    private User casey;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        profiles = new InMemoryProfiles();
        friendships = new InMemoryFriendships();
        messages = new InMemoryMessages();
        conversations = new InMemoryConversations(messages);
        media = new InMemoryMedia();
        gateway = new RecordingGateway();
        clock = new MutableClock(NOW);
        service = new MessagingService(conversations, messages, friendships, users, profiles, media, gateway, clock);
        alex = member("alex");
        blake = member("blake");
        casey = member("casey");
        friendships.accept(alex.id(), blake.id());
    }

    @Test
    void fsMsg01_friendsOpenADirectConversation() {
        ListedConversation opened = service.open(alex.id(), blake.id());
        assertThat(opened.peer().handle()).isEqualTo("blake");
        ListedConversation again = service.open(blake.id(), alex.id());
        assertThat(again.conversation().id()).isEqualTo(opened.conversation().id());
    }

    @Test
    void fsMsg02_nonFriendsCannotOpenOrSend() {
        assertThatThrownBy(() -> service.open(alex.id(), casey.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void fsMsg03_textMustBeOneTo4000Characters() {
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        assertThatThrownBy(() -> service.send(alex.id(), conversationId, "text", "", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
        assertThatThrownBy(() -> service.send(alex.id(), conversationId, "text", "x".repeat(4001), null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.VALIDATION));
        Message sent = service.send(alex.id(), conversationId, "text", "Ready?", null);
        assertThat(sent.body()).isEqualTo("Ready?");
        assertThat(sent.type()).isEqualTo(MessageType.TEXT);
    }

    @Test
    void fsMsg04And05_imageAndAudioAttachReadyMessageMedia() {
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        Media image = readyMedia(alex, "image/jpeg");
        Media audio = readyMedia(alex, "audio/webm");
        Message picture = service.send(alex.id(), conversationId, "image", "spot me", image.id());
        Message clip = service.send(alex.id(), conversationId, "audio", null, audio.id());
        assertThat(picture.mediaId()).isEqualTo(image.id());
        assertThat(clip.type()).isEqualTo(MessageType.AUDIO);
        assertThat(clip.mediaId()).isEqualTo(audio.id());
    }

    @Test
    void fsMsg06_strangerConversationIsNotFound() {
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        service.send(alex.id(), conversationId, "text", "secret", null);
        assertThatThrownBy(() -> service.listMessages(casey.id(), conversationId, null, 20))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.send(casey.id(), conversationId, "text", "hi", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void fsMsg06_participantCanReadAttachedMedia() {
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        Media image = readyMedia(alex, "image/jpeg");
        service.send(alex.id(), conversationId, "image", null, image.id());
        MessageAttachedMediaAccess access = new MessageAttachedMediaAccess(messages, conversations);
        assertThat(access.canRead(blake.id(), image)).isTrue();
        assertThat(access.canRead(alex.id(), image)).isTrue();
        assertThat(access.canRead(casey.id(), image)).isFalse();
    }

    @Test
    void fsMsg07_messagePersistsWhenGatewayIsDown() {
        gateway.fail = true;
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        Message sent = service.send(alex.id(), conversationId, "text", "still here", null);
        assertThat(messages.findById(sent.id())).isPresent();
        assertThat(service.listMessages(blake.id(), conversationId, null, 20).data())
                .extracting(Message::body)
                .containsExactly("still here");
    }

    @Test
    void fsMsg08_senderCanTombstoneWithinTenMinutes() {
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        Message sent = service.send(alex.id(), conversationId, "text", "oops", null);
        service.delete(alex.id(), sent.id());
        Message tombstone = messages.findById(sent.id()).orElseThrow();
        assertThat(tombstone.deleted()).isTrue();
        assertThat(tombstone.visibleBody()).isEqualTo(Message.TOMBSTONE);
        assertThatThrownBy(() -> service.delete(blake.id(), sent.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        Message later = service.send(alex.id(), conversationId, "text", "too late", null);
        clock.set(NOW.plus(Message.DELETE_WINDOW).plusSeconds(1));
        assertThatThrownBy(() -> service.delete(alex.id(), later.id()))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void fsMsg09_inboxOrdersByLastMessageAndTracksUnread() {
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        service.send(alex.id(), conversationId, "text", "first", null);
        ConversationList unread = service.listInbox(blake.id(), null, 20);
        assertThat(unread.data()).hasSize(1);
        assertThat(unread.data().getFirst().unreadCount()).isEqualTo(1);
        assertThat(unread.data().getFirst().lastMessage().body()).isEqualTo("first");
        service.listMessages(blake.id(), conversationId, null, 20);
        assertThat(service.listInbox(blake.id(), null, 20).data().getFirst().unreadCount())
                .isZero();
    }

    @Test
    void fsMsg10_blockStopsSendButHistoryRemains() {
        UUID conversationId = service.open(alex.id(), blake.id()).conversation().id();
        service.send(alex.id(), conversationId, "text", "before block", null);
        friendships.block(blake.id(), alex.id());
        assertThatThrownBy(() -> service.send(alex.id(), conversationId, "text", "nope", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.send(blake.id(), conversationId, "text", "nope", null))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(service.listMessages(alex.id(), conversationId, null, 20).data())
                .extracting(Message::body)
                .containsExactly("before block");
        assertThat(service.listMessages(blake.id(), conversationId, null, 20).data())
                .extracting(Message::body)
                .containsExactly("before block");
    }

    private Media readyMedia(User owner, String mime) {
        Media row = new Media(
                UUID.randomUUID(),
                owner.id(),
                MediaKind.MESSAGE,
                mime,
                128,
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

    private static final class RecordingGateway implements MessagingGateway {
        private boolean fail;
        private final List<String> events = new ArrayList<>();

        @Override
        public void messageCreated(UUID recipientId, Message message) {
            record("created", recipientId);
        }

        @Override
        public void messageDeleted(UUID recipientId, UUID conversationId, UUID messageId) {
            record("deleted", recipientId);
        }

        @Override
        public void conversationUpdated(UUID recipientId, ListedConversation conversation) {
            record("updated", recipientId);
        }

        private void record(String event, UUID recipientId) {
            if (fail) {
                fail = false;
                throw new RuntimeException("socket down");
            }
            events.add(event + ":" + recipientId);
        }
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

        void block(UUID blocker, UUID target) {
            Optional<Friendship> existing = findPair(blocker, target);
            if (existing.isPresent()) {
                update(existing.get().asBlock(blocker, target, NOW));
                return;
            }
            save(new Friendship(UUID.randomUUID(), blocker, target, FriendshipStatus.BLOCKED, NOW, NOW));
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

    private static final class InMemoryMessages implements MessageRepository {
        private final Map<UUID, Message> store = new LinkedHashMap<>();

        @Override
        public void save(Message message) {
            store.put(message.id(), message);
        }

        @Override
        public void update(Message message) {
            store.put(message.id(), message);
        }

        @Override
        public Optional<Message> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Message> findByMediaId(UUID mediaId) {
            return store.values().stream()
                    .filter(row -> mediaId.equals(row.mediaId()))
                    .findFirst();
        }

        @Override
        public List<Message> list(UUID conversationId, InstantIdCursor before, int limit) {
            return store.values().stream()
                    .filter(row -> row.conversationId().equals(conversationId))
                    .sorted(Comparator.comparing(Message::createdAt)
                            .thenComparing(Message::id)
                            .reversed())
                    .limit(limit)
                    .toList();
        }
    }

    private static final class InMemoryConversations implements ConversationRepository {
        private final Map<UUID, Conversation> store = new LinkedHashMap<>();
        private final Map<String, Instant> reads = new LinkedHashMap<>();
        private final InMemoryMessages messages;

        private InMemoryConversations(InMemoryMessages messages) {
            this.messages = messages;
        }

        @Override
        public void save(Conversation conversation) {
            store.put(conversation.id(), conversation);
        }

        @Override
        public Optional<Conversation> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Conversation> findPair(UUID left, UUID right) {
            UUID lo = Conversation.lo(left, right);
            UUID hi = Conversation.hi(left, right);
            return store.values().stream()
                    .filter(row -> row.userLo().equals(lo) && row.userHi().equals(hi))
                    .findFirst();
        }

        @Override
        public List<InboxRow> listInbox(UUID userId, InstantIdCursor before, int limit) {
            return store.values().stream()
                    .filter(row -> row.involves(userId))
                    .map(row -> toInbox(row, userId))
                    .sorted(Comparator.comparing(InboxRow::updatedAt)
                            .thenComparing(row -> row.conversation().id())
                            .reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<InboxRow> inboxRow(UUID conversationId, UUID userId) {
            return findById(conversationId).filter(row -> row.involves(userId)).map(row -> toInbox(row, userId));
        }

        @Override
        public void markRead(UUID conversationId, UUID userId, Instant at) {
            reads.put(conversationId + ":" + userId, at);
        }

        private InboxRow toInbox(Conversation conversation, UUID userId) {
            List<Message> rows = messages.store.values().stream()
                    .filter(row -> row.conversationId().equals(conversation.id()))
                    .sorted(Comparator.comparing(Message::createdAt)
                            .thenComparing(Message::id)
                            .reversed())
                    .toList();
            Message last = rows.isEmpty() ? null : rows.getFirst();
            Instant lastRead = reads.getOrDefault(conversation.id() + ":" + userId, Instant.EPOCH);
            long unread = rows.stream()
                    .filter(row -> !row.senderId().equals(userId)
                            && !row.deleted()
                            && row.createdAt().isAfter(lastRead))
                    .count();
            return new InboxRow(conversation, last, unread);
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
}
