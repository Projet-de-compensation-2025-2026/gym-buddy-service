package fr.projetcompensation.gymbuddy.feed;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.posts.PostAccess;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.posts.VisiblePost;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FeedService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String NOT_FOUND = "post not found";

    private final PostRepository posts;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;

    public FeedService(
            PostRepository posts, FriendshipRepository friendships, UserRepository users, ProfileRepository profiles) {
        this.posts = posts;
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
    }

    public FeedList list(UUID callerId, String before, Integer size) {
        User caller = requireActive(callerId);
        int pageSize = pageSize(size);
        InstantIdCursor cursor = InstantIdCursor.parse(before).orElse(null);
        List<FeedActivity> rows = posts.listFeed(caller.id(), cursor, pageSize + 1);
        String next = null;
        if (rows.size() > pageSize) {
            FeedActivity last = rows.get(pageSize - 1);
            next = new InstantIdCursor(last.activityAt(), last.id()).encode();
            rows = rows.subList(0, pageSize);
        }
        List<VisibleFeedItem> data = new ArrayList<>();
        for (FeedActivity activity : rows) {
            User actor = users.findById(activity.actorId()).orElse(null);
            if (actor == null || !actor.active()) {
                continue;
            }
            Post original = posts.findById(activity.postId()).orElse(null);
            if (original == null || !PostAccess.canView(original, caller, friendships, users)) {
                continue;
            }
            Profile actorProfile =
                    profiles.findByUserId(actor.id()).orElse(Profile.created(actor.id(), actor.handle()));
            data.add(new VisibleFeedItem(activity, actor, actorProfile, visible(original, caller)));
        }
        return new FeedList(List.copyOf(data), next, pageSize);
    }

    private VisiblePost visible(Post row, User caller) {
        User author = users.findById(row.authorId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Profile profile = profiles.findByUserId(author.id()).orElse(Profile.created(author.id(), author.handle()));
        return new VisiblePost(
                row,
                author,
                profile,
                posts.mediaIds(row.id()),
                posts.likeCount(row.id()),
                posts.repostCount(row.id()),
                posts.commentCount(row.id()),
                posts.liked(caller.id(), row.id()),
                posts.reposted(caller.id(), row.id()));
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
