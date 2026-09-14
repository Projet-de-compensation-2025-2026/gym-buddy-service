package fr.projetcompensation.gymbuddy.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;

final class ImageVariants {

    record Pair(byte[] sm, byte[] md) {}

    private ImageVariants() {}

    static Pair create(Path original) {
        BufferedImage source = read(original);
        try {
            if (source.getWidth() > MediaRules.MAX_DIMENSION_PX || source.getHeight() > MediaRules.MAX_DIMENSION_PX) {
                throw new IllegalArgumentException("dimensions exceed 8000 px");
            }
            long pixels = (long) source.getWidth() * source.getHeight();
            if (pixels > MediaRules.MAX_PIXELS) {
                throw new IllegalArgumentException("decompressed pixel count exceeds cap");
            }
            BufferedImage clean = stripMetadata(source);
            return new Pair(
                    scaleWebp(clean, MediaRules.VARIANT_SM_WIDTH), scaleWebp(clean, MediaRules.VARIANT_MD_WIDTH));
        } finally {
            source.flush();
        }
    }

    private static BufferedImage read(Path original) {
        try (InputStream in = Files.newInputStream(original)) {
            try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(in)) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("unreadable image");
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1
                            || height < 1
                            || width > MediaRules.MAX_DIMENSION_PX
                            || height > MediaRules.MAX_DIMENSION_PX
                            || (long) width * height > MediaRules.MAX_PIXELS) {
                        throw new IllegalArgumentException("image dimensions exceed limit");
                    }
                    return reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("unreadable image", ex);
        }
    }

    static byte[] sanitize(Path original, String mime) {
        BufferedImage source = read(original);
        try {
            BufferedImage clean = stripMetadata(source);
            try {
                if ("image/webp".equals(mime)) return writeWebp(clean);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(clean, "image/jpeg".equals(mime) ? "jpeg" : "png", output)) {
                    throw new IllegalArgumentException("unsupported image format");
                }
                return output.toByteArray();
            } finally {
                clean.flush();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            source.flush();
        }
    }

    private static BufferedImage stripMetadata(BufferedImage source) {
        BufferedImage clean = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = clean.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, clean.getWidth(), clean.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return clean;
    }

    private static byte[] scaleWebp(BufferedImage source, int maxWidth) {
        try {
            int width = Math.min(maxWidth, source.getWidth());
            BufferedImage scaled =
                    Thumbnails.of(source).width(width).keepAspectRatio(true).asBufferedImage();
            return writeWebp(scaled);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static byte[] writeWebp(BufferedImage image) {
        ImageIO.scanForPlugins();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            writers = ImageIO.getImageWritersByFormatName("webp");
        }
        if (!writers.hasNext()) {
            throw new IllegalStateException("No WebP ImageWriter is registered");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0) {
                    String chosen = types[0];
                    for (String type : types) {
                        if (type.toLowerCase().contains("lossless")) {
                            chosen = type;
                            break;
                        }
                    }
                    param.setCompressionType(chosen);
                }
            }
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
