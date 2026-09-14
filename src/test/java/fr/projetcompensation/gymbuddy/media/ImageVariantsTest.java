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
    void sanitizedPngPreservesTransparentPixels() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x40123456);
        Path original = temp.resolve("transparent.png");
        ImageIO.write(image, "png", original.toFile());
        BufferedImage sanitized =
                ImageIO.read(new java.io.ByteArrayInputStream(ImageVariants.sanitize(original, "image/png")));
        assertThat(sanitized.getRGB(0, 0)).isEqualTo(0x40123456);
        assertThat(sanitized.getRGB(1, 1) >>> 24).isZero();
    }

    @Test
    void sanitizedJpegAppliesExifOrientationBeforeStrippingIt() throws Exception {
        BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        byte[] jpeg = output.toByteArray();
        byte[] exif = {
            69, 120, 105, 102, 0, 0, 73, 73, 42, 0, 8, 0, 0, 0, 1, 0, 18, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0
        };
        output.reset();
        output.write(jpeg, 0, 2);
        output.write(new byte[] {(byte) 255, (byte) 225, 0, (byte) (exif.length + 2)});
        output.write(exif);
        output.write(jpeg, 2, jpeg.length - 2);
        Path original = temp.resolve("rotated.jpg");
        Files.write(original, output.toByteArray());
        byte[] cleaned = ImageVariants.sanitize(original, "image/jpeg");
        BufferedImage sanitized = ImageIO.read(new java.io.ByteArrayInputStream(cleaned));
        assertThat(sanitized.getWidth()).isEqualTo(8);
        assertThat(sanitized.getHeight()).isEqualTo(16);
        assertThat(new String(cleaned, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("Exif");
    }

    @Test
    void sanitizedJpegRemovesEmbeddedMetadata() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var buffer = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", buffer);
        byte[] jpeg = buffer.toByteArray();
        byte[] metadata = "Exif\0\0private-location-marker".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        buffer.reset();
        buffer.write(jpeg, 0, 2);
        buffer.write(new byte[] {(byte) 255, (byte) 225, 0, (byte) (metadata.length + 2)});
        buffer.write(metadata);
        buffer.write(jpeg, 2, jpeg.length - 2);
        Path original = temp.resolve("private.jpg");
        Files.write(original, buffer.toByteArray());
        byte[] sanitized = ImageVariants.sanitize(original, "image/jpeg");
        assertThat(new String(sanitized, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("private-location-marker");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(sanitized)).getWidth())
                .isEqualTo(16);
    }

    @Test
    void rejectsOversizedDimensionsBeforeDecode() throws Exception {
        // Valid PNG header declares a huge canvas without allocating that canvas.
        var buffer = new java.io.ByteArrayOutputStream();
        var data = new java.io.DataOutputStream(buffer);
        data.writeLong(0x89504e470d0a1a0aL);
        data.writeInt(13);
        data.writeBytes("IHDR");
        data.writeInt(100000);
        data.writeInt(100000);
        data.write(new byte[] {8, 2, 0, 0, 0});
        data.writeInt(0);
        Path png = temp.resolve("oversized.png");
        Files.write(png, buffer.toByteArray());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ImageVariants.create(png))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensions");
    }

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
