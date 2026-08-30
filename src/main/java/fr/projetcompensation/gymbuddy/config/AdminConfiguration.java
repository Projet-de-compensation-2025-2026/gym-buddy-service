package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.admin.AdminCatalog;
import fr.projetcompensation.gymbuddy.admin.AdminService;
import fr.projetcompensation.gymbuddy.admin.AuditEventRepository;
import fr.projetcompensation.gymbuddy.admin.ReportRepository;
import fr.projetcompensation.gymbuddy.comments.CommentRepository;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class AdminConfiguration {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    AdminService adminService(
            UserRepository users,
            ProfileRepository profiles,
            PostRepository posts,
            CommentRepository comments,
            MediaRepository media,
            FriendshipRepository friendships,
            ReportRepository reports,
            AuditEventRepository audit,
            AdminCatalog catalog,
            Clock clock,
            Environment environment) {
        return new AdminService(
                users,
                profiles,
                posts,
                comments,
                media,
                friendships,
                reports,
                audit,
                catalog,
                clock,
                environment.acceptsProfiles(Profiles.of("prod")));
    }
}
