package fr.projetcompensation.gymbuddy.media;

import java.util.UUID;

public interface AttachedMediaAccess {

    boolean canRead(UUID viewerId, Media media);

    static AttachedMediaAccess none() {
        return (viewerId, media) -> false;
    }
}
