package fr.projetcompensation.gymbuddy.messaging;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcMessageRepository implements MessageRepository {

    private static final String SELECT = """
            SELECT id, conversation_id, sender_id, type, body, media_id, created_at, deleted_at
            FROM messages
            """;

    private final JdbcTemplate jdbc;

    public JdbcMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Message message) {
        jdbc.update(
                """
                INSERT INTO messages (id, conversation_id, sender_id, type, body, media_id, created_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                message.id(),
                message.conversationId(),
                message.senderId(),
                message.type().wireValue(),
                message.body(),
                message.mediaId(),
                Timestamp.from(message.createdAt()),
                message.deletedAt() == null ? null : Timestamp.from(message.deletedAt()));
    }

    @Override
    public void update(Message message) {
        jdbc.update(
                """
                UPDATE messages
                SET body = ?, media_id = ?, deleted_at = ?
                WHERE id = ?
                """,
                message.body(),
                message.mediaId(),
                message.deletedAt() == null ? null : Timestamp.from(message.deletedAt()),
                message.id());
    }

    @Override
    public Optional<Message> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public Optional<Message> findByMediaId(UUID mediaId) {
        return jdbc.query(SELECT + " WHERE media_id = ?", this::map, mediaId).stream()
                .findFirst();
    }

    @Override
    public List<Message> list(UUID conversationId, InstantIdCursor before, int limit) {
        if (before == null) {
            return jdbc.query(SELECT + """
                            WHERE conversation_id = ?
                            ORDER BY created_at DESC, id DESC
                            LIMIT ?
                            """, this::map, conversationId, limit);
        }
        return jdbc.query(SELECT + """
                        WHERE conversation_id = ?
                          AND (created_at, id) < (?, ?)
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """, this::map, conversationId, Timestamp.from(before.at()), before.id(), limit);
    }

    private Message map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp deleted = rs.getTimestamp("deleted_at");
        return new Message(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getObject("sender_id", UUID.class),
                MessageType.fromWire(rs.getString("type")),
                rs.getString("body"),
                rs.getObject("media_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                deleted == null ? null : deleted.toInstant());
    }
}
