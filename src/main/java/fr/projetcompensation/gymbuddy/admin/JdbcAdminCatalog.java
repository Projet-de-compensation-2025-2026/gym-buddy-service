package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaKind;
import fr.projetcompensation.gymbuddy.media.MediaStatus;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
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
public class JdbcAdminCatalog implements AdminCatalog {

    private final JdbcTemplate jdbc;

    public JdbcAdminCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ListedAdminUser> listUsers(String q, String role, String status, InstantIdCursor after, int limit) {
        String like = q == null ? null : "%" + q + "%";
        long admins = countAdmins();
        if (after == null) {
            return jdbc.query(
                    """
                    SELECT u.id, u.email, u.handle, u.password_hash, u.role, u.status, u.created_at, p.display_name
                    FROM users u
                    JOIN profiles p ON p.user_id = u.id
                    WHERE (? IS NULL OR u.handle ILIKE ? OR u.email ILIKE ? OR p.display_name ILIKE ?)
                      AND (? IS NULL OR u.role = ?)
                      AND (? IS NULL OR u.status = ?)
                    ORDER BY u.created_at DESC, u.id DESC
                    LIMIT ?
                    """,
                    (rs, rowNum) -> mapUser(rs, admins),
                    like,
                    like,
                    like,
                    like,
                    role,
                    role,
                    status,
                    status,
                    limit);
        }
        return jdbc.query(
                """
                SELECT u.id, u.email, u.handle, u.password_hash, u.role, u.status, u.created_at, p.display_name
                FROM users u
                JOIN profiles p ON p.user_id = u.id
                WHERE (? IS NULL OR u.handle ILIKE ? OR u.email ILIKE ? OR p.display_name ILIKE ?)
                  AND (? IS NULL OR u.role = ?)
                  AND (? IS NULL OR u.status = ?)
                  AND (u.created_at, u.id) < (?, ?)
                ORDER BY u.created_at DESC, u.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapUser(rs, admins),
                like,
                like,
                like,
                like,
                role,
                role,
                status,
                status,
                Timestamp.from(after.at()),
                after.id(),
                limit);
    }

    @Override
    public long countAdmins() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'admin'", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<ListedAdminContent> listContent(
            String type, String q, Boolean hidden, InstantIdCursor after, int limit) {
        String like = q == null ? null : "%" + q + "%";
        String cursor = after == null ? "" : " AND (row.created_at, row.id) < (?, ?) ";
        String sql =
                switch (type) {
                    case "post" -> """
                            SELECT row.type, row.id, row.author_handle, row.summary, row.created_at,
                                   row.hidden, row.hidden_reason
                            FROM (
                              SELECT 'post' AS type, p.id, u.handle AS author_handle,
                                     COALESCE(LEFT(p.body, 280), '') AS summary,
                                     p.created_at, (p.hidden_at IS NOT NULL) AS hidden, p.hidden_reason
                              FROM posts p
                              JOIN users u ON u.id = p.author_id
                              WHERE p.deleted_at IS NULL
                                AND (? IS NULL OR u.handle ILIKE ? OR p.body ILIKE ? OR p.id::text ILIKE ?)
                                AND (? IS NULL OR (p.hidden_at IS NOT NULL) = ?)
                            ) row
                            WHERE TRUE
                            """ + cursor + " ORDER BY row.created_at DESC, row.id DESC LIMIT ?";
                    case "comment" -> """
                            SELECT row.type, row.id, row.author_handle, row.summary, row.created_at,
                                   row.hidden, row.hidden_reason
                            FROM (
                              SELECT 'comment' AS type, c.id, u.handle AS author_handle,
                                     COALESCE(LEFT(c.body, 280), '') AS summary,
                                     c.created_at, (c.hidden_at IS NOT NULL) AS hidden, c.hidden_reason
                              FROM comments c
                              JOIN users u ON u.id = c.author_id
                              WHERE (? IS NULL OR u.handle ILIKE ? OR c.body ILIKE ? OR c.id::text ILIKE ?)
                                AND (? IS NULL OR (c.hidden_at IS NOT NULL) = ?)
                            ) row
                            WHERE TRUE
                            """ + cursor + " ORDER BY row.created_at DESC, row.id DESC LIMIT ?";
                    case "event" -> """
                            SELECT row.type, row.id, row.author_handle, row.summary, row.created_at,
                                   row.hidden, row.hidden_reason
                            FROM (
                              SELECT 'event' AS type, e.id, u.handle AS author_handle,
                                     COALESCE(LEFT(e.title, 280), '') AS summary,
                                     e.created_at, (e.hidden_at IS NOT NULL) AS hidden, NULL::text AS hidden_reason
                              FROM events e
                              JOIN users u ON u.id = e.organizer_id
                              WHERE (? IS NULL OR u.handle ILIKE ? OR e.title ILIKE ? OR e.id::text ILIKE ?)
                                AND (? IS NULL OR (e.hidden_at IS NOT NULL) = ?)
                            ) row
                            WHERE TRUE
                            """ + cursor + " ORDER BY row.created_at DESC, row.id DESC LIMIT ?";
                    case "media" -> """
                            SELECT row.type, row.id, row.author_handle, row.summary, row.created_at,
                                   row.hidden, row.hidden_reason
                            FROM (
                              SELECT 'media' AS type, m.id, u.handle AS author_handle,
                                     COALESCE(LEFT(m.object_key, 280), '') AS summary,
                                     m.created_at, (m.hidden_at IS NOT NULL) AS hidden, m.hidden_reason
                              FROM media m
                              JOIN users u ON u.id = m.owner_id
                              WHERE m.deleted_at IS NULL
                                AND (? IS NULL OR u.handle ILIKE ? OR m.object_key ILIKE ? OR m.id::text ILIKE ?)
                                AND (? IS NULL OR (m.hidden_at IS NOT NULL) = ?)
                            ) row
                            WHERE TRUE
                            """ + cursor + " ORDER BY row.created_at DESC, row.id DESC LIMIT ?";
                    default -> throw new IllegalArgumentException("type");
                };
        if (after == null) {
            return jdbc.query(sql, this::mapContent, like, like, like, like, hidden, hidden, limit);
        }
        return jdbc.query(
                sql,
                this::mapContent,
                like,
                like,
                like,
                like,
                hidden,
                hidden,
                Timestamp.from(after.at()),
                after.id(),
                limit);
    }

    @Override
    public List<ListedAdminMedia> listMedia(String q, InstantIdCursor after, int limit) {
        String like = q == null ? null : "%" + q + "%";
        if (after == null) {
            return jdbc.query("""
                    SELECT m.id, m.owner_id, u.handle AS owner_handle, m.kind, m.mime, m.bytes, m.variant_bytes,
                           m.status, m.object_key, m.created_at, m.deleted_at, m.hidden_at, m.hidden_reason
                    FROM media m
                    JOIN users u ON u.id = m.owner_id
                    WHERE (? IS NULL OR u.handle ILIKE ? OR m.object_key ILIKE ? OR m.id::text ILIKE ?)
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT ?
                    """, this::mapMedia, like, like, like, like, limit);
        }
        return jdbc.query("""
                SELECT m.id, m.owner_id, u.handle AS owner_handle, m.kind, m.mime, m.bytes, m.variant_bytes,
                       m.status, m.object_key, m.created_at, m.deleted_at, m.hidden_at, m.hidden_reason
                FROM media m
                JOIN users u ON u.id = m.owner_id
                WHERE (? IS NULL OR u.handle ILIKE ? OR m.object_key ILIKE ? OR m.id::text ILIKE ?)
                  AND (m.created_at, m.id) < (?, ?)
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT ?
                """, this::mapMedia, like, like, like, like, Timestamp.from(after.at()), after.id(), limit);
    }

    private ListedAdminUser mapUser(ResultSet rs, long admins) throws SQLException {
        User user = new User(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("handle"),
                rs.getString("password_hash"),
                UserRole.fromWire(rs.getString("role")),
                UserStatus.fromWire(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
        boolean lastAdmin = user.role() == UserRole.ADMIN && admins <= 1;
        return new ListedAdminUser(user, rs.getString("display_name"), lastAdmin);
    }

    private ListedAdminContent mapContent(ResultSet rs, int rowNum) throws SQLException {
        return new ListedAdminContent(
                rs.getString("type"),
                rs.getObject("id", UUID.class),
                rs.getString("author_handle"),
                rs.getString("summary"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("hidden"),
                rs.getString("hidden_reason"));
    }

    private ListedAdminMedia mapMedia(ResultSet rs, int rowNum) throws SQLException {
        Timestamp deleted = rs.getTimestamp("deleted_at");
        Timestamp hidden = rs.getTimestamp("hidden_at");
        Media media = new Media(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                MediaKind.fromWire(rs.getString("kind")),
                rs.getString("mime"),
                rs.getLong("bytes"),
                rs.getLong("variant_bytes"),
                MediaStatus.fromWire(rs.getString("status")),
                rs.getString("object_key"),
                rs.getTimestamp("created_at").toInstant(),
                deleted == null ? null : deleted.toInstant(),
                hidden == null ? null : hidden.toInstant(),
                rs.getString("hidden_reason"));
        return new ListedAdminMedia(media, rs.getString("owner_handle"));
    }
}
