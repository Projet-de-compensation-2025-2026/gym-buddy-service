package fr.projetcompensation.gymbuddy.auth;

public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String passwordHash);
}
