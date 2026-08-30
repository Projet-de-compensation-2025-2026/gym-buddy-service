package fr.projetcompensation.gymbuddy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.messaging.ConversationRepository;
import fr.projetcompensation.gymbuddy.messaging.MessageRepository;
import fr.projetcompensation.gymbuddy.messaging.MessagingGateway;
import fr.projetcompensation.gymbuddy.messaging.MessagingService;
import fr.projetcompensation.gymbuddy.messaging.ws.MessagingSessionRegistry;
import fr.projetcompensation.gymbuddy.messaging.ws.WebSocketMessagingGateway;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import io.lettuce.core.RedisClient;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfiguration {

    @Bean
    MessagingSessionRegistry messagingSessionRegistry() {
        return new MessagingSessionRegistry();
    }

    @Bean(destroyMethod = "close")
    MessagingGateway messagingGateway(
            MessagingSessionRegistry sessions, ObjectMapper mapper, ObjectProvider<RedisClient> redis) {
        return new WebSocketMessagingGateway(sessions, mapper, redis.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    MessagingService messagingService(
            ConversationRepository conversations,
            MessageRepository messages,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            MediaRepository media,
            MessagingGateway gateway,
            Clock clock) {
        return new MessagingService(conversations, messages, friendships, users, profiles, media, gateway, clock);
    }
}
