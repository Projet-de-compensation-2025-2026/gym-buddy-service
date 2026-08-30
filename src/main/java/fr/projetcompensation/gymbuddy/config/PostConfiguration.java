package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.media.AttachedMediaAccess;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.posts.PostAttachedMediaAccess;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.posts.PostService;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    PostService postService(
            PostRepository posts,
            MediaRepository media,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            Clock clock) {
        return new PostService(posts, media, friendships, users, profiles, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    AttachedMediaAccess attachedMediaAccess(
            PostRepository posts, FriendshipRepository friendships, UserRepository users) {
        return new PostAttachedMediaAccess(posts, friendships, users);
    }
}
