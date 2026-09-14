package fr.projetcompensation.gymbuddy.media;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.profiles.FriendshipQueries;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class MediaService {

    static final Duration SIGNED_TTL = Duration.ofSeconds(60);
    static final Duration PENDING_ORPHAN = Duration.ofHours(1);
    static final Duration OBJECT_GRACE = Duration.ofDays(7);
    private static final String NOT_FOUND = "media not found";

    private final MediaRepository media;
    private final ObjectStorage storage;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final FriendshipQueries friendships;
    private final Clock clock;
    private final MediaProcessor processor;
    private final AttachedMediaAccess attachedMedia;

    public MediaService(
            MediaRepository media,
            ObjectStorage storage,
            UserRepository users,
            ProfileRepository profiles,
            FriendshipQueries friendships,
            Clock clock) {
        this(media, storage, users, profiles, friendships, clock, AttachedMediaAccess.none());
    }

    public MediaService(
            MediaRepository media,
            ObjectStorage storage,
            UserRepository users,
            ProfileRepository profiles,
            FriendshipQueries friendships,
            Clock clock,
            AttachedMediaAccess attachedMedia) {
        this.media = media;
        this.storage = storage;
        this.users = users;
        this.profiles = profiles;
        this.friendships = friendships;
        this.clock = clock;
        this.processor = new MediaProcessor(storage);
        this.attachedMedia = attachedMedia == null ? AttachedMediaAccess.none() : attachedMedia;
    }

    public CreateUpload create(UUID ownerId, String kindWire, String mime, long bytes) {
        return users.withAccountLock(ownerId, () -> createLocked(ownerId, kindWire, mime, bytes));
    }

    private CreateUpload createLocked(UUID ownerId, String kindWire, String mime, long bytes) {
        User owner = requireActive(ownerId);
        MediaKind kind = parseKind(kindWire);
        MediaRules.validateDeclare(kind, mime, bytes);
        long used = media.usedBytes(owner.id());
        if (used + bytes > MediaRules.QUOTA_BYTES) {
            throw AuthException.quotaExceeded("storage quota exceeded");
        }
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        Media row = new Media(
                id,
                owner.id(),
                kind,
                mime,
                bytes,
                0,
                MediaStatus.PENDING,
                Media.originalKey(owner.id(), id),
                now,
                null);
        media.save(row);
        Instant expiresAt = now.plus(SIGNED_TTL);
        return new CreateUpload(id, storage.signPut(row.objectKey(), mime, SIGNED_TTL, bytes), expiresAt);
    }

    public SignedGet url(UUID viewerId, UUID mediaId) {
        User viewer = requireActive(viewerId);
        Media row = media.findById(mediaId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!canRead(viewer, row)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Instant expiresAt = clock.instant().plus(SIGNED_TTL);
        return new SignedGet(storage.signGet(row.objectKey(), row.mime(), SIGNED_TTL), expiresAt);
    }

    public void delete(UUID viewerId, UUID mediaId) {
        users.withAccountLock(viewerId, () -> {
            deleteLocked(viewerId, mediaId);
            return null;
        });
    }

    private void deleteLocked(UUID viewerId, UUID mediaId) {
        User viewer = requireActive(viewerId);
        Media row = media.findById(mediaId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!row.ownerId().equals(viewer.id()) || row.deletedAt() != null) {
            throw AuthException.notFound(NOT_FOUND);
        }
        media.update(row.deleted(clock.instant()));
    }

    public void sweep() {
        Instant now = clock.instant();
        for (Media pending : media.findPending()) {
            try {
                ingest(pending);
            } catch (RuntimeException ex) {
                System.getLogger(MediaService.class.getName())
                        .log(
                                System.Logger.Level.WARNING,
                                "Media processing will retry for {0}: {1}",
                                pending.id(),
                                ex.getClass().getSimpleName());
            }
        }
        for (Media orphan : media.findPendingCreatedBefore(now.minus(PENDING_ORPHAN))) {
            users.withAccountLock(orphan.ownerId(), () -> {
                Media latest = media.findById(orphan.id()).orElse(null);
                if (latest != null && latest.pending()) {
                    deleteObjectTree(latest);
                    media.delete(latest.id());
                }
                return null;
            });
        }
        for (Media completed : media.findUploadCleanupCandidates(now.minus(PENDING_ORPHAN))) {
            String uploadKey = Media.originalKey(completed.ownerId(), completed.id());
            if (completed.status() == MediaStatus.REJECTED
                    || !completed.objectKey().equals(uploadKey)) {
                storage.delete(uploadKey);
            }
            media.markUploadCleaned(completed.id(), now);
        }
        for (Media expired : media.findDeletedBefore(now.minus(OBJECT_GRACE))) {
            deleteObjectTree(expired);
            media.delete(expired.id());
        }
    }

    void ingest(Media pending) {
        if (!pending.pending()) {
            return;
        }
        try {
            Optional<byte[]> body = storage.get(pending.objectKey());
            if (body.isEmpty()) return;
            byte[] original = body.get();
            MediaProcessor.Result result = processor.process(pending, original);
            users.withAccountLock(pending.ownerId(), () -> {
                Media current = media.findById(pending.id()).orElse(null);
                if (current == null || !current.pending()) {
                    if (current == null || !current.ready()) deleteObjectTree(pending);
                    return null;
                }
                long projected =
                        media.usedBytes(current.ownerId()) - current.bytes() + result.bytes() + result.variantBytes();
                if (projected > MediaRules.QUOTA_BYTES)
                    throw new IllegalArgumentException("processed media exceeds quota");
                media.update(current.processed(result.bytes(), result.variantBytes()));
                return null;
            });
            storage.delete(pending.objectKey());
        } catch (IllegalArgumentException ex) {
            users.withAccountLock(pending.ownerId(), () -> {
                Media current = media.findById(pending.id()).orElse(null);
                if (current == null || !current.ready()) deleteObjectTree(pending);
                if (current != null && current.pending()) media.update(current.rejected());
                return null;
            });
        }
    }

    boolean canRead(User viewer, Media row) {
        if (row == null || !row.ready()) {
            return false;
        }
        if (row.hidden() && (viewer == null || !viewer.isStaff())) {
            return false;
        }
        User owner = users.findById(row.ownerId()).orElse(null);
        if (owner == null || owner.closed()) {
            return viewer.isStaff();
        }
        if (viewer.isStaff()) {
            return true;
        }
        if (viewer.id().equals(row.ownerId())) {
            return true;
        }
        if (row.kind() == MediaKind.POST || row.kind() == MediaKind.EVENT || row.kind() == MediaKind.MESSAGE) {
            return attachedMedia.canRead(viewer.id(), row);
        }
        if (row.kind() != MediaKind.AVATAR) {
            return false;
        }
        Profile profile = profiles.findByUserId(owner.id()).orElse(null);
        if (profile == null) {
            return false;
        }
        if (profile.visibility() == ProfileVisibility.PUBLIC) {
            return true;
        }
        return friendships.areAcceptedFriends(viewer.id(), owner.id());
    }

    private User requireActive(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> AuthException.unauthenticated("missing or invalid access token"));
        if (!user.active()) {
            throw AuthException.unauthenticated("missing or invalid access token");
        }
        return user;
    }

    private static MediaKind parseKind(String kindWire) {
        try {
            return MediaKind.fromWire(kindWire);
        } catch (RuntimeException ex) {
            throw AuthException.validation(
                    "kind is not allowed", new fr.projetcompensation.gymbuddy.auth.FieldIssue("kind", "enum"));
        }
    }

    private void deleteObjectTree(Media row) {
        storage.delete(row.objectKey());
        storage.delete(Media.originalKey(row.ownerId(), row.id()));
        storage.delete(row.processedKey());
        storage.delete(row.variantKey("sm"));
        storage.delete(row.variantKey("md"));
    }
}
