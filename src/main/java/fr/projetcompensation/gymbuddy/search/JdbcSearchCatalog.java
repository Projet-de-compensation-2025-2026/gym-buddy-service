package fr.projetcompensation.gymbuddy.search;

import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcSearchCatalog implements SearchCatalog {

    private final JdbcTemplate jdbc;

    public JdbcSearchCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PersonCandidate> people() {
        return jdbc.query(
                """
                SELECT u.id, u.email, u.handle, u.password_hash, u.role, u.status, u.created_at,
                       p.user_id, p.display_name, p.bio, p.visibility, p.sports, p.experience_level,
                       p.city, p.lat, p.lng, p.preferred_windows::text AS preferred_windows, p.avatar_media_id
                FROM users u
                JOIN profiles p ON p.user_id = u.id
                WHERE u.status = 'active'
                """,
                this::mapPerson);
    }

    @Override
    public List<EventCandidate> events() {
        Boolean present = jdbc.queryForObject("SELECT to_regclass('public.events') IS NOT NULL", Boolean.class);
        if (!Boolean.TRUE.equals(present)) {
            return List.of();
        }
        Instant now = Instant.now();
        List<EventCandidate> rows = jdbc.query(
                """
                SELECT e.id, e.organizer_id, e.title, e.description, e.activity, e.place, e.lat, e.lng,
                       COALESCE((
                           SELECT MIN(o.starts_at) FROM event_occurrences o
                           WHERE o.event_id = e.id AND o.cancelled_at IS NULL AND o.starts_at >= ?
                       ), e.starts_at) AS next_start,
                       e.capacity,
                       e.capacity - COALESCE((
                           SELECT COUNT(*) FROM event_applications a
                           JOIN event_occurrences o ON o.id = a.occurrence_id
                           WHERE a.event_id = e.id AND a.status = 'accepted'
                             AND o.id = (
                                 SELECT o2.id FROM event_occurrences o2
                                 WHERE o2.event_id = e.id AND o2.cancelled_at IS NULL AND o2.starts_at >= ?
                                 ORDER BY o2.starts_at ASC, o2.id ASC
                                 LIMIT 1
                             )
                       ), 0) AS remaining_seats,
                       e.visibility, e.hidden_at, e.cancelled_at,
                       u.email, u.handle, u.password_hash, u.role, u.status, u.created_at,
                       p.display_name, p.bio, p.visibility AS profile_visibility, p.sports, p.experience_level,
                       p.city, p.lat AS profile_lat, p.lng AS profile_lng,
                       p.preferred_windows::text AS preferred_windows, p.avatar_media_id
                FROM events e
                JOIN users u ON u.id = e.organizer_id
                JOIN profiles p ON p.user_id = e.organizer_id
                WHERE u.status = 'active'
                """,
                this::mapEvent,
                Timestamp.from(now),
                Timestamp.from(now));
        Map<UUID, Set<UUID>> invitees = loadPairs("SELECT event_id, user_id FROM event_invites");
        Map<UUID, Set<UUID>> accepted = loadPairs(
                "SELECT event_id, user_id FROM event_applications WHERE status = 'accepted'");
        List<EventCandidate> out = new ArrayList<>(rows.size());
        for (EventCandidate row : rows) {
            out.add(new EventCandidate(
                    row.id(),
                    row.organizer(),
                    row.organizerProfile(),
                    row.title(),
                    row.description(),
                    row.activity(),
                    row.place(),
                    row.lat(),
                    row.lng(),
                    row.startsAt(),
                    row.remainingSeats(),
                    row.capacity(),
                    row.visibility(),
                    invitees.getOrDefault(row.id(), Set.of()),
                    accepted.getOrDefault(row.id(), Set.of()),
                    row.hidden(),
                    row.cancelled()));
        }
        return List.copyOf(out);
    }

    private Map<UUID, Set<UUID>> loadPairs(String sql) {
        Map<UUID, Set<UUID>> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            UUID eventId = rs.getObject("event_id", UUID.class);
            UUID userId = rs.getObject("user_id", UUID.class);
            out.computeIfAbsent(eventId, ignored -> new HashSet<>()).add(userId);
        });
        return out;
    }

    private PersonCandidate mapPerson(ResultSet rs, int rowNum) throws SQLException {
        User user = mapUser(rs, rs.getObject("id", UUID.class));
        return new PersonCandidate(user, mapProfile(rs, user.id()), user.createdAt());
    }

    private EventCandidate mapEvent(ResultSet rs, int rowNum) throws SQLException {
        User organizer = mapUser(rs, rs.getObject("organizer_id", UUID.class));
        Profile profile = mapOrganizerProfile(rs, organizer.id());
        Timestamp hidden = rs.getTimestamp("hidden_at");
        Timestamp cancelled = rs.getTimestamp("cancelled_at");
        Timestamp starts = rs.getTimestamp("next_start");
        return new EventCandidate(
                rs.getObject("id", UUID.class),
                organizer,
                profile,
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("activity"),
                rs.getString("place"),
                (Double) rs.getObject("lat"),
                (Double) rs.getObject("lng"),
                starts == null ? null : starts.toInstant(),
                rs.getInt("remaining_seats"),
                rs.getInt("capacity"),
                SearchEventVisibility.fromWire(rs.getString("visibility")),
                Set.of(),
                Set.of(),
                hidden != null,
                cancelled != null);
    }

    private static User mapUser(ResultSet rs, UUID id) throws SQLException {
        return new User(
                id,
                rs.getString("email"),
                rs.getString("handle"),
                rs.getString("password_hash"),
                UserRole.fromWire(rs.getString("role")),
                UserStatus.fromWire(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Profile mapProfile(ResultSet rs, UUID userId) throws SQLException {
        return new Profile(
                userId,
                rs.getString("display_name"),
                rs.getString("bio"),
                ProfileVisibility.fromWire(rs.getString("visibility")),
                sports(rs),
                ExperienceLevel.fromWire(rs.getString("experience_level")),
                rs.getString("city"),
                (Double) rs.getObject("lat"),
                (Double) rs.getObject("lng"),
                readWindows(rs.getString("preferred_windows")),
                rs.getObject("avatar_media_id", UUID.class));
    }

    private static Profile mapOrganizerProfile(ResultSet rs, UUID userId) throws SQLException {
        return new Profile(
                userId,
                rs.getString("display_name"),
                rs.getString("bio"),
                ProfileVisibility.fromWire(rs.getString("profile_visibility")),
                sports(rs),
                ExperienceLevel.fromWire(rs.getString("experience_level")),
                rs.getString("city"),
                (Double) rs.getObject("profile_lat"),
                (Double) rs.getObject("profile_lng"),
                readWindows(rs.getString("preferred_windows")),
                rs.getObject("avatar_media_id", UUID.class));
    }

    private static List<String> sports(ResultSet rs) throws SQLException {
        Array sports = rs.getArray("sports");
        String[] tags = sports == null ? new String[0] : (String[]) sports.getArray();
        return List.of(tags);
    }

    private static List<PreferredWindow> readWindows(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("[]")) {
            return List.of();
        }
        java.util.ArrayList<PreferredWindow> windows = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "\"weekday\"\\s*:\\s*(\\d+)\\s*,\\s*\"start\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"end\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(raw);
        while (matcher.find()) {
            windows.add(new PreferredWindow(Integer.parseInt(matcher.group(1)), matcher.group(2), matcher.group(3)));
        }
        return List.copyOf(windows);
    }
}
