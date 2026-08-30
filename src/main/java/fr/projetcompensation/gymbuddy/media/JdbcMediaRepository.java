package fr.projetcompensation.gymbuddy.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcMediaRepository implements MediaRepository {

    private static final String SELECT = """
            SELECT id, owner_id, kind, mime, bytes, variant_bytes, status, object_key, created_at, deleted_at,
                   hidden_at, hidden_reason
            FROM media
            """;

    private final JdbcTemplate jdbc;

    public JdbcMediaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Media media) {
        jdbc.update(
                """
                INSERT INTO media (id, owner_id, kind, mime, bytes, variant_bytes, status, object_key, created_at, deleted_at, hidden_at, hidden_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                media.id(),
                media.ownerId(),
                media.kind().wireValue(),
                media.mime(),
                media.bytes(),
                media.variantBytes(),
                media.status().wireValue(),
                media.objectKey(),
                Timestamp.from(media.createdAt()),
                media.deletedAt() == null ? null : Timestamp.from(media.deletedAt()),
                media.hiddenAt() == null ? null : Timestamp.from(media.hiddenAt()),
                media.hiddenReason());
    }

    @Override
    public void update(Media media) {
        jdbc.update(
                """
                UPDATE media
                SET kind = ?, mime = ?, bytes = ?, variant_bytes = ?, status = ?, object_key = ?, deleted_at = ?,
                    hidden_at = ?, hidden_reason = ?
                WHERE id = ?
                """,
                media.kind().wireValue(),
                media.mime(),
                media.bytes(),
                media.variantBytes(),
                media.status().wireValue(),
                media.objectKey(),
                media.deletedAt() == null ? null : Timestamp.from(media.deletedAt()),
                media.hiddenAt() == null ? null : Timestamp.from(media.hiddenAt()),
                media.hiddenReason(),
                media.id());
    }

    @Override
    public void delete(UUID id) {
        jdbc.update("DELETE FROM media WHERE id = ?", id);
    }

    @Override
    public Optional<Media> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public long usedBytes(UUID ownerId) {
        Long used = jdbc.queryForObject("""
                SELECT COALESCE(SUM(bytes + variant_bytes), 0)
                FROM media
                WHERE owner_id = ? AND deleted_at IS NULL
                """, Long.class, ownerId);
        return used == null ? 0L : used;
    }

    @Override
    public List<Media> findPendingCreatedBefore(Instant cutoff) {
        return jdbc.query(
                SELECT + " WHERE status = 'pending' AND deleted_at IS NULL AND created_at < ?",
                this::map,
                Timestamp.from(cutoff));
    }

    @Override
    public List<Media> findPending() {
        return jdbc.query(SELECT + " WHERE status = 'pending' AND deleted_at IS NULL", this::map);
    }

    @Override
    public List<Media> findDeletedBefore(Instant cutoff) {
        return jdbc.query(
                SELECT + " WHERE status = 'deleted' AND deleted_at IS NOT NULL AND deleted_at < ?",
                this::map,
                Timestamp.from(cutoff));
    }

    private Media map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp deleted = rs.getTimestamp("deleted_at");
        Timestamp hidden = rs.getTimestamp("hidden_at");
        return new Media(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                MediaKind.fromWire(rs.getString("kind")),
                rs.getString("mime"),
                rs.getLong("bytes"),
                rs.getLong("variant_bytes"),
                MediaStatus.fromWire(rs.getString("status")),
                rs.getString("object_key"),
                created.toInstant(),
                deleted == null ? null : deleted.toInstant(),
                hidden == null ? null : hidden.toInstant(),
                rs.getString("hidden_reason"));
    }
}
