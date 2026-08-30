package fr.projetcompensation.gymbuddy.friends;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FriendshipService {

    private static final String NOT_FOUND = "friendship not found";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final Clock clock;

    public FriendshipService(
            FriendshipRepository friendships, UserRepository users, ProfileRepository profiles, Clock clock) {
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    public ListedFriendship request(UUID callerId, String handle, UUID userId) {
        User caller = requireActive(callerId);
        if ((handle == null || handle.isBlank()) && userId == null) {
            throw AuthException.validation("handle or userId is required", new FieldIssue("handle", "required"));
        }
        User target = resolveTarget(handle, userId);
        if (target.id().equals(caller.id())) {
            throw AuthException.validation("cannot friend yourself", new FieldIssue("handle", "self"));
        }
        if (!target.active()) {
            throw AuthException.notFound(NOT_FOUND);
        }
        if (friendships.isBlockedEitherWay(caller.id(), target.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Optional<Friendship> existing = friendships.findPair(caller.id(), target.id());
        if (existing.isPresent()) {
            Friendship row = existing.get();
            if (row.status() == FriendshipStatus.PENDING || row.status() == FriendshipStatus.ACCEPTED) {
                throw AuthException.conflict("friendship already exists", new FieldIssue("handle", "duplicate"));
            }
            Instant now = clock.instant();
            Friendship revived =
                    new Friendship(row.id(), caller.id(), target.id(), FriendshipStatus.PENDING, now, null);
            friendships.update(revived);
            return listed(revived, caller.id());
        }
        Friendship created = new Friendship(
                UUID.randomUUID(), caller.id(), target.id(), FriendshipStatus.PENDING, clock.instant(), null);
        friendships.save(created);
        return listed(created, caller.id());
    }

    public ListedFriendship accept(UUID callerId, UUID friendshipId) {
        requireActive(callerId);
        Friendship row = friendships.findById(friendshipId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.status() != FriendshipStatus.PENDING || !row.addresseeId().equals(callerId)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Friendship accepted = row.withStatus(FriendshipStatus.ACCEPTED, clock.instant());
        friendships.update(accepted);
        return listed(accepted, callerId);
    }

    public void decline(UUID callerId, UUID friendshipId) {
        requireActive(callerId);
        Friendship row = friendships.findById(friendshipId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.status() != FriendshipStatus.PENDING || !row.addresseeId().equals(callerId)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        friendships.update(row.withStatus(FriendshipStatus.DECLINED, clock.instant()));
    }

    public void remove(UUID callerId, UUID friendshipId) {
        requireActive(callerId);
        Friendship row = friendships.findById(friendshipId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.status() == FriendshipStatus.PENDING && row.requesterId().equals(callerId)) {
            friendships.delete(row.id());
            return;
        }
        if (row.status() == FriendshipStatus.ACCEPTED && row.involves(callerId)) {
            friendships.delete(row.id());
            return;
        }
        throw AuthException.notFound(NOT_FOUND);
    }

    public void block(UUID callerId, UUID targetId) {
        User caller = requireActive(callerId);
        if (targetId == null) {
            throw AuthException.validation("userId is required", new FieldIssue("userId", "required"));
        }
        if (targetId.equals(caller.id())) {
            throw AuthException.validation("cannot block yourself", new FieldIssue("userId", "self"));
        }
        User target = users.findById(targetId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (target.closed()) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Instant now = clock.instant();
        Optional<Friendship> existing = friendships.findPair(caller.id(), target.id());
        if (existing.isPresent()) {
            friendships.update(existing.get().asBlock(caller.id(), target.id(), now));
            return;
        }
        friendships.save(
                new Friendship(UUID.randomUUID(), caller.id(), target.id(), FriendshipStatus.BLOCKED, now, now));
    }

    public void unblock(UUID callerId, UUID targetId) {
        requireActive(callerId);
        Friendship row = friendships.findPair(callerId, targetId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.status() != FriendshipStatus.BLOCKED || !row.requesterId().equals(callerId)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        friendships.delete(row.id());
    }

    public FriendshipList list(UUID callerId, String filterRaw, String handle, String after, Integer size) {
        User caller = requireActive(callerId);
        FriendshipFilter filter;
        try {
            filter = FriendshipFilter.fromQuery(filterRaw);
        } catch (IllegalArgumentException ex) {
            throw new AuthException(ErrorCode.VALIDATION, "unknown filter", List.of(new FieldIssue("filter", "enum")));
        }
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        InstantIdCursor cursor = InstantIdCursor.parse(after).orElse(null);
        User owner = caller;
        if (handle != null && !handle.isBlank() && !handle.equalsIgnoreCase(caller.handle())) {
            if (filter != FriendshipFilter.ACCEPTED) {
                throw AuthException.notFound(NOT_FOUND);
            }
            owner = users.findByHandle(handle.trim()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
            if (!owner.active() || !friendships.areAcceptedFriends(caller.id(), owner.id())) {
                throw AuthException.notFound(NOT_FOUND);
            }
        }
        List<Friendship> rows =
                switch (filter) {
                    case ACCEPTED -> friendships.listAccepted(owner.id(), cursor, pageSize + 1);
                    case INCOMING -> friendships.listIncoming(owner.id(), cursor, pageSize + 1);
                    case OUTGOING -> friendships.listOutgoing(owner.id(), cursor, pageSize + 1);
                };
        String next = null;
        if (rows.size() > pageSize) {
            Friendship last = rows.get(pageSize - 1);
            next = new InstantIdCursor(last.createdAt(), last.id()).encode();
            rows = rows.subList(0, pageSize);
        }
        List<ListedFriendship> data = new ArrayList<>();
        for (Friendship row : rows) {
            data.add(listed(row, owner.id()));
        }
        return new FriendshipList(data, next, pageSize);
    }

    private ListedFriendship listed(Friendship row, UUID callerId) {
        UUID peerId = row.other(callerId);
        User peer = users.findById(peerId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Profile profile = profiles.findByUserId(peerId).orElse(Profile.created(peerId, peer.handle()));
        return new ListedFriendship(row, peer, profile, row.incomingFor(callerId));
    }

    private User resolveTarget(String handle, UUID userId) {
        if (userId != null) {
            return users.findById(userId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        }
        return users.findByHandle(handle.trim()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
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
