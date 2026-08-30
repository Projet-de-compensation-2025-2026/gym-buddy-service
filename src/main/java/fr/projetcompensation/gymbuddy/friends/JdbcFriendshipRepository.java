package fr.projetcompensation.gymbuddy.friends;

import fr.projetcompensation.gymbuddy.profiles.FriendshipQueries;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcFriendshipRepository implements FriendshipRepository, FriendshipQueries {

    private static final String SELECT =
            "SELECT id, requester_id, addressee_id, status, created_at, responded_at FROM friendships ";

    private final JdbcTemplate jdbc;

    public JdbcFriendshipRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Friendship friendship) {
        jdbc.update(
                """
                INSERT INTO friendships (id, requester_id, addressee_id, status, created_at, responded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                friendship.id(),
                friendship.requesterId(),
                friendship.addresseeId(),
                friendship.status().wireValue(),
                Timestamp.from(friendship.createdAt()),
                friendship.respondedAt() == null ? null : Timestamp.from(friendship.respondedAt()));
    }

    @Override
    public void update(Friendship friendship) {
        jdbc.update(
                """
                UPDATE friendships
                SET requester_id = ?, addressee_id = ?, status = ?, responded_at = ?
                WHERE id = ?
                """,
                friendship.requesterId(),
                friendship.addresseeId(),
                friendship.status().wireValue(),
                friendship.respondedAt() == null ? null : Timestamp.from(friendship.respondedAt()),
                friendship.id());
    }

    @Override
    public void delete(UUID id) {
        jdbc.update("DELETE FROM friendships WHERE id = ?", id);
    }

    @Override
    public Optional<Friendship> findById(UUID id) {
        return jdbc.query(SELECT + "WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public Optional<Friendship> findPair(UUID left, UUID right) {
        return jdbc.query(SELECT + """
                                WHERE LEAST(requester_id, addressee_id) = LEAST(?, ?)
                                  AND GREATEST(requester_id, addressee_id) = GREATEST(?, ?)
                                """, this::map, left, right, left, right).stream()
                .findFirst();
    }

    @Override
    public List<Friendship> listAccepted(UUID userId, InstantIdCursor after, int limit) {
        return list("""
                WHERE status = 'accepted' AND (requester_id = ? OR addressee_id = ?)
                """, userId, after, limit);
    }

    @Override
    public List<Friendship> listIncoming(UUID userId, InstantIdCursor after, int limit) {
        return list("""
                WHERE status = 'pending' AND addressee_id = ?
                """, userId, after, limit, false);
    }

    @Override
    public List<Friendship> listOutgoing(UUID userId, InstantIdCursor after, int limit) {
        return list("""
                WHERE status = 'pending' AND requester_id = ?
                """, userId, after, limit, false);
    }

    @Override
    public boolean areAcceptedFriends(UUID left, UUID right) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM friendships
                WHERE status = 'accepted'
                  AND LEAST(requester_id, addressee_id) = LEAST(?, ?)
                  AND GREATEST(requester_id, addressee_id) = GREATEST(?, ?)
                """, Integer.class, left, right, left, right);
        return count != null && count > 0;
    }

    @Override
    public int acceptedCount(UUID userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM friendships
                WHERE status = 'accepted' AND (requester_id = ? OR addressee_id = ?)
                """, Integer.class, userId, userId);
        return count == null ? 0 : count;
    }

    @Override
    public boolean isBlockedEitherWay(UUID left, UUID right) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM friendships
                WHERE status = 'blocked'
                  AND LEAST(requester_id, addressee_id) = LEAST(?, ?)
                  AND GREATEST(requester_id, addressee_id) = GREATEST(?, ?)
                """, Integer.class, left, right, left, right);
        return count != null && count > 0;
    }

    private List<Friendship> list(String where, UUID userId, InstantIdCursor after, int limit) {
        return list(where, userId, after, limit, true);
    }

    private List<Friendship> list(String where, UUID userId, InstantIdCursor after, int limit, boolean twoUserParams) {
        String sql = SELECT + where;
        if (after != null) {
            sql += " AND (created_at, id) < (?, ?)";
        }
        sql += " ORDER BY created_at DESC, id DESC LIMIT ?";
        if (twoUserParams) {
            if (after == null) {
                return jdbc.query(sql, this::map, userId, userId, limit);
            }
            return jdbc.query(sql, this::map, userId, userId, Timestamp.from(after.at()), after.id(), limit);
        }
        if (after == null) {
            return jdbc.query(sql, this::map, userId, limit);
        }
        return jdbc.query(sql, this::map, userId, Timestamp.from(after.at()), after.id(), limit);
    }

    private Friendship map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp responded = rs.getTimestamp("responded_at");
        Instant respondedAt = responded == null ? null : responded.toInstant();
        return new Friendship(
                rs.getObject("id", UUID.class),
                rs.getObject("requester_id", UUID.class),
                rs.getObject("addressee_id", UUID.class),
                FriendshipStatus.fromWire(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                respondedAt);
    }
}
