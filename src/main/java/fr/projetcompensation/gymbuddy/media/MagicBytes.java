package fr.projetcompensation.gymbuddy.media;

final class MagicBytes {

    private MagicBytes() {}

    static boolean matches(byte[] data, String mime) {
        if (data == null || data.length == 0 || mime == null) {
            return false;
        }
        return switch (mime) {
            case "image/jpeg" -> jpeg(data);
            case "image/png" -> png(data);
            case "image/webp" -> webp(data);
            case "audio/webm" -> webm(data);
            case "audio/mpeg" -> mpeg(data);
            default -> false;
        };
    }

    private static boolean jpeg(byte[] data) {
        return data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
    }

    private static boolean png(byte[] data) {
        return data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A;
    }

    private static boolean webp(byte[] data) {
        return data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P';
    }

    private static boolean webm(byte[] data) {
        return data.length >= 4
                && (data[0] & 0xFF) == 0x1A
                && (data[1] & 0xFF) == 0x45
                && (data[2] & 0xFF) == 0xDF
                && (data[3] & 0xFF) == 0xA3;
    }

    private static boolean mpeg(byte[] data) {
        if (data.length >= 3 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            return true;
        }
        return data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0;
    }
}
