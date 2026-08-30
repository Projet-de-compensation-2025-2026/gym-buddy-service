package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.comments.CommentRepository;
import fr.projetcompensation.gymbuddy.comments.CommentService;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommentConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    CommentService commentService(
            CommentRepository comments,
            PostRepository posts,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            Clock clock) {
        return new CommentService(comments, posts, friendships, users, profiles, clock);
    }
}
