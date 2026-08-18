package fr.projetcompensation.gymbuddy.auth;

import java.util.Optional;

/**
 * FS-ACCT-03: password ≥ 10 characters and not equal to email or handle.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    public Optional<FieldIssue> validate(String password, String email, String handle) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of(new FieldIssue("password", "minLength"));
        }
        if (equalsIgnoreCase(password, email) || equalsIgnoreCase(password, handle)) {
            return Optional.of(new FieldIssue("password", "mustNotMatchIdentity"));
        }
        return Optional.empty();
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
