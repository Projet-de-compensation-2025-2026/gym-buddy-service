package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository {

    void save(AuditEvent event);

    List<AuditEvent> list(UUID actorId, boolean contentOnly, String q, String action, InstantIdCursor after, int limit);
}
