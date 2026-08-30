package fr.projetcompensation.gymbuddy.comments;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.posts.PostAccess;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CommentService {

    private static final String NOT_FOUND = "comment not found";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final CommentRepository comments;
    private final PostRepository posts;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final Clock clock;

    public CommentService(
            CommentRepository comments,
            PostRepository posts,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            Clock clock) {
        this.comments = comments;
        this.posts = posts;
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    public VisibleComment create(UUID callerId, UUID postId, String body, UUID parentId) {
        User caller = requireActive(callerId);
        Post post = requireVisiblePost(caller, postId);
        String normalized = requireBody(body);
        int depth = 0;
        if (parentId != null) {
            Comment parent = comments.findById(parentId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
            if (!parent.postId().equals(post.id())) {
                throw AuthException.notFound(NOT_FOUND);
            }
            if (parent.depth() >= Comment.MAX_DEPTH) {
                throw AuthException.validation("reply exceeds max depth", new FieldIssue("parentId", "depth"));
            }
            depth = parent.depth() + 1;
        }
        Instant now = clock.instant();
        Comment row =
                new Comment(UUID.randomUUID(), post.id(), caller.id(), parentId, normalized, depth, now, null, null);
        comments.save(row);
        return visible(row, caller);
    }

    public CommentList listRoots(UUID callerId, UUID postId, String before, Integer size) {
        User caller = requireActive(callerId);
        Post post = requireVisiblePost(caller, postId);
        return page(
                caller,
                comments.listRoots(post.id(), InstantIdCursor.parse(before).orElse(null), pageSize(size) + 1),
                pageSize(size));
    }

    public CommentList listReplies(UUID callerId, UUID commentId, String before, Integer size) {
        User caller = requireActive(callerId);
        Comment parent = requireVisibleComment(caller, commentId);
        return page(
                caller,
                comments.listReplies(parent.id(), InstantIdCursor.parse(before).orElse(null), pageSize(size) + 1),
                pageSize(size));
    }

    public void delete(UUID callerId, UUID commentId) {
        User caller = requireActive(callerId);
        Comment row = requireVisibleComment(caller, commentId);
        if (!row.authorId().equals(caller.id())) {
            throw AuthException.forbidden("not the author");
        }
        if (row.deleted()) {
            return;
        }
        comments.update(row.tombstone(clock.instant()));
    }

    public void like(UUID callerId, UUID commentId) {
        User caller = requireActive(callerId);
        Comment row = requireVisibleComment(caller, commentId);
        comments.insertLike(caller.id(), row.id(), clock.instant());
    }

    public void unlike(UUID callerId, UUID commentId) {
        User caller = requireActive(callerId);
        Comment row = requireVisibleComment(caller, commentId);
        comments.deleteLike(caller.id(), row.id());
    }

    private CommentList page(User caller, List<Comment> rows, int pageSize) {
        String next = null;
        if (rows.size() > pageSize) {
            Comment last = rows.get(pageSize - 1);
            next = new InstantIdCursor(last.createdAt(), last.id()).encode();
            rows = rows.subList(0, pageSize);
        }
        List<VisibleComment> data = new ArrayList<>();
        for (Comment row : rows) {
            data.add(visible(row, caller));
        }
        return new CommentList(data, next, pageSize);
    }

    private VisibleComment visible(Comment row, User caller) {
        User author = users.findById(row.authorId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Profile profile = profiles.findByUserId(author.id()).orElse(Profile.created(author.id(), author.handle()));
        return new VisibleComment(
                row,
                author,
                profile,
                comments.likeCount(row.id()),
                comments.liked(caller.id(), row.id()),
                comments.replyCount(row.id()));
    }

    private Comment requireVisibleComment(User caller, UUID commentId) {
        Comment row = comments.findById(commentId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        requireVisiblePost(caller, row.postId());
        return row;
    }

    private Post requireVisiblePost(User caller, UUID postId) {
        Post row = posts.findById(postId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!PostAccess.canView(row, caller, friendships, users)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        return row;
    }

    private static String requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw AuthException.validation("body is required", new FieldIssue("body", "required"));
        }
        if (body.length() > Comment.MAX_BODY) {
            throw AuthException.validation("body is too long", new FieldIssue("body", "max"));
        }
        return body;
    }

    private static int pageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_SIZE);
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
