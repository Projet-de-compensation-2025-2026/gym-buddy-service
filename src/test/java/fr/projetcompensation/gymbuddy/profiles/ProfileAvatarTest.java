package fr.projetcompensation.gymbuddy.profiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.media.*;
import fr.projetcompensation.gymbuddy.users.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileAvatarTest {
    @Test
    void avatarMustBeReadyOwnedAvatarMedia() {
        UUID owner = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        var users = mock(UserRepository.class);
        doCallRealMethod().when(users).withAccountLock(any(), any());
        var profiles = mock(ProfileRepository.class);
        var media = mock(MediaRepository.class);
        var friends = mock(FriendshipQueries.class);
        var user = new User(
                owner, "alex@example.invalid", "alex", "unused", UserRole.MEMBER, UserStatus.ACTIVE, Instant.EPOCH);
        when(users.findById(owner)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(owner)).thenReturn(Optional.of(Profile.created(owner, "Alex")));
        var service = new ProfileService(users, profiles, friends, media);
        var patch = new ProfilePatch(
                null, null, null, false, null, null, false, null, false, null, false, null, false, null, false, null,
                false, mediaId, true);
        when(media.findById(mediaId))
                .thenReturn(Optional.of(new Media(
                        mediaId,
                        UUID.randomUUID(),
                        MediaKind.AVATAR,
                        "image/png",
                        10,
                        0,
                        MediaStatus.READY,
                        "key",
                        Instant.EPOCH,
                        null)));
        assertThatThrownBy(() -> service.patchMe(owner, patch)).isInstanceOf(AuthException.class);
        verify(profiles, never()).update(any());
        when(media.findById(mediaId))
                .thenReturn(Optional.of(new Media(
                        mediaId,
                        owner,
                        MediaKind.AVATAR,
                        "image/png",
                        10,
                        0,
                        MediaStatus.READY,
                        "key",
                        Instant.EPOCH,
                        null)));
        service.patchMe(owner, patch);
        verify(profiles).update(argThat(profile -> mediaId.equals(profile.avatarMediaId())));
    }
}
