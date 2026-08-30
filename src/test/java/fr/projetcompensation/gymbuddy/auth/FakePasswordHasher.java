package fr.projetcompensation.gymbuddy.auth;

final class FakePasswordHasher implements PasswordHasher {

    @Override
    public String hash(String password) {
        return "hashed:" + password;
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        return hash(password).equals(passwordHash);
    }
}
