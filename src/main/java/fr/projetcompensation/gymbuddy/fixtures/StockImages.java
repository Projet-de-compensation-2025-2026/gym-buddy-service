package fr.projetcompensation.gymbuddy.fixtures;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Ten tiny JPEG objects. Fixtures reuse these object-storage keys instead of storing
 * thousands of unique files.
 */
public final class StockImages {

    public static final int COUNT = 10;
    public static final String MIME = "image/jpeg";

    private static final byte[] JPEG = createJpeg();

    private StockImages() {}

    public static List<String> keys() {
        return IntStream.range(0, COUNT).mapToObj(StockImages::key).toList();
    }

    public static String key(int index) {
        return "fixtures/stock/%02d.jpg".formatted(Math.floorMod(index, COUNT));
    }

    public static byte[] jpeg() {
        return JPEG.clone();
    }

    public static int bytes() {
        return JPEG.length;
    }

    private static byte[] createJpeg() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0x1A765E : 0xDCEEE7);
            }
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "jpeg", bytes)) {
                throw new IllegalStateException("JPEG encoder unavailable");
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create stock fixture JPEG", ex);
        }
    }
}
