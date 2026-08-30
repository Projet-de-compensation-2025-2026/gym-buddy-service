package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.auth.TransactionRunner;
import fr.projetcompensation.gymbuddy.events.EventAttachedMediaAccess;
import fr.projetcompensation.gymbuddy.events.EventRepository;
import fr.projetcompensation.gymbuddy.events.EventService;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.media.AttachedMediaAccess;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.posts.PostAttachedMediaAccess;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EventConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    EventService eventService(
            EventRepository events,
            MediaRepository media,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            TransactionRunner transactions,
            Clock clock) {
        return new EventService(events, media, friendships, users, profiles, transactions, clock);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "DATABASE_URL")
    AttachedMediaAccess attachedMediaAccess(
            PostRepository posts, EventRepository events, FriendshipRepository friendships, UserRepository users) {
        PostAttachedMediaAccess postsAccess = new PostAttachedMediaAccess(posts, friendships, users);
        EventAttachedMediaAccess eventsAccess = new EventAttachedMediaAccess(events, friendships, users);
        return (viewerId, media) -> postsAccess.canRead(viewerId, media) || eventsAccess.canRead(viewerId, media);
    }
}
