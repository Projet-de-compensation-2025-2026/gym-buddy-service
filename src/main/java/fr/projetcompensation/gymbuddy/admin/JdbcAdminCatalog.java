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
    public List<ListedAdminMedia> listMedia(String q, InstantIdCursor after, int limit) {
        String like = q == null ? null : "%" + q + "%";
        if (after == null) {
            return jdbc.query(
                    """
                    SELECT m.id, m.owner_id, u.handle AS owner_handle, m.kind, m.mime, m.bytes, m.variant_bytes,
                           m.status, m.object_key, m.created_at, m.deleted_at, m.hidden_at, m.hidden_reason
                    FROM media m
                    JOIN users u ON u.id = m.owner_id
                    WHERE (? IS NULL OR u.handle ILIKE ? OR m.object_key ILIKE ? OR m.id::text ILIKE ?)
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT ?
                    """,
                    this::mapMedia,
                    like,
                    like,
                    like,
                    like,
                    limit);
        }
        return jdbc.query(
                """
                SELECT m.id, m.owner_id, u.handle AS owner_handle, m.kind, m.mime, m.bytes, m.variant_bytes,
                       m.status, m.object_key, m.created_at, m.deleted_at, m.hidden_at, m.hidden_reason
                FROM media m
                JOIN users u ON u.id = m.owner_id
                WHERE (? IS NULL OR u.handle ILIKE ? OR m.object_key ILIKE ? OR m.id::text ILIKE ?)
                  AND (m.created_at, m.id) < (?, ?)
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT ?
                """,
                this::mapMedia,
                like,
                like,
                like,
                like,
                Timestamp.from(after.at()),
                after.id(),
                limit);
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
