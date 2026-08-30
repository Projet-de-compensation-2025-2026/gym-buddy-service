package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;

public record ListedAdminMedia(Media media, String ownerHandle) {

    InstantIdCursor cursor() {
        return new InstantIdCursor(media.createdAt(), media.id());
    }
}
