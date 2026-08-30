package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        UUID actorId,
        String actorHandle,
        String action,
        String targetType,
        UUID targetId,
        String reason,
        Instant at) {

    static final String LOCK_USER = "lock_user";
    static final String UNLOCK_USER = "unlock_user";
    static final String CHANGE_ROLE = "change_role";
    static final String HIDE_CONTENT = "hide_content";
    static final String UNHIDE_CONTENT = "unhide_content";
    static final String RESOLVE_REPORT = "resolve_report";
    static final String GENERATE_FIXTURES = "generate_fixtures";
    static final String RESET_FIXTURES = "reset_fixtures";

    InstantIdCursor cursor() {
        return new InstantIdCursor(at, id);
    }
}
