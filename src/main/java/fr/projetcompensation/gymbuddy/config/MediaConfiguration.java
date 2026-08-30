package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.media.MediaService;
import fr.projetcompensation.gymbuddy.media.ObjectStorage;
import fr.projetcompensation.gymbuddy.media.S3ObjectStorage;
import fr.projetcompensation.gymbuddy.profiles.FriendshipQueries;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableScheduling
public class MediaConfiguration {

    @Bean
    @ConditionalOnBean({S3Client.class, S3Presigner.class})
    ObjectStorage objectStorage(S3Client s3Client, S3Presigner s3Presigner, @Value("${S3_BUCKET}") String bucket) {
        return new S3ObjectStorage(s3Client, s3Presigner, bucket);
    }

    @Bean
    @ConditionalOnBean({MediaRepository.class, ObjectStorage.class})
    MediaService mediaService(
            MediaRepository media,
            ObjectStorage storage,
            UserRepository users,
            ProfileRepository profiles,
            FriendshipQueries friendships,
            Clock clock) {
        return new MediaService(media, storage, users, profiles, friendships, clock);
    }

    @Bean
    @ConditionalOnBean(MediaService.class)
    MediaSweepJob mediaSweepJob(MediaService mediaService) {
        return new MediaSweepJob(mediaService);
    }

    public static final class MediaSweepJob {
        private final MediaService mediaService;

        MediaSweepJob(MediaService mediaService) {
            this.mediaService = mediaService;
        }

        @Scheduled(fixedDelayString = "${MEDIA_SWEEP_MS:5000}")
        public void sweep() {
            mediaService.sweep();
        }
    }
}
