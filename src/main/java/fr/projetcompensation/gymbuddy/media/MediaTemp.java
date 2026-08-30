package fr.projetcompensation.gymbuddy.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

final class MediaTemp implements AutoCloseable {

    private final Path root;
    private final Path file;

    private MediaTemp(Path root, Path file) {
        this.root = root;
        this.file = file;
    }

    static MediaTemp write(byte[] data) {
        try {
            Path root = Path.of(System.getProperty("java.io.tmpdir"), "gb-media");
            Files.createDirectories(root);
            long used = usedBytes(root);
            if (used + data.length > MediaRules.TEMP_CAP_BYTES) {
                throw new IllegalStateException("gb-media temp dir would exceed 256 MiB");
            }
            Path file = root.resolve(UUID.randomUUID().toString());
            Files.write(file, data);
            return new MediaTemp(root, file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    Path path() {
        return file;
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best-effort cleanup; the sweep still caps the directory.
        }
    }

    static long usedBytes(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            return 0L;
                        }
                    })
                    .sum();
        }
    }
}
