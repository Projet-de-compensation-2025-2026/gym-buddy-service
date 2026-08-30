package fr.projetcompensation.gymbuddy.comments;

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
public class JdbcCommentRepository implements CommentRepository {

    private static final String SELECT = """
            SELECT id, post_id, author_id, parent_id, body, depth, created_at, deleted_at, hidden_at
            FROM comments
            """;

    private final JdbcTemplate jdbc;

    public JdbcCommentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Comment comment) {
        jdbc.update(
                """
                INSERT INTO comments (id, post_id, author_id, parent_id, body, depth, created_at, deleted_at, hidden_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                comment.id(),
                comment.postId(),
                comment.authorId(),
                comment.parentId(),
                comment.body(),
                comment.depth(),
                Timestamp.from(comment.createdAt()),
                comment.deletedAt() == null ? null : Timestamp.from(comment.deletedAt()),
                comment.hiddenAt() == null ? null : Timestamp.from(comment.hiddenAt()));
    }

    @Override
    public void update(Comment comment) {
        jdbc.update(
                """
                UPDATE comments
                SET body = ?, deleted_at = ?, hidden_at = ?
                WHERE id = ?
                """,
                comment.body(),
                comment.deletedAt() == null ? null : Timestamp.from(comment.deletedAt()),
                comment.hiddenAt() == null ? null : Timestamp.from(comment.hiddenAt()),
                comment.id());
    }

    @Override
    public Optional<Comment> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public List<Comment> listRoots(UUID postId, InstantIdCursor before, int limit) {
        if (before == null) {
            return jdbc.query(SELECT + """
                            WHERE post_id = ? AND parent_id IS NULL
                            ORDER BY created_at DESC, id DESC
                            LIMIT ?
                            """, this::map, postId, limit);
        }
        return jdbc.query(SELECT + """
                        WHERE post_id = ? AND parent_id IS NULL
                          AND (created_at, id) < (?, ?)
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """, this::map, postId, Timestamp.from(before.at()), before.id(), limit);
    }

    @Override
    public List<Comment> listReplies(UUID parentId, InstantIdCursor before, int limit) {
        if (before == null) {
            return jdbc.query(SELECT + """
                            WHERE parent_id = ?
                            ORDER BY created_at ASC, id ASC
                            LIMIT ?
                            """, this::map, parentId, limit);
        }
        return jdbc.query(SELECT + """
                        WHERE parent_id = ?
                          AND (created_at, id) > (?, ?)
                        ORDER BY created_at ASC, id ASC
                        LIMIT ?
                        """, this::map, parentId, Timestamp.from(before.at()), before.id(), limit);
    }

    @Override
    public long replyCount(UUID parentId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE parent_id = ?", Long.class, parentId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean insertLike(UUID userId, UUID commentId, Instant at) {
        int rows = jdbc.update("""
                INSERT INTO likes (user_id, target_type, target_id, created_at)
                VALUES (?, 'comment', ?, ?)
                ON CONFLICT (user_id, target_type, target_id) DO NOTHING
                """, userId, commentId, Timestamp.from(at));
        return rows > 0;
    }

    @Override
    public boolean deleteLike(UUID userId, UUID commentId) {
        return jdbc.update(
                        "DELETE FROM likes WHERE user_id = ? AND target_type = 'comment' AND target_id = ?",
                        userId,
                        commentId)
                > 0;
    }

    @Override
    public boolean liked(UUID userId, UUID commentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE user_id = ? AND target_type = 'comment' AND target_id = ?",
                Integer.class,
                userId,
                commentId);
        return count != null && count > 0;
    }

    @Override
    public long likeCount(UUID commentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE target_type = 'comment' AND target_id = ?", Long.class, commentId);
        return count == null ? 0L : count;
    }

    private Comment map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp deleted = rs.getTimestamp("deleted_at");
        Timestamp hidden = rs.getTimestamp("hidden_at");
        return new Comment(
                rs.getObject("id", UUID.class),
                rs.getObject("post_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getString("body"),
                rs.getInt("depth"),
                created.toInstant(),
                deleted == null ? null : deleted.toInstant(),
                hidden == null ? null : hidden.toInstant());
    }
}
