package fr.projetcompensation.gymbuddy.matching;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcMatchingStore implements MatchingStore {

    private final JdbcTemplate jdbc;

    public JdbcMatchingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void optIn(UUID userId, LocalDate weekStart, Instant at) {
        jdbc.update("""
                INSERT INTO matching_opt_ins (user_id, week_start, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, week_start) DO NOTHING
                """, userId, Date.valueOf(weekStart), Timestamp.from(at));
    }

    @Override
    public void optOut(UUID userId, LocalDate weekStart) {
        jdbc.update(
                "DELETE FROM matching_opt_ins WHERE user_id = ? AND week_start = ?", userId, Date.valueOf(weekStart));
    }

    @Override
    public boolean optedIn(UUID userId, LocalDate weekStart) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_opt_ins WHERE user_id = ? AND week_start = ?",
                Integer.class,
                userId,
                Date.valueOf(weekStart));
        return count != null && count > 0;
    }

    @Override
    public List<MatchingOptIn> listOptIns(LocalDate weekStart) {
        return jdbc.query(
                """
                SELECT user_id, created_at FROM matching_opt_ins
                WHERE week_start = ?
                ORDER BY created_at, user_id
                """,
                (rs, row) -> new MatchingOptIn(
                        rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant()),
                Date.valueOf(weekStart));
    }

    @Override
    public void replacePairs(LocalDate weekStart, List<ProposedMatch> matches) {
        jdbc.update("DELETE FROM matching_pairs WHERE week_start = ?", Date.valueOf(weekStart));
        for (ProposedMatch match : matches) {
            jdbc.update(
                    """
                    INSERT INTO matching_pairs
                        (week_start, user_a, user_b, event_id, activity, starts_at, duration_min, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                    """,
                    Date.valueOf(weekStart),
                    match.left(),
                    match.right(),
                    match.eventId(),
                    match.activity(),
                    Timestamp.from(match.startsAt()),
                    match.durationMin());
        }
    }

    @Override
    public Optional<ProposedMatch> pairFor(UUID userId, LocalDate weekStart) {
        List<ProposedMatch> rows = jdbc.query(
                """
                SELECT user_a, user_b, event_id, activity, starts_at, duration_min
                FROM matching_pairs
                WHERE week_start = ? AND (user_a = ? OR user_b = ?)
                """,
                (rs, row) -> new ProposedMatch(
                        rs.getObject("user_a", UUID.class),
                        rs.getObject("user_b", UUID.class),
                        0.0,
                        rs.getString("activity"),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getInt("duration_min"),
                        weekStart,
                        rs.getObject("event_id", UUID.class)),
                Date.valueOf(weekStart),
                userId,
                userId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean hasPairs(LocalDate weekStart) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_pairs WHERE week_start = ?", Integer.class, Date.valueOf(weekStart));
        return count != null && count > 0;
    }
}
