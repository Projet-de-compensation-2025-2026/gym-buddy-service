package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcAuditEventRepository implements AuditEventRepository {

    private static final String SELECT = """
            SELECT a.id, a.actor_id, u.handle AS actor_handle, a.action, a.target_type, a.target_id, a.reason, a.at
            FROM audit_events a
            JOIN users u ON u.id = a.actor_id
            """;

    private final JdbcTemplate jdbc;

    public JdbcAuditEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AuditEvent event) {
        jdbc.update(
                """
                INSERT INTO audit_events (id, actor_id, action, target_type, target_id, reason, at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                event.id(),
                event.actorId(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.reason(),
                Timestamp.from(event.at()));
    }

    @Override
    public List<AuditEvent> list(
            UUID actorId, boolean contentOnly, String q, String action, InstantIdCursor after, int limit) {
        String like = q == null ? null : "%" + q + "%";
        if (after == null) {
            return jdbc.query(
                    SELECT + """
                              WHERE (?::uuid IS NULL OR a.actor_id = ?)
                                AND (? = FALSE OR a.action IN ('hide_content', 'unhide_content') OR a.actor_id = ?)
                                AND (? IS NULL OR a.action = ?)
                                AND (? IS NULL OR u.handle ILIKE ? OR a.action ILIKE ? OR COALESCE(a.reason, '') ILIKE ?)
                              ORDER BY a.at DESC, a.id DESC
                              LIMIT ?
                              """,
                    this::map,
                    actorId,
                    actorId,
                    contentOnly,
                    actorId,
                    action,
                    action,
                    like,
                    like,
                    like,
                    like,
                    limit);
        }
        return jdbc.query(
                SELECT + """
                          WHERE (?::uuid IS NULL OR a.actor_id = ?)
                            AND (? = FALSE OR a.action IN ('hide_content', 'unhide_content') OR a.actor_id = ?)
                            AND (? IS NULL OR a.action = ?)
                            AND (? IS NULL OR u.handle ILIKE ? OR a.action ILIKE ? OR COALESCE(a.reason, '') ILIKE ?)
                            AND (a.at, a.id) < (?, ?)
                          ORDER BY a.at DESC, a.id DESC
                          LIMIT ?
                          """,
                this::map,
                actorId,
                actorId,
                contentOnly,
                actorId,
                action,
                action,
                like,
                like,
                like,
                like,
                Timestamp.from(after.at()),
                after.id(),
                limit);
    }

    private AuditEvent map(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_handle"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getString("reason"),
                rs.getTimestamp("at").toInstant());
    }
}
