package fr.projetcompensation.gymbuddy.auth;

public final class HandleRules {

    private HandleRules() {}

    public static String requireHandle(String handle, String email) {
        if (handle == null || handle.isBlank()) {
            throw AuthException.validation("handle is required", new FieldIssue("handle", "required"));
        }
        String trimmed = handle.trim();
        if (trimmed.indexOf('@') >= 0) {
            throw AuthException.validation("handle must not be an email", new FieldIssue("handle", "format"));
        }
        if (email != null && trimmed.equalsIgnoreCase(email.trim())) {
            throw AuthException.validation("handle must not be an email", new FieldIssue("handle", "format"));
        }
        return trimmed;
    }
}
