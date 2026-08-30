package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.FriendshipService;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FriendshipConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    FriendshipService friendshipService(
            FriendshipRepository friendships, UserRepository users, ProfileRepository profiles, Clock clock) {
        return new FriendshipService(friendships, users, profiles, clock);
    }
}
