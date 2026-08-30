package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.profiles.FriendshipQueries;
import fr.projetcompensation.gymbuddy.profiles.NoAcceptedFriendships;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileService;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfileConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    ProfileService profileService(UserRepository users, ProfileRepository profiles, FriendshipQueries friendships) {
        return new ProfileService(users, profiles, friendships);
    }

    @Bean
    @ConditionalOnMissingBean(FriendshipQueries.class)
    FriendshipQueries noAcceptedFriendships() {
        return new NoAcceptedFriendships();
    }
}
