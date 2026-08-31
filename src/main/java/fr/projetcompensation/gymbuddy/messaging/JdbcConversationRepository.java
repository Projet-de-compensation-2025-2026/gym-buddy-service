package fr.projetcompensation.gymbuddy.messaging;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
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
public class JdbcConversationRepository implements ConversationRepository {

    private static final String SELECT_CONVERSATION = "SELECT id, user_lo, user_hi, created_at FROM conversations ";

    private final JdbcTemplate jdbc;

    public JdbcConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Conversation conversation) {
        jdbc.update(
                """
                INSERT INTO conversations (id, user_lo, user_hi, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_lo, user_hi) DO NOTHING
                """,
                conversation.id(),
                conversation.userLo(),
                conversation.userHi(),
                Timestamp.from(conversation.createdAt()));
    }

    @Override
    public Optional<Conversation> findById(UUID id) {
        return jdbc.query(SELECT_CONVERSATION + "WHERE id = ?", this::mapConversation, id).stream()
                .findFirst();
    }

    @Override
    public Optional<Conversation> findPair(UUID left, UUID right) {
        return jdbc
                .query(
                        SELECT_CONVERSATION + "WHERE user_lo = ? AND user_hi = ?",
                        this::mapConversation,
                        Conversation.lo(left, right),
                        Conversation.hi(left, right))
                .stream()
                .findFirst();
    }

    @Override
    public List<InboxRow> listInbox(UUID userId, InstantIdCursor before, int limit) {
        String sql = inboxSql() + " WHERE (c.user_lo = ? OR c.user_hi = ?) ";
        if (before == null) {
            return jdbc.query(sql + """
                            ORDER BY COALESCE(m.created_at, c.created_at) DESC, c.id DESC
                            LIMIT ?
                            """, this::mapInbox, userId, userId, userId, userId, limit);
        }
        return jdbc.query(
                sql + """
                        AND (COALESCE(m.created_at, c.created_at), c.id) < (?, ?)
                        ORDER BY COALESCE(m.created_at, c.created_at) DESC, c.id DESC
                        LIMIT ?
                        """,
                this::mapInbox,
                userId,
                userId,
                userId,
                userId,
                Timestamp.from(before.at()),
                before.id(),
                limit);
    }

    private static String inboxSql() {
        return """
                SELECT c.id, c.user_lo, c.user_hi, c.created_at,
                       m.id AS message_id, m.conversation_id, m.sender_id, m.type, m.body, m.media_id,
                       m.created_at AS message_created_at, m.deleted_at,
                       (
                         SELECT COUNT(*) FROM messages u
                         WHERE u.conversation_id = c.id
                           AND u.sender_id <> ?
                           AND u.deleted_at IS NULL
                           AND u.created_at > COALESCE((
                             SELECT r.last_read_at FROM conversation_reads r
                             WHERE r.conversation_id = c.id AND r.user_id = ?
                           ), TIMESTAMPTZ 'epoch')
                       ) AS unread_count
                FROM conversations c
                LEFT JOIN LATERAL (
                  SELECT * FROM messages
                  WHERE conversation_id = c.id
                  ORDER BY created_at DESC, id DESC
                  LIMIT 1
                ) m ON TRUE
                """;
    }

    @Override
    public Optional<InboxRow> inboxRow(UUID conversationId, UUID userId) {
        List<InboxRow> rows =
                jdbc.query(inboxSql() + """
                        WHERE c.id = ? AND (c.user_lo = ? OR c.user_hi = ?)
                        """, this::mapInbox, userId, userId, conversationId, userId, userId);
        return rows.stream().findFirst();
    }

    @Override
    public void markRead(UUID conversationId, UUID userId, Instant at) {
        jdbc.update("""
                INSERT INTO conversation_reads (conversation_id, user_id, last_read_at)
                VALUES (?, ?, ?)
                ON CONFLICT (conversation_id, user_id)
                DO UPDATE SET last_read_at = EXCLUDED.last_read_at
                """, conversationId, userId, Timestamp.from(at));
    }

    private Conversation mapConversation(ResultSet rs, int rowNum) throws SQLException {
        return new Conversation(
                rs.getObject("id", UUID.class),
                rs.getObject("user_lo", UUID.class),
                rs.getObject("user_hi", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private InboxRow mapInbox(ResultSet rs, int rowNum) throws SQLException {
        Conversation conversation = new Conversation(
                rs.getObject("id", UUID.class),
                rs.getObject("user_lo", UUID.class),
                rs.getObject("user_hi", UUID.class),
                rs.getTimestamp("created_at").toInstant());
        UUID messageId = rs.getObject("message_id", UUID.class);
        Message last = null;
        if (messageId != null) {
            Timestamp deleted = rs.getTimestamp("deleted_at");
            last = new Message(
                    messageId,
                    rs.getObject("conversation_id", UUID.class),
                    rs.getObject("sender_id", UUID.class),
                    MessageType.fromWire(rs.getString("type")),
                    rs.getString("body"),
                    rs.getObject("media_id", UUID.class),
                    rs.getTimestamp("message_created_at").toInstant(),
                    deleted == null ? null : deleted.toInstant());
        }
        return new InboxRow(conversation, last, rs.getLong("unread_count"));
    }
}
