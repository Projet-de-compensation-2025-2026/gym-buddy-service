package fr.projetcompensation.gymbuddy.posts;

import fr.projetcompensation.gymbuddy.feed.FeedActivity;
import fr.projetcompensation.gymbuddy.feed.FeedKind;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.sql.PreparedStatement;
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
public class JdbcPostRepository implements PostRepository {

    private static final String SELECT = """
            SELECT id, author_id, body, visibility, created_at, edited_at, deleted_at, hidden_at, hidden_reason
            FROM posts
            """;

    private static final String FEED_CTE = """
            WITH actors AS (
              SELECT CAST(? AS uuid) AS user_id
              UNION
              SELECT CASE WHEN requester_id = ? THEN addressee_id ELSE requester_id END
              FROM friendships
              WHERE status = 'accepted' AND (requester_id = ? OR addressee_id = ?)
            ),
            visible_posts AS (
              SELECT p.id, p.author_id, p.created_at
              FROM posts p
              JOIN users u ON u.id = p.author_id AND u.status = 'active'
              WHERE p.deleted_at IS NULL
                AND p.hidden_at IS NULL
                AND NOT EXISTS (
                  SELECT 1 FROM friendships f
                  WHERE f.status = 'blocked'
                    AND LEAST(f.requester_id, f.addressee_id) = LEAST(?, p.author_id)
                    AND GREATEST(f.requester_id, f.addressee_id) = GREATEST(?, p.author_id)
                )
                AND (
                  p.author_id = ?
                  OR p.visibility = 'public'
                  OR (
                    p.visibility = 'friends'
                    AND EXISTS (
                      SELECT 1 FROM friendships f
                      WHERE f.status = 'accepted'
                        AND LEAST(f.requester_id, f.addressee_id) = LEAST(?, p.author_id)
                        AND GREATEST(f.requester_id, f.addressee_id) = GREATEST(?, p.author_id)
                    )
                  )
                )
            ),
            activities AS (
              SELECT p.id AS activity_id,
                     'post' AS kind,
                     p.author_id AS actor_id,
                     p.id AS post_id,
                     p.created_at AS activity_at
              FROM visible_posts p
              JOIN actors a ON a.user_id = p.author_id
              UNION ALL
              SELECT r.id,
                     'repost',
                     r.user_id,
                     r.post_id,
                     r.created_at
              FROM reposts r
              JOIN actors a ON a.user_id = r.user_id
              JOIN users actor ON actor.id = r.user_id AND actor.status = 'active'
              JOIN visible_posts p ON p.id = r.post_id
            )
            """;

    private final JdbcTemplate jdbc;

    public JdbcPostRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Post post, List<UUID> mediaIds) {
        jdbc.update(
                """
                INSERT INTO posts (id, author_id, body, visibility, created_at, edited_at, deleted_at, hidden_at, hidden_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                post.id(),
                post.authorId(),
                post.body(),
                post.visibility().wireValue(),
                Timestamp.from(post.createdAt()),
                post.editedAt() == null ? null : Timestamp.from(post.editedAt()),
                post.deletedAt() == null ? null : Timestamp.from(post.deletedAt()),
                post.hiddenAt() == null ? null : Timestamp.from(post.hiddenAt()),
                post.hiddenReason());
        int position = 0;
        for (UUID mediaId : mediaIds) {
            jdbc.update(
                    "INSERT INTO post_media (post_id, media_id, position) VALUES (?, ?, ?)",
                    post.id(),
                    mediaId,
                    position);
            position++;
        }
    }

    @Override
    public void update(Post post) {
        jdbc.update(
                """
                UPDATE posts
                SET body = ?, visibility = ?, edited_at = ?, deleted_at = ?, hidden_at = ?, hidden_reason = ?
                WHERE id = ?
                """,
                post.body(),
                post.visibility().wireValue(),
                post.editedAt() == null ? null : Timestamp.from(post.editedAt()),
                post.deletedAt() == null ? null : Timestamp.from(post.deletedAt()),
                post.hiddenAt() == null ? null : Timestamp.from(post.hiddenAt()),
                post.hiddenReason(),
                post.id());
    }

    @Override
    public Optional<Post> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public Optional<Post> findByMediaId(UUID mediaId) {
        return jdbc
                .query(SELECT + " WHERE id = (SELECT post_id FROM post_media WHERE media_id = ?)", this::map, mediaId)
                .stream()
                .findFirst();
    }

    @Override
    public List<UUID> mediaIds(UUID postId) {
        return jdbc.query(
                "SELECT media_id FROM post_media WHERE post_id = ? ORDER BY position",
                (rs, rowNum) -> rs.getObject("media_id", UUID.class),
                postId);
    }

    @Override
    public boolean insertLike(UUID userId, UUID postId, Instant at) {
        int rows = jdbc.update("""
                INSERT INTO likes (user_id, target_type, target_id, created_at)
                VALUES (?, 'post', ?, ?)
                ON CONFLICT (user_id, target_type, target_id) DO NOTHING
                """, userId, postId, Timestamp.from(at));
        return rows > 0;
    }

    @Override
    public boolean deleteLike(UUID userId, UUID postId) {
        return jdbc.update(
                        "DELETE FROM likes WHERE user_id = ? AND target_type = 'post' AND target_id = ?",
                        userId,
                        postId)
                > 0;
    }

    @Override
    public boolean liked(UUID userId, UUID postId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE user_id = ? AND target_type = 'post' AND target_id = ?",
                Integer.class,
                userId,
                postId);
        return count != null && count > 0;
    }

    @Override
    public long likeCount(UUID postId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE target_type = 'post' AND target_id = ?", Long.class, postId);
        return count == null ? 0L : count;
    }

    @Override
    public List<LikeRow> listLikes(UUID postId, InstantIdCursor after, int limit) {
        if (after == null) {
            return jdbc.query("""
                    SELECT user_id, created_at FROM likes
                    WHERE target_type = 'post' AND target_id = ?
                    ORDER BY created_at DESC, user_id DESC
                    LIMIT ?
                    """, this::mapLike, postId, limit);
        }
        return jdbc.query("""
                SELECT user_id, created_at FROM likes
                WHERE target_type = 'post' AND target_id = ?
                  AND (created_at, user_id) < (?, ?)
                ORDER BY created_at DESC, user_id DESC
                LIMIT ?
                """, this::mapLike, postId, Timestamp.from(after.at()), after.id(), limit);
    }

    @Override
    public boolean insertRepost(UUID userId, UUID postId, Instant at) {
        int rows = jdbc.update("""
                INSERT INTO reposts (user_id, post_id, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, post_id) DO NOTHING
                """, userId, postId, Timestamp.from(at));
        return rows > 0;
    }

    @Override
    public boolean deleteRepost(UUID userId, UUID postId) {
        return jdbc.update("DELETE FROM reposts WHERE user_id = ? AND post_id = ?", userId, postId) > 0;
    }

    @Override
    public boolean reposted(UUID userId, UUID postId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reposts WHERE user_id = ? AND post_id = ?", Integer.class, userId, postId);
        return count != null && count > 0;
    }

    @Override
    public long repostCount(UUID postId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM reposts WHERE post_id = ?", Long.class, postId);
        return count == null ? 0L : count;
    }

    @Override
    public long commentCount(UUID postId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId);
        return count == null ? 0L : count;
    }

    @Override
    public List<FeedActivity> listFeed(UUID viewerId, InstantIdCursor before, int limit) {
        if (before == null) {
            return jdbc.query(
                    FEED_CTE + """
                    SELECT activity_id, kind, actor_id, post_id, activity_at
                    FROM activities
                    ORDER BY activity_at DESC, activity_id DESC
                    LIMIT ?
                    """,
                    ps -> {
                        bindViewer(ps, viewerId);
                        ps.setInt(10, limit);
                    },
                    this::mapActivity);
        }
        return jdbc.query(
                FEED_CTE + """
                SELECT activity_id, kind, actor_id, post_id, activity_at
                FROM activities
                WHERE (activity_at, activity_id) < (?, ?)
                ORDER BY activity_at DESC, activity_id DESC
                LIMIT ?
                """,
                ps -> {
                    bindViewer(ps, viewerId);
                    ps.setTimestamp(10, Timestamp.from(before.at()));
                    ps.setObject(11, before.id());
                    ps.setInt(12, limit);
                },
                this::mapActivity);
    }

    private Post map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp edited = rs.getTimestamp("edited_at");
        Timestamp deleted = rs.getTimestamp("deleted_at");
        Timestamp hidden = rs.getTimestamp("hidden_at");
        return new Post(
                rs.getObject("id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("body"),
                PostVisibility.fromWire(rs.getString("visibility")),
                created.toInstant(),
                edited == null ? null : edited.toInstant(),
                deleted == null ? null : deleted.toInstant(),
                hidden == null ? null : hidden.toInstant(),
                rs.getString("hidden_reason"));
    }

    private LikeRow mapLike(ResultSet rs, int rowNum) throws SQLException {
        return new LikeRow(
                rs.getObject("user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private FeedActivity mapActivity(ResultSet rs, int rowNum) throws SQLException {
        return new FeedActivity(
                rs.getObject("activity_id", UUID.class),
                FeedKind.fromWire(rs.getString("kind")),
                rs.getObject("actor_id", UUID.class),
                rs.getObject("post_id", UUID.class),
                rs.getTimestamp("activity_at").toInstant());
    }

    private static void bindViewer(PreparedStatement ps, UUID viewerId) throws SQLException {
        for (int i = 1; i <= 9; i++) {
            ps.setObject(i, viewerId);
        }
    }
}
