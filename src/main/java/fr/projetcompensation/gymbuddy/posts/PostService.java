package fr.projetcompensation.gymbuddy.posts;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PostService {

    static final Duration EDIT_WINDOW = Duration.ofMinutes(15);
    static final int MAX_IMAGES = 4;
    static final int MAX_BODY = 2000;
    private static final String NOT_FOUND = "post not found";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final PostRepository posts;
    private final MediaRepository media;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final Clock clock;

    public PostService(
            PostRepository posts,
            MediaRepository media,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            Clock clock) {
        this.posts = posts;
        this.media = media;
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    public VisiblePost create(UUID callerId, String body, String visibilityWire, List<UUID> mediaIds) {
        User caller = requireActive(callerId);
        String normalized = normalizeBody(body);
        List<UUID> images = requireImages(caller.id(), mediaIds);
        if (normalized == null && images.isEmpty()) {
            throw AuthException.validation("body or media is required", new FieldIssue("body", "required"));
        }
        PostVisibility visibility;
        try {
            visibility = PostVisibility.fromWire(visibilityWire);
        } catch (IllegalArgumentException ex) {
            throw AuthException.validation("visibility is not allowed", new FieldIssue("visibility", "enum"));
        }
        Instant now = clock.instant();
        Post row = new Post(UUID.randomUUID(), caller.id(), normalized, visibility, now, null, null, null, null);
        posts.save(row, images);
        return visible(row, caller, images);
    }

    public VisiblePost get(UUID callerId, UUID postId) {
        User caller = requireActive(callerId);
        return visible(requireVisible(caller, postId), caller);
    }

    public VisiblePost edit(UUID callerId, UUID postId, String body) {
        User caller = requireActive(callerId);
        Post row = posts.findById(postId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.deleted() || row.hidden() || !row.authorId().equals(caller.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Instant now = clock.instant();
        if (now.isAfter(row.createdAt().plus(EDIT_WINDOW))) {
            throw AuthException.forbidden("edit window elapsed");
        }
        String normalized = normalizeBody(body);
        List<UUID> images = posts.mediaIds(row.id());
        if (normalized == null && images.isEmpty()) {
            throw AuthException.validation("body or media is required", new FieldIssue("body", "required"));
        }
        Post updated = row.withBody(normalized, now);
        posts.update(updated);
        return visible(updated, caller, images);
    }

    public void delete(UUID callerId, UUID postId) {
        User caller = requireActive(callerId);
        Post row = posts.findById(postId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.deleted() || !row.authorId().equals(caller.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        posts.update(row.deleted(clock.instant()));
    }

    public VisiblePost repost(UUID callerId, UUID postId) {
        User caller = requireActive(callerId);
        Post row = requireVisible(caller, postId);
        if (!posts.insertRepost(caller.id(), row.id(), clock.instant())) {
            throw AuthException.conflict("already reposted", new FieldIssue("id", "duplicate"));
        }
        return visible(row, caller);
    }

    public void unrepost(UUID callerId, UUID postId) {
        User caller = requireActive(callerId);
        Post row = requireVisible(caller, postId);
        if (!posts.deleteRepost(caller.id(), row.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
    }

    public void like(UUID callerId, UUID postId) {
        User caller = requireActive(callerId);
        Post row = requireVisible(caller, postId);
        posts.insertLike(caller.id(), row.id(), clock.instant());
    }

    public void unlike(UUID callerId, UUID postId) {
        User caller = requireActive(callerId);
        Post row = requireVisible(caller, postId);
        posts.deleteLike(caller.id(), row.id());
    }

    public PostLikerList likes(UUID callerId, UUID postId, String after, Integer size) {
        User caller = requireActive(callerId);
        Post row = requireVisible(caller, postId);
        if (!row.authorId().equals(caller.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        int pageSize = size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
        InstantIdCursor cursor = InstantIdCursor.parse(after).orElse(null);
        List<LikeRow> rows = posts.listLikes(row.id(), cursor, pageSize + 1);
        String next = null;
        if (rows.size() > pageSize) {
            LikeRow last = rows.get(pageSize - 1);
            next = new InstantIdCursor(last.likedAt(), last.userId()).encode();
            rows = rows.subList(0, pageSize);
        }
        List<PostLiker> data = new ArrayList<>();
        for (LikeRow like : rows) {
            User liker = users.findById(like.userId()).orElse(null);
            if (liker == null) {
                continue;
            }
            Profile profile = profiles.findByUserId(liker.id()).orElse(Profile.created(liker.id(), liker.handle()));
            data.add(new PostLiker(
                    liker.id(), liker.handle(), profile.displayName(), profile.avatarMediaId(), like.likedAt()));
        }
        return new PostLikerList(data, next, pageSize);
    }

    private Post requireVisible(User caller, UUID postId) {
        Post row = posts.findById(postId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!PostAccess.canView(row, caller, friendships, users)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        return row;
    }

    private VisiblePost visible(Post row, User caller) {
        return visible(row, caller, posts.mediaIds(row.id()));
    }

    private VisiblePost visible(Post row, User caller, List<UUID> mediaIds) {
        User author = users.findById(row.authorId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Profile profile = profiles.findByUserId(author.id()).orElse(Profile.created(author.id(), author.handle()));
        return new VisiblePost(
                row,
                author,
                profile,
                List.copyOf(mediaIds),
                posts.likeCount(row.id()),
                posts.repostCount(row.id()),
                posts.commentCount(row.id()),
                posts.liked(caller.id(), row.id()),
                posts.reposted(caller.id(), row.id()));
    }

    private List<UUID> requireImages(UUID ownerId, List<UUID> mediaIds) {
        List<UUID> ids = mediaIds == null ? List.of() : List.copyOf(mediaIds);
        if (ids.size() > MAX_IMAGES) {
            throw AuthException.validation("too many images", new FieldIssue("mediaIds", "max"));
        }
        Set<UUID> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw AuthException.validation("duplicate media", new FieldIssue("mediaIds", "duplicate"));
        }
        for (UUID mediaId : ids) {
            if (mediaId == null) {
                throw AuthException.validation("media is not allowed", new FieldIssue("mediaIds", "invalid"));
            }
            Media row = media.findById(mediaId)
                    .orElseThrow(() ->
                            AuthException.validation("media is not allowed", new FieldIssue("mediaIds", "invalid")));
            if (!row.ownerId().equals(ownerId)
                    || row.kind() != MediaKind.POST
                    || row.status() != MediaStatus.READY
                    || row.deletedAt() != null
                    || row.mime() == null
                    || !row.mime().startsWith("image/")) {
                throw AuthException.validation("media is not allowed", new FieldIssue("mediaIds", "invalid"));
            }
            if (posts.findByMediaId(mediaId).isPresent()) {
                throw AuthException.validation("media is not allowed", new FieldIssue("mediaIds", "attached"));
            }
        }
        return ids;
    }

    private static String normalizeBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        if (body.length() > MAX_BODY) {
            throw AuthException.validation("body is too long", new FieldIssue("body", "max"));
        }
        return body;
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
