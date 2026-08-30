package fr.projetcompensation.gymbuddy.media;

import java.util.OptionalDouble;

final class MediaProcessor {

    record Result(long bytes, long variantBytes) {}

    private final ObjectStorage storage;

    MediaProcessor(ObjectStorage storage) {
        this.storage = storage;
    }

    Result process(Media media, byte[] original) {
        if (original.length > MediaRules.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("object exceeds 8 MiB");
        }
        if (!MagicBytes.matches(original, media.mime())) {
            throw new IllegalArgumentException("magic-byte mismatch");
        }
        if (media.audio()) {
            OptionalDouble duration = AudioDuration.seconds(original, media.mime());
            if (duration.isEmpty() || duration.getAsDouble() > MediaRules.MAX_AUDIO_SECONDS) {
                throw new IllegalArgumentException("audio duration exceeds 120 s");
            }
            return new Result(original.length, 0);
        }
        try (MediaTemp temp = MediaTemp.write(original)) {
            ImageVariants.Pair variants = ImageVariants.create(temp.path());
            storage.put(media.variantKey("sm"), "image/webp", variants.sm());
            storage.put(media.variantKey("md"), "image/webp", variants.md());
            return new Result(original.length, (long) variants.sm().length + variants.md().length);
        }
    }
}
