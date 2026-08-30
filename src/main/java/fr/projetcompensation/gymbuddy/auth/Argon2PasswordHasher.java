package fr.projetcompensation.gymbuddy.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Argon2id with memory ≥ 19 MiB (FS-ACCT-03). Never log the password argument.
 */
public final class Argon2PasswordHasher implements PasswordHasher {

    static final int MEMORY_KIB = 19 * 1024;

    private final Argon2PasswordEncoder encoder;

    public Argon2PasswordHasher() {
        this(new Argon2PasswordEncoder(16, 32, 1, MEMORY_KIB, 2));
    }

    Argon2PasswordHasher(Argon2PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String password) {
        return encoder.encode(password);
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        return encoder.matches(password, passwordHash);
    }
}
