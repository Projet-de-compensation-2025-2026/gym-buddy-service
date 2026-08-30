package fr.projetcompensation.gymbuddy.media;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;

final class MediaRules {

    static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    static final long QUOTA_BYTES = 1024L * 1024 * 1024;
    static final int MAX_AUDIO_SECONDS = 120;
    static final int MAX_DIMENSION_PX = 8000;
    static final long MAX_PIXELS = 64_000_000L;
    static final long TEMP_CAP_BYTES = 256L * 1024 * 1024;
    static final int VARIANT_SM_WIDTH = 320;
    static final int VARIANT_MD_WIDTH = 960;

    private MediaRules() {}

    static void validateDeclare(MediaKind kind, String mime, long bytes) {
        if (bytes < 1) {
            throw AuthException.validation("declared size is invalid", new FieldIssue("bytes", "min"));
        }
        if (bytes > MAX_FILE_BYTES) {
            throw AuthException.payloadTooLarge("file exceeds 8 MiB");
        }
        if (!allowedMime(mime)) {
            throw AuthException.validation("mime is not allowed", new FieldIssue("mime", "enum"));
        }
        boolean audio = mime.startsWith("audio/");
        if (audio && kind != MediaKind.MESSAGE) {
            throw AuthException.validation("audio is message-only", new FieldIssue("mime", "pairing"));
        }
        if (!audio && kind.imageOnly() && !mime.startsWith("image/")) {
            throw AuthException.validation("kind/mime pairing is invalid", new FieldIssue("mime", "pairing"));
        }
        if (kind.imageOnly() && audio) {
            throw AuthException.validation("audio is message-only", new FieldIssue("mime", "pairing"));
        }
    }

    static boolean allowedMime(String mime) {
        return "image/jpeg".equals(mime)
                || "image/png".equals(mime)
                || "image/webp".equals(mime)
                || "audio/webm".equals(mime)
                || "audio/mpeg".equals(mime);
    }
}
