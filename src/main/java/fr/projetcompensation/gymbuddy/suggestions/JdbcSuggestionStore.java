package fr.projetcompensation.gymbuddy.suggestions;

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
public class JdbcSuggestionStore implements SuggestionStore {

    private final JdbcTemplate jdbc;

    public JdbcSuggestionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replaceScores(UUID viewerId, Instant computedAt, List<ScoredCandidate> ranked) {
        jdbc.update("DELETE FROM suggestion_scores WHERE user_id = ?", viewerId);
        for (ScoredCandidate scored : ranked) {
            jdbc.update(
                    """
                    INSERT INTO suggestion_scores
                        (user_id, candidate_id, score, primary_reason, mutual_friends, computed_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    viewerId,
                    scored.userId(),
                    scored.score(),
                    scored.reason(),
                    scored.mutualFriends(),
                    Timestamp.from(computedAt));
        }
    }

    @Override
    public Optional<Instant> scoresComputedAt(UUID viewerId) {
        List<Timestamp> rows = jdbc.query(
                "SELECT MAX(computed_at) AS computed_at FROM suggestion_scores WHERE user_id = ?",
                (rs, row) -> rs.getTimestamp("computed_at"),
                viewerId);
        if (rows.isEmpty() || rows.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst().toInstant());
    }

    @Override
    public List<ScoredCandidate> loadScores(UUID viewerId) {
        return jdbc.query(
                """
                SELECT candidate_id, score, primary_reason, mutual_friends
                FROM suggestion_scores
                WHERE user_id = ?
                ORDER BY score DESC, candidate_id
                """,
                (rs, row) -> new ScoredCandidate(
                        rs.getObject("candidate_id", UUID.class),
                        rs.getDouble("score"),
                        rs.getString("primary_reason"),
                        rs.getInt("mutual_friends"),
                        List.of(),
                        new FeatureVector(0, 0, 0, 0, 0)),
                viewerId);
    }

    @Override
    public void dismiss(UUID viewerId, UUID candidateId, Instant until) {
        jdbc.update("""
                INSERT INTO suggestion_dismissals (viewer_id, candidate_id, until)
                VALUES (?, ?, ?)
                ON CONFLICT (viewer_id, candidate_id) DO UPDATE SET until = EXCLUDED.until
                """, viewerId, candidateId, Timestamp.from(until));
    }

    @Override
    public void deleteScore(UUID viewerId, UUID candidateId) {
        jdbc.update("DELETE FROM suggestion_scores WHERE user_id = ? AND candidate_id = ?", viewerId, candidateId);
    }
}
