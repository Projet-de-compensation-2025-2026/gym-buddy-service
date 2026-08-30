package fr.projetcompensation.gymbuddy.users;

public final class DuplicateUserException extends RuntimeException {

    public DuplicateUserException() {
        super("email or handle already exists");
    }
}
