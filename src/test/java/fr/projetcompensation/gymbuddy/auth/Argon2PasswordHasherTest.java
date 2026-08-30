package fr.projetcompensation.gymbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    @Test
    void fsAcct03_hashIsArgon2idAndDoesNotContainThePassword() {
        String password = "correct-horse";

        String hash = hasher.hash(password);

        assertThat(hash).startsWith("$argon2id$");
        assertThat(hash).contains("m=" + Argon2PasswordHasher.MEMORY_KIB);
        assertThat(hash).doesNotContain(password);
        assertThat(hasher.matches(password, hash)).isTrue();
        assertThat(hasher.matches("wrong-password", hash)).isFalse();
    }
}
