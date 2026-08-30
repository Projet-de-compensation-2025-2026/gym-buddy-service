package fr.projetcompensation.gymbuddy.fixtures;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Ten tiny JPEG objects. Fixtures reuse these MinIO keys instead of storing
 * thousands of unique files.
 */
public final class StockImages {

    public static final int COUNT = 10;
    public static final String MIME = "image/jpeg";

    /** Minimal JPEG (SOI + EOI). Metadata only; not 15 000 unique files. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    private StockImages() {}

    public static List<String> keys() {
        return IntStream.range(0, COUNT).mapToObj(StockImages::key).toList();
    }

    public static String key(int index) {
        return "fixtures/stock/%02d.jpg".formatted(Math.floorMod(index, COUNT));
    }

    public static byte[] jpeg() {
        return JPEG;
    }

    public static int bytes() {
        return JPEG.length;
    }
}
