package fr.projetcompensation.gymbuddy.fixtures;

import java.util.Base64;
import java.util.List;

/**
 * Ten tiny JPEG objects. Fixtures reuse these MinIO keys instead of storing
 * thousands of unique files.
 */
public final class StockImages {

    public static final int COUNT = 10;
    public static final String MIME = "image/jpeg";

    private static final byte[] JPEG = Base64.getDecoder()
            .decode("/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP/////////////////////////////////"
                    + "///////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/"
                    + "aAAgBAQABPxA=");

    private StockImages() {}

    public static List<String> keys() {
        return java.util.stream.IntStream.range(0, COUNT)
                .mapToObj(StockImages::key)
                .toList();
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
