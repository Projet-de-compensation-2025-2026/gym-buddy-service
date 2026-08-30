package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcReportRepository implements ReportRepository {

    private static final String SELECT = """
            SELECT r.id, r.reporter_id, u.handle AS reporter_handle, r.target_type, r.target_id, r.reason, r.status, r.created_at
            FROM reports r
            JOIN users u ON u.id = r.reporter_id
            """;

    private final JdbcTemplate jdbc;

    public JdbcReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Report report) {
        try {
            jdbc.update(
                    """
                    INSERT INTO reports (id, reporter_id, target_type, target_id, reason, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    report.id(),
                    report.reporterId(),
                    report.targetType(),
                    report.targetId(),
                    report.reason(),
                    report.status(),
                    Timestamp.from(report.createdAt()));
        } catch (DuplicateKeyException ex) {
            throw AuthException.conflict("already reported", new FieldIssue("targetId", "duplicate"));
        }
    }

    @Override
    public void update(Report report) {
        jdbc.update("UPDATE reports SET status = ? WHERE id = ?", report.status(), report.id());
    }

    @Override
    public Optional<Report> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE r.id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public Optional<Report> findOpen(UUID reporterId, String targetType, UUID targetId) {
        return jdbc
                .query(
                        SELECT
                                + " WHERE r.reporter_id = ? AND r.target_type = ? AND r.target_id = ? AND r.status = 'open'",
                        this::map,
                        reporterId,
                        targetType,
                        targetId)
                .stream()
                .findFirst();
    }

    @Override
    public List<Report> list(String status, String q, InstantIdCursor after, int limit) {
        String like = q == null ? null : "%" + q + "%";
        if (after == null) {
            return jdbc.query(SELECT + """
                              WHERE r.status = ?
                                AND (? IS NULL OR u.handle ILIKE ? OR r.reason ILIKE ?)
                              ORDER BY r.created_at DESC, r.id DESC
                              LIMIT ?
                              """, this::map, status, like, like, like, limit);
        }
        return jdbc.query(
                SELECT + """
                          WHERE r.status = ?
                            AND (? IS NULL OR u.handle ILIKE ? OR r.reason ILIKE ?)
                            AND (r.created_at, r.id) < (?, ?)
                          ORDER BY r.created_at DESC, r.id DESC
                          LIMIT ?
                          """, this::map, status, like, like, like, Timestamp.from(after.at()), after.id(), limit);
    }

    private Report map(ResultSet rs, int rowNum) throws SQLException {
        return new Report(
                rs.getObject("id", UUID.class),
                rs.getObject("reporter_id", UUID.class),
                rs.getString("reporter_handle"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }
}
