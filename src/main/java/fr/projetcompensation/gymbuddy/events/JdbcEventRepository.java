package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.sql.Array;
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
public class JdbcEventRepository implements EventRepository {

    private static final String SELECT_EVENT = """
            SELECT id, organizer_id, title, description, activity, place, lat, lng, starts_at, duration_min,
                   visibility, capacity, recurrence, tags, cover_media_id, cancelled_at, updated_after_accept,
                   created_at, hidden_at
            FROM events
            """;

    private static final String SELECT_OCCURRENCE = """
            SELECT id, event_id, starts_at, cancelled_at
            FROM event_occurrences
            """;

    private static final String SELECT_APPLICATION = """
            SELECT id, event_id, occurrence_id, user_id, status, created_at, responded_at
            FROM event_applications
            """;

    private final JdbcTemplate jdbc;

    public JdbcEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Event event, List<EventOccurrence> occurrences, List<UUID> inviteeIds) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO events (
                      id, organizer_id, title, description, activity, place, lat, lng, starts_at, duration_min,
                      visibility, capacity, recurrence, tags, cover_media_id, cancelled_at, updated_after_accept,
                      created_at, hidden_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            bindEvent(ps, event);
            return ps;
        });
        saveOccurrences(occurrences);
        replaceInvitees(event.id(), inviteeIds);
    }

    @Override
    public void update(Event event) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    UPDATE events
                    SET organizer_id = ?, title = ?, description = ?, activity = ?, place = ?, lat = ?, lng = ?,
                        starts_at = ?, duration_min = ?, visibility = ?, capacity = ?, recurrence = ?, tags = ?,
                        cover_media_id = ?, cancelled_at = ?, updated_after_accept = ?, hidden_at = ?
                    WHERE id = ?
                    """);
            ps.setObject(1, event.organizerId());
            ps.setString(2, event.title());
            ps.setString(3, event.description());
            ps.setString(4, event.activity());
            ps.setString(5, event.place());
            setDouble(ps, 6, event.lat());
            setDouble(ps, 7, event.lng());
            ps.setTimestamp(8, Timestamp.from(event.startsAt()));
            ps.setInt(9, event.durationMin());
            ps.setString(10, event.visibility().wireValue());
            ps.setInt(11, event.capacity());
            ps.setString(12, event.recurrence());
            ps.setArray(13, connection.createArrayOf("text", event.tags().toArray(String[]::new)));
            ps.setObject(14, event.coverMediaId());
            ps.setTimestamp(15, timestamp(event.cancelledAt()));
            ps.setBoolean(16, event.updatedAfterAccept());
            ps.setTimestamp(17, timestamp(event.hiddenAt()));
            ps.setObject(18, event.id());
            return ps;
        });
    }

    @Override
    public void replaceInvitees(UUID eventId, List<UUID> inviteeIds) {
        jdbc.update("DELETE FROM event_invites WHERE event_id = ?", eventId);
        for (UUID userId : inviteeIds) {
            jdbc.update("INSERT INTO event_invites (event_id, user_id) VALUES (?, ?)", eventId, userId);
        }
    }

    @Override
    public void saveOccurrences(List<EventOccurrence> occurrences) {
        for (EventOccurrence occurrence : occurrences) {
            jdbc.update(
                    """
                    INSERT INTO event_occurrences (id, event_id, starts_at, cancelled_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (event_id, starts_at) DO NOTHING
                    """,
                    occurrence.id(),
                    occurrence.eventId(),
                    Timestamp.from(occurrence.startsAt()),
                    timestamp(occurrence.cancelledAt()));
        }
    }

    @Override
    public void updateOccurrence(EventOccurrence occurrence) {
        jdbc.update("""
                UPDATE event_occurrences
                SET starts_at = ?, cancelled_at = ?
                WHERE id = ?
                """, Timestamp.from(occurrence.startsAt()), timestamp(occurrence.cancelledAt()), occurrence.id());
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jdbc.query(SELECT_EVENT + " WHERE id = ?", this::mapEvent, id).stream()
                .findFirst();
    }

    @Override
    public Optional<Event> findByCoverMediaId(UUID mediaId) {
        return jdbc.query(SELECT_EVENT + " WHERE cover_media_id = ?", this::mapEvent, mediaId).stream()
                .findFirst();
    }

    @Override
    public List<EventOccurrence> occurrences(UUID eventId) {
        return jdbc.query(
                SELECT_OCCURRENCE + " WHERE event_id = ? ORDER BY starts_at ASC, id ASC", this::mapOccurrence, eventId);
    }

    @Override
    public Optional<EventOccurrence> findOccurrence(UUID id) {
        return jdbc.query(SELECT_OCCURRENCE + " WHERE id = ?", this::mapOccurrence, id).stream()
                .findFirst();
    }

    @Override
    public Optional<EventOccurrence> lockOccurrence(UUID id) {
        return jdbc.query(SELECT_OCCURRENCE + " WHERE id = ? FOR UPDATE", this::mapOccurrence, id).stream()
                .findFirst();
    }

    @Override
    public List<UUID> inviteeIds(UUID eventId) {
        return jdbc.query(
                "SELECT user_id FROM event_invites WHERE event_id = ?",
                (rs, rowNum) -> rs.getObject("user_id", UUID.class),
                eventId);
    }

    @Override
    public boolean isInvitee(UUID eventId, UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_invites WHERE event_id = ? AND user_id = ?",
                Integer.class,
                eventId,
                userId);
        return count != null && count > 0;
    }

    @Override
    public void saveApplication(EventApplication application) {
        jdbc.update(
                """
                INSERT INTO event_applications (id, event_id, occurrence_id, user_id, status, created_at, responded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                application.id(),
                application.eventId(),
                application.occurrenceId(),
                application.applicantId(),
                application.status().wireValue(),
                Timestamp.from(application.createdAt()),
                timestamp(application.respondedAt()));
    }

    @Override
    public void updateApplication(EventApplication application) {
        jdbc.update("""
                UPDATE event_applications
                SET status = ?, responded_at = ?
                WHERE id = ?
                """, application.status().wireValue(), timestamp(application.respondedAt()), application.id());
    }

    @Override
    public Optional<EventApplication> findApplication(UUID id) {
        return jdbc.query(SELECT_APPLICATION + " WHERE id = ?", this::mapApplication, id).stream()
                .findFirst();
    }

    @Override
    public Optional<EventApplication> findApplication(UUID occurrenceId, UUID applicantId) {
        return jdbc
                .query(
                        SELECT_APPLICATION + " WHERE occurrence_id = ? AND user_id = ?",
                        this::mapApplication,
                        occurrenceId,
                        applicantId)
                .stream()
                .findFirst();
    }

    @Override
    public List<EventApplication> applicationsForEvent(UUID eventId) {
        return jdbc.query(SELECT_APPLICATION + " WHERE event_id = ?", this::mapApplication, eventId);
    }

    @Override
    public List<EventApplication> pendingForOccurrence(UUID occurrenceId) {
        return jdbc.query(
                SELECT_APPLICATION + " WHERE occurrence_id = ? AND status = 'pending' ORDER BY created_at ASC, id ASC",
                this::mapApplication,
                occurrenceId);
    }

    @Override
    public boolean hasAccepted(UUID eventId, UUID userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_applications
                WHERE event_id = ? AND user_id = ? AND status = 'accepted'
                """, Integer.class, eventId, userId);
        return count != null && count > 0;
    }

    @Override
    public int countAccepted(UUID occurrenceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_applications WHERE occurrence_id = ? AND status = 'accepted'",
                Integer.class,
                occurrenceId);
        return count == null ? 0 : count;
    }

    @Override
    public int countAcceptedCoAttendance(UUID organizerId, UUID applicantId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_applications a
                JOIN events e ON e.id = a.event_id
                WHERE e.organizer_id = ? AND a.user_id = ? AND a.status = 'accepted'
                """, Integer.class, organizerId, applicantId);
        return count == null ? 0 : count;
    }

    @Override
    public List<Event> listVisible(
            UUID viewerId, String kind, Instant from, Instant until, InstantIdCursor after, int limit) {
        String kindClause = "";
        if ("instant".equals(kind)) {
            kindClause = " AND e.recurrence IS NULL ";
        } else if ("recurring".equals(kind)) {
            kindClause = " AND e.recurrence IS NOT NULL ";
        }
        String cursorClause = "";
        if (after != null) {
            cursorClause = " AND (e.starts_at > ? OR (e.starts_at = ? AND e.id > ?)) ";
        }
        String sql = """
                SELECT DISTINCT e.id, e.organizer_id, e.title, e.description, e.activity, e.place, e.lat, e.lng,
                       e.starts_at, e.duration_min, e.visibility, e.capacity, e.recurrence, e.tags, e.cover_media_id,
                       e.cancelled_at, e.updated_after_accept, e.created_at, e.hidden_at
                FROM events e
                JOIN event_occurrences o ON o.event_id = e.id
                WHERE e.hidden_at IS NULL
                  AND e.cancelled_at IS NULL
                  AND o.cancelled_at IS NULL
                  AND o.starts_at >= ?
                  AND o.starts_at < ?
                  AND (
                    e.organizer_id = CAST(? AS uuid)
                    OR EXISTS (
                      SELECT 1 FROM event_applications a
                      WHERE a.event_id = e.id AND a.user_id = CAST(? AS uuid) AND a.status = 'accepted'
                    )
                    OR (
                      NOT EXISTS (
                        SELECT 1 FROM friendships f
                        WHERE f.status = 'blocked'
                          AND LEAST(f.requester_id, f.addressee_id) = LEAST(CAST(? AS uuid), e.organizer_id)
                          AND GREATEST(f.requester_id, f.addressee_id) = GREATEST(CAST(? AS uuid), e.organizer_id)
                      )
                      AND (
                        e.visibility = 'public'
                        OR (
                          e.visibility = 'friends'
                          AND EXISTS (
                            SELECT 1 FROM friendships f
                            WHERE f.status = 'accepted'
                              AND LEAST(f.requester_id, f.addressee_id) = LEAST(CAST(? AS uuid), e.organizer_id)
                              AND GREATEST(f.requester_id, f.addressee_id) = GREATEST(CAST(? AS uuid), e.organizer_id)
                          )
                        )
                        OR (
                          e.visibility = 'private'
                          AND EXISTS (
                            SELECT 1 FROM event_invites i
                            WHERE i.event_id = e.id AND i.user_id = CAST(? AS uuid)
                          )
                        )
                      )
                    )
                  )
                """ + kindClause + cursorClause + " ORDER BY e.starts_at ASC, e.id ASC LIMIT ?";
        return jdbc.query(sql, this::mapEvent, listVisibleArgs(viewerId, from, until, after, limit));
    }

    private static Object[] listVisibleArgs(
            UUID viewerId, Instant from, Instant until, InstantIdCursor after, int limit) {
        // Window (2) + organizer/applicant/block pair/friends pair/invitee (7) + optional cursor (3) + limit.
        int extra = after == null ? 0 : 3;
        Object[] args = new Object[2 + 7 + extra + 1];
        int i = 0;
        args[i++] = Timestamp.from(from);
        args[i++] = Timestamp.from(until);
        for (int n = 0; n < 7; n++) {
            args[i++] = viewerId;
        }
        if (after != null) {
            args[i++] = Timestamp.from(after.at());
            args[i++] = Timestamp.from(after.at());
            args[i++] = after.id();
        }
        args[i] = limit;
        return args;
    }

    private void bindEvent(PreparedStatement ps, Event event) throws SQLException {
        ps.setObject(1, event.id());
        ps.setObject(2, event.organizerId());
        ps.setString(3, event.title());
        ps.setString(4, event.description());
        ps.setString(5, event.activity());
        ps.setString(6, event.place());
        setDouble(ps, 7, event.lat());
        setDouble(ps, 8, event.lng());
        ps.setTimestamp(9, Timestamp.from(event.startsAt()));
        ps.setInt(10, event.durationMin());
        ps.setString(11, event.visibility().wireValue());
        ps.setInt(12, event.capacity());
        ps.setString(13, event.recurrence());
        ps.setArray(14, ps.getConnection().createArrayOf("text", event.tags().toArray(String[]::new)));
        ps.setObject(15, event.coverMediaId());
        ps.setTimestamp(16, timestamp(event.cancelledAt()));
        ps.setBoolean(17, event.updatedAfterAccept());
        ps.setTimestamp(18, Timestamp.from(event.createdAt()));
        ps.setTimestamp(19, timestamp(event.hiddenAt()));
    }

    private Event mapEvent(ResultSet rs, int rowNum) throws SQLException {
        Array tags = rs.getArray("tags");
        String[] values = tags == null ? new String[0] : (String[]) tags.getArray();
        Double lat = rs.getObject("lat") == null ? null : rs.getDouble("lat");
        Double lng = rs.getObject("lng") == null ? null : rs.getDouble("lng");
        return new Event(
                rs.getObject("id", UUID.class),
                rs.getObject("organizer_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("activity"),
                rs.getString("place"),
                lat,
                lng,
                rs.getTimestamp("starts_at").toInstant(),
                rs.getInt("duration_min"),
                EventVisibility.fromWire(rs.getString("visibility")),
                rs.getInt("capacity"),
                rs.getString("recurrence"),
                List.of(values),
                rs.getObject("cover_media_id", UUID.class),
                instant(rs, "cancelled_at"),
                rs.getBoolean("updated_after_accept"),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs, "hidden_at"));
    }

    private EventOccurrence mapOccurrence(ResultSet rs, int rowNum) throws SQLException {
        return new EventOccurrence(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getTimestamp("starts_at").toInstant(),
                instant(rs, "cancelled_at"));
    }

    private EventApplication mapApplication(ResultSet rs, int rowNum) throws SQLException {
        return new EventApplication(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("occurrence_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                EventApplicationStatus.fromWire(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs, "responded_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static void setDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setDouble(index, value);
        }
    }
}
