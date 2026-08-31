package fr.projetcompensation.gymbuddy.media;

/** Object storage is missing or unusable. Not an authentication failure. */
public final class MediaUnavailableException extends RuntimeException {

    public MediaUnavailableException() {
        super("not configured");
    }
}
