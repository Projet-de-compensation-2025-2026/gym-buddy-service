package fr.projetcompensation.gymbuddy.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;

final class AudioDuration {

    private AudioDuration() {}

    static OptionalDouble seconds(byte[] data, String mime) {
        if (data == null || mime == null) {
            return OptionalDouble.empty();
        }
        return switch (mime) {
            case "audio/mpeg" -> mpegSeconds(data);
            case "audio/webm" -> webmSeconds(data);
            default -> OptionalDouble.empty();
        };
    }

    private static OptionalDouble mpegSeconds(byte[] data) {
        int offset = 0;
        if (data.length >= 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            int size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
            offset = 10 + size;
        }
        if (offset + 4 > data.length) {
            return OptionalDouble.empty();
        }
        int header =
                ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if ((header & 0xFFE00000) != 0xFFE00000) {
            return OptionalDouble.empty();
        }
        int versionBits = (header >> 19) & 0x3;
        int layerBits = (header >> 17) & 0x3;
        int bitrateIndex = (header >> 12) & 0xF;
        int sampleIndex = (header >> 10) & 0x3;
        if (bitrateIndex == 0 || bitrateIndex == 15 || sampleIndex == 3 || layerBits == 0) {
            return OptionalDouble.empty();
        }
        int sampleRate =
                switch (versionBits) {
                    case 3 -> new int[] {44100, 48000, 32000}[sampleIndex];
                    case 2 -> new int[] {22050, 24000, 16000}[sampleIndex];
                    default -> new int[] {11025, 12000, 8000}[sampleIndex];
                };
        int[] mpeg1Layer3 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
        int[] mpeg2Layer3 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
        int bitrateKbps = versionBits == 3 ? mpeg1Layer3[bitrateIndex] : mpeg2Layer3[bitrateIndex];
        if (bitrateKbps == 0 || sampleRate == 0) {
            return OptionalDouble.empty();
        }
        int payload = data.length - offset;
        return OptionalDouble.of((payload * 8.0) / (bitrateKbps * 1000.0));
    }

    private static OptionalDouble webmSeconds(byte[] data) {
        // EBML Duration element id 0x4489, stored as float. Fail closed if missing.
        for (int i = 0; i + 10 < data.length; i++) {
            if ((data[i] & 0xFF) == 0x44 && (data[i + 1] & 0xFF) == 0x89) {
                int size = data[i + 2] & 0xFF;
                int length;
                int valueAt;
                if (size == 0x84) {
                    length = 4;
                    valueAt = i + 3;
                } else if (size == 0x88) {
                    length = 8;
                    valueAt = i + 3;
                } else if ((size & 0x80) != 0) {
                    length = size & 0x7F;
                    valueAt = i + 3;
                } else {
                    continue;
                }
                if (valueAt + length > data.length || (length != 4 && length != 8)) {
                    continue;
                }
                ByteBuffer buffer = ByteBuffer.wrap(data, valueAt, length).order(ByteOrder.BIG_ENDIAN);
                double seconds = length == 4 ? buffer.getFloat() : buffer.getDouble();
                if (seconds > 0 && Double.isFinite(seconds)) {
                    return OptionalDouble.of(seconds / 1000.0 > 120 ? seconds / 1000.0 : seconds);
                }
            }
        }
        return OptionalDouble.empty();
    }
}
