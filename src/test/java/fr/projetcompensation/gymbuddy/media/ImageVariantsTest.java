package fr.projetcompensation.gymbuddy.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageVariantsTest {

    @TempDir
    Path temp;

    @Test
    void writesSmAndMdWebpFromJpeg() throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        assertThat(writers.hasNext())
                .as("registered webp writers: %s", String.join(",", ImageIO.getWriterFormatNames()))
                .isTrue();
        BufferedImage image = new BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.fillRect(0, 0, 48, 32);
        graphics.dispose();
        Path jpeg = temp.resolve("in.jpg");
        ImageIO.write(image, "jpeg", jpeg.toFile());
        ImageVariants.Pair pair = ImageVariants.create(jpeg);
        assertThat(pair.sm()).isNotEmpty();
        assertThat(pair.md()).isNotEmpty();
        assertThat(MagicBytes.matches(pair.sm(), "image/webp")).isTrue();
        Files.deleteIfExists(jpeg);
    }
}
