package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.search.SearchCatalog;
import fr.projetcompensation.gymbuddy.search.SearchService;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    SearchService searchService(
            SearchCatalog catalog,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            Clock clock) {
        return new SearchService(catalog, friendships, users, profiles, clock);
    }
}
