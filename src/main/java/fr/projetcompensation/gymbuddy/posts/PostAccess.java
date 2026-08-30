package fr.projetcompensation.gymbuddy.posts;

import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;

public final class PostAccess {

    private PostAccess() {}

    static boolean canView(Post post, User viewer, FriendshipRepository friendships, UserRepository users) {
        if (post == null || post.deleted() || post.hidden()) {
            return false;
        }
        if (viewer == null || !viewer.active()) {
            return false;
        }
        if (post.authorId().equals(viewer.id())) {
            return true;
        }
        User author = users.findById(post.authorId()).orElse(null);
        if (author == null || !author.active()) {
            return false;
        }
        if (friendships.isBlockedEitherWay(viewer.id(), post.authorId())) {
            return false;
        }
        if (post.visibility() == PostVisibility.PUBLIC) {
            return true;
        }
        return friendships.areAcceptedFriends(viewer.id(), post.authorId());
    }
}
