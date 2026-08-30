package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.feed.FeedService;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeedConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    FeedService feedService(
            PostRepository posts, FriendshipRepository friendships, UserRepository users, ProfileRepository profiles) {
        return new FeedService(posts, friendships, users, profiles);
    }
}
