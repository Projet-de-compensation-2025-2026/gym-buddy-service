package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class StockImagesTest {

    @Test
    void stockJpegHasPositiveBytesAndTenKeys() {
        assertThat(StockImages.bytes()).isGreaterThan(0);
        assertThat(StockImages.jpeg()).hasSize(StockImages.bytes());
        assertThat(StockImages.keys()).hasSize(StockImages.COUNT).doesNotHaveDuplicates();
    }

    @Test
    void stockJpegDecodesAndCallersCannotCorruptSharedPayload() throws Exception {
        byte[] jpeg = StockImages.jpeg();
        var decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(32);
        assertThat(decoded.getHeight()).isEqualTo(32);
        jpeg[0] = 0;
        assertThat(ImageIO.read(new ByteArrayInputStream(StockImages.jpeg()))).isNotNull();
    }
}
