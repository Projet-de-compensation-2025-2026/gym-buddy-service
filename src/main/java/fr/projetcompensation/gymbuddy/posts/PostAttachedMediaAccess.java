package fr.projetcompensation.gymbuddy.posts;

import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.media.AttachedMediaAccess;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.util.UUID;

public final class PostAttachedMediaAccess implements AttachedMediaAccess {

    private final PostRepository posts;
    private final FriendshipRepository friendships;
    private final UserRepository users;

    public PostAttachedMediaAccess(PostRepository posts, FriendshipRepository friendships, UserRepository users) {
        this.posts = posts;
        this.friendships = friendships;
        this.users = users;
    }

    @Override
    public boolean canRead(UUID viewerId, Media media) {
        Post post = posts.findByMediaId(media.id()).orElse(null);
        if (post == null) {
            return false;
        }
        User viewer = users.findById(viewerId).orElse(null);
        return PostAccess.canView(post, viewer, friendships, users);
    }
}
