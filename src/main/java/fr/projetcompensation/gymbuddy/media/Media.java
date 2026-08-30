package fr.projetcompensation.gymbuddy.media;

import java.time.Instant;
import java.util.UUID;

public record Media(
        UUID id,
        UUID ownerId,
        MediaKind kind,
        String mime,
        long bytes,
        long variantBytes,
        MediaStatus status,
        String objectKey,
        Instant createdAt,
        Instant deletedAt) {

    boolean ready() {
        return status == MediaStatus.READY && deletedAt == null;
    }

    boolean pending() {
        return status == MediaStatus.PENDING && deletedAt == null;
    }

    boolean image() {
        return mime != null && mime.startsWith("image/");
    }

    boolean audio() {
        return mime != null && mime.startsWith("audio/");
    }

    String variantKey(String name) {
        return "variant/" + ownerId + "/" + id + "/" + name;
    }

    static String originalKey(UUID ownerId, UUID mediaId) {
        return "original/" + ownerId + "/" + mediaId;
    }

    Media withStatus(MediaStatus status) {
        return new Media(id, ownerId, kind, mime, bytes, variantBytes, status, objectKey, createdAt, deletedAt);
    }

    Media processed(long actualBytes, long variantBytes) {
        return new Media(
                id, ownerId, kind, mime, actualBytes, variantBytes, MediaStatus.READY, objectKey, createdAt, deletedAt);
    }

    Media rejected() {
        return new Media(
                id, ownerId, kind, mime, bytes, variantBytes, MediaStatus.REJECTED, objectKey, createdAt, deletedAt);
    }

    Media deleted(Instant at) {
        return new Media(id, ownerId, kind, mime, bytes, variantBytes, MediaStatus.DELETED, objectKey, createdAt, at);
    }
}
