package fr.projetcompensation.gymbuddy.messaging;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaKind;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.media.MediaStatus;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MessagingService {

    private static final String NOT_FOUND = "conversation not found";
    private static final String FORBIDDEN_SEND = "not friends";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final MediaRepository media;
    private final MessagingGateway gateway;
    private final Clock clock;

    public MessagingService(
            ConversationRepository conversations,
            MessageRepository messages,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            MediaRepository media,
            MessagingGateway gateway,
            Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
        this.media = media;
        this.gateway = gateway == null ? MessagingGateway.noop() : gateway;
        this.clock = clock;
    }

    public ListedConversation open(UUID callerId, UUID peerId) {
        User caller = requireActive(callerId);
        if (peerId == null) {
            throw AuthException.validation("userId is required", new FieldIssue("userId", "required"));
        }
        if (peerId.equals(caller.id())) {
            throw AuthException.validation("cannot message yourself", new FieldIssue("userId", "self"));
        }
        User peer = users.findById(peerId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!peer.active()) {
            throw AuthException.notFound(NOT_FOUND);
        }
        requireCanSend(caller.id(), peer.id());
        Conversation existing = conversations.findPair(caller.id(), peer.id()).orElse(null);
        if (existing != null) {
            return listed(existing, caller.id());
        }
        Instant now = clock.instant();
        Conversation created = new Conversation(
                UUID.randomUUID(),
                Conversation.lo(caller.id(), peer.id()),
                Conversation.hi(caller.id(), peer.id()),
                now);
        conversations.save(created);
        return listed(created, caller.id());
    }

    public ConversationList listInbox(UUID callerId, String before, Integer size) {
        User caller = requireActive(callerId);
        int pageSize = pageSize(size);
        InstantIdCursor cursor = InstantIdCursor.parse(before).orElse(null);
        List<InboxRow> rows = conversations.listInbox(caller.id(), cursor, pageSize + 1);
        String next = null;
        if (rows.size() > pageSize) {
            InboxRow last = rows.get(pageSize - 1);
            next = new InstantIdCursor(last.updatedAt(), last.conversation().id()).encode();
            rows = rows.subList(0, pageSize);
        }
        List<ListedConversation> data = new ArrayList<>();
        for (InboxRow row : rows) {
            data.add(listed(row, caller.id()));
        }
        return new ConversationList(data, next, pageSize);
    }

    public MessageList listMessages(UUID callerId, UUID conversationId, String before, Integer size) {
        User caller = requireActive(callerId);
        Conversation conversation = requireMember(caller.id(), conversationId);
        int pageSize = pageSize(size);
        InstantIdCursor cursor = InstantIdCursor.parse(before).orElse(null);
        List<Message> rows = messages.list(conversation.id(), cursor, pageSize + 1);
        conversations.markRead(conversation.id(), caller.id(), clock.instant());
        String next = null;
        if (rows.size() > pageSize) {
            Message last = rows.get(pageSize - 1);
            next = new InstantIdCursor(last.createdAt(), last.id()).encode();
            rows = rows.subList(0, pageSize);
        }
        return new MessageList(List.copyOf(rows), next, pageSize);
    }

    public Message send(UUID callerId, UUID conversationId, String typeWire, String body, UUID mediaId) {
        User caller = requireActive(callerId);
        Conversation conversation = requireMember(caller.id(), conversationId);
        UUID peerId = conversation.other(caller.id());
        requireCanSend(caller.id(), peerId);
        MessageType type;
        try {
            type = MessageType.fromWire(typeWire);
        } catch (RuntimeException ex) {
            throw AuthException.validation("type is not allowed", new FieldIssue("type", "enum"));
        }
        Instant now = clock.instant();
        String normalizedBody;
        UUID attached;
        if (type == MessageType.TEXT) {
            normalizedBody = requireText(body);
            if (mediaId != null) {
                throw AuthException.validation(
                        "text messages cannot attach media", new FieldIssue("mediaId", "forbidden"));
            }
            attached = null;
        } else {
            attached = requireMedia(caller.id(), type, mediaId);
            normalizedBody = optionalCaption(body);
        }
        Message row = new Message(
                UUID.randomUUID(), conversation.id(), caller.id(), type, normalizedBody, attached, now, null);
        messages.save(row);
        ListedConversation updated = listed(conversation, caller.id());
        fanOutCreated(caller.id(), peerId, row, updated);
        return row;
    }

    public void delete(UUID callerId, UUID messageId) {
        User caller = requireActive(callerId);
        Message row = messages.findById(messageId).orElseThrow(() -> AuthException.notFound("message not found"));
        requireMember(caller.id(), row.conversationId());
        if (!row.senderId().equals(caller.id())) {
            throw AuthException.forbidden("cannot delete this message");
        }
        if (row.deleted()) {
            return;
        }
        Instant now = clock.instant();
        if (now.isAfter(row.createdAt().plus(Message.DELETE_WINDOW))) {
            throw AuthException.forbidden("cannot delete this message");
        }
        Message tombstone = row.tombstone(now);
        messages.update(tombstone);
        Conversation conversation = conversations.findById(row.conversationId()).orElseThrow();
        UUID peerId = conversation.other(caller.id());
        ListedConversation updated = listed(conversation, caller.id());
        fanOutDeleted(caller.id(), peerId, tombstone, updated);
    }

    private void fanOutCreated(UUID callerId, UUID peerId, Message message, ListedConversation updated) {
        try {
            gateway.messageCreated(callerId, message);
            gateway.messageCreated(peerId, message);
            gateway.conversationUpdated(callerId, updated);
            gateway.conversationUpdated(peerId, listed(updated.conversation(), peerId));
        } catch (RuntimeException ignored) {
            // Persistence first: HTTP success does not depend on the socket.
        }
    }

    private void fanOutDeleted(UUID callerId, UUID peerId, Message message, ListedConversation updated) {
        try {
            gateway.messageDeleted(callerId, message.conversationId(), message.id());
            gateway.messageDeleted(peerId, message.conversationId(), message.id());
            gateway.conversationUpdated(callerId, updated);
            gateway.conversationUpdated(peerId, listed(updated.conversation(), peerId));
        } catch (RuntimeException ignored) {
            // Persistence first: HTTP success does not depend on the socket.
        }
    }

    private void requireCanSend(UUID callerId, UUID peerId) {
        if (friendships.isBlockedEitherWay(callerId, peerId) || !friendships.areAcceptedFriends(callerId, peerId)) {
            throw AuthException.forbidden(FORBIDDEN_SEND);
        }
    }

    private Conversation requireMember(UUID callerId, UUID conversationId) {
        Conversation conversation =
                conversations.findById(conversationId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!conversation.involves(callerId)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        return conversation;
    }

    private ListedConversation listed(Conversation conversation, UUID callerId) {
        InboxRow row = conversations.inboxRow(conversation.id(), callerId).orElse(new InboxRow(conversation, null, 0));
        return listed(row, callerId);
    }

    private ListedConversation listed(InboxRow row, UUID callerId) {
        UUID peerId = row.conversation().other(callerId);
        User peer = users.findById(peerId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Profile profile = profiles.findByUserId(peerId).orElse(Profile.created(peerId, peer.handle()));
        return new ListedConversation(
                row.conversation(), peer, profile, row.lastMessage(), row.unreadCount(), row.updatedAt());
    }

    private UUID requireMedia(UUID ownerId, MessageType type, UUID mediaId) {
        if (mediaId == null) {
            throw AuthException.validation("mediaId is required", new FieldIssue("mediaId", "required"));
        }
        Media row = media.findById(mediaId)
                .orElseThrow(
                        () -> AuthException.validation("media is not allowed", new FieldIssue("mediaId", "invalid")));
        if (!row.ownerId().equals(ownerId)
                || row.kind() != MediaKind.MESSAGE
                || row.status() != MediaStatus.READY
                || row.deletedAt() != null
                || row.mime() == null) {
            throw AuthException.validation("media is not allowed", new FieldIssue("mediaId", "invalid"));
        }
        boolean audio = row.mime().startsWith("audio/");
        boolean image = row.mime().startsWith("image/");
        if (type == MessageType.IMAGE && !image) {
            throw AuthException.validation("media is not allowed", new FieldIssue("mediaId", "pairing"));
        }
        if (type == MessageType.AUDIO && !audio) {
            throw AuthException.validation("media is not allowed", new FieldIssue("mediaId", "pairing"));
        }
        if (messages.findByMediaId(mediaId).isPresent()) {
            throw AuthException.validation("media is not allowed", new FieldIssue("mediaId", "attached"));
        }
        return mediaId;
    }

    private static String requireText(String body) {
        if (body == null || body.isBlank()) {
            throw AuthException.validation("body is required", new FieldIssue("body", "required"));
        }
        if (body.length() > Message.MAX_BODY) {
            throw AuthException.validation("body is too long", new FieldIssue("body", "max"));
        }
        return body;
    }

    private static String optionalCaption(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        if (body.length() > Message.MAX_BODY) {
            throw AuthException.validation("body is too long", new FieldIssue("body", "max"));
        }
        return body;
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
