package fr.projetcompensation.gymbuddy.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MagicBytesTest {

    @Test
    void acceptsKnownImageAndAudioHeaders() {
        assertThat(MagicBytes.matches(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}, "image/jpeg"))
                .isTrue();
        assertThat(MagicBytes.matches(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, "image/png"))
                .isTrue();
        assertThat(MagicBytes.matches("RIFF....WEBP".getBytes(), "image/webp")).isTrue();
        assertThat(MagicBytes.matches(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}, "audio/webm"))
                .isTrue();
        assertThat(MagicBytes.matches(new byte[] {'I', 'D', '3', 0x04}, "audio/mpeg"))
                .isTrue();
    }

    @Test
    void rejectsMismatchedPayloads() {
        assertThat(MagicBytes.matches("not-a-jpeg".getBytes(), "image/jpeg")).isFalse();
        assertThat(MagicBytes.matches(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/png"))
                .isFalse();
        assertThat(MagicBytes.matches(new byte[0], "image/webp")).isFalse();
    }
}
