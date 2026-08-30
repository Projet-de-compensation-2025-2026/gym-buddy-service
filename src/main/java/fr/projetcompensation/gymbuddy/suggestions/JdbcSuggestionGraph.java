package fr.projetcompensation.gymbuddy.suggestions;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.profiles.ExperienceLevel;
import fr.projetcompensation.gymbuddy.profiles.PreferredWindow;
import fr.projetcompensation.gymbuddy.profiles.ProfileVisibility;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcSuggestionGraph implements SuggestionGraph {

    private static final String MEMBER_SELECT = """
            SELECT u.id, u.handle, u.status, u.created_at,
                   p.display_name, p.visibility, p.sports, p.experience_level, p.city, p.lat, p.lng,
                   p.preferred_windows::text AS preferred_windows, p.avatar_media_id
            FROM users u
            JOIN profiles p ON p.user_id = u.id
            """;

    private final JdbcTemplate jdbc;

    public JdbcSuggestionGraph(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MemberSnapshot requireMember(UUID userId) {
        return membersByIds(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> AuthException.unauthenticated("missing or invalid access token"));
    }

    @Override
    public Set<UUID> acceptedFriendIds(UUID userId) {
        return pairIds(userId, "accepted");
    }

    @Override
    public Set<UUID> pendingIds(UUID userId) {
        return pairIds(userId, "pending");
    }

    @Override
    public Set<UUID> blockedIds(UUID userId) {
        return pairIds(userId, "blocked");
    }

    @Override
    public Set<UUID> dismissedIds(UUID viewerId, Instant now) {
        return new LinkedHashSet<>(
                jdbc.query("""
                SELECT candidate_id FROM suggestion_dismissals
                WHERE viewer_id = ? AND until > ?
                """, (rs, row) -> rs.getObject("candidate_id", UUID.class), viewerId, Timestamp.from(now)));
    }

    @Override
    public Instant latestRelationshipChange(UUID userId) {
        Timestamp stamp = jdbc.query("""
                SELECT MAX(t) AS t FROM (
                    SELECT GREATEST(created_at, COALESCE(responded_at, created_at)) AS t
                    FROM friendships
                    WHERE requester_id = ? OR addressee_id = ?
                    UNION ALL
                    SELECT until FROM suggestion_dismissals WHERE viewer_id = ?
                ) changes
                """, rs -> rs.next() ? rs.getTimestamp("t") : null, userId, userId, userId);
        return stamp == null ? Instant.EPOCH : stamp.toInstant();
    }

    @Override
    public Map<UUID, Set<UUID>> neighbors(Collection<UUID> userIds) {
        Map<UUID, Set<UUID>> map = new HashMap<>();
        for (UUID id : userIds) {
            map.put(id, new HashSet<>());
        }
        if (userIds.isEmpty()) {
            return map;
        }
        String placeholders = placeholders(userIds.size());
        List<UUID> ids = List.copyOf(userIds);
        Object[] args = ids.toArray();
        Object[] twice = new Object[args.length * 2];
        System.arraycopy(args, 0, twice, 0, args.length);
        System.arraycopy(args, 0, twice, args.length, args.length);
        jdbc.query(
                "SELECT requester_id, addressee_id FROM friendships WHERE status = 'accepted' AND (requester_id IN ("
                        + placeholders + ") OR addressee_id IN (" + placeholders + "))",
                rs -> {
                    UUID left = rs.getObject("requester_id", UUID.class);
                    UUID right = rs.getObject("addressee_id", UUID.class);
                    map.computeIfAbsent(left, key -> new HashSet<>()).add(right);
                    map.computeIfAbsent(right, key -> new HashSet<>()).add(left);
                },
                twice);
        return map;
    }

    @Override
    public List<MemberSnapshot> membersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> list = List.copyOf(ids);
        return jdbc.query(
                MEMBER_SELECT + " WHERE u.id IN (" + placeholders(list.size()) + ")", this::mapMember, list.toArray());
    }

    @Override
    public Set<UUID> sameCityAndSport(MemberSnapshot viewer, int limit) {
        boolean hasCity = viewer.city() != null && !viewer.city().isBlank();
        boolean hasCoords = viewer.lat() != null && viewer.lng() != null;
        if ((!hasCity && !hasCoords) || viewer.sports().isEmpty()) {
            return Set.of();
        }
        List<MemberSnapshot> rows = jdbc.query(
                MEMBER_SELECT + """
                        WHERE u.status = 'active' AND u.id <> ?
                          AND p.sports && ?::text[]
                          AND (
                            (? AND p.city IS NOT NULL AND LOWER(TRIM(p.city)) = LOWER(TRIM(?)))
                            OR (? AND p.lat IS NOT NULL AND p.lng IS NOT NULL)
                          )
                        LIMIT ?
                        """,
                this::mapMember,
                viewer.userId(),
                pgTextArray(viewer.sports()),
                hasCity,
                hasCity ? viewer.city() : "",
                hasCoords,
                Math.max(limit * 2, limit));
        Set<UUID> ids = new LinkedHashSet<>();
        for (MemberSnapshot row : rows) {
            if (ids.size() >= limit) {
                break;
            }
            if (SportsOverlap.sharesAny(viewer.sports(), row.sports())
                    && GeoScore.nearbyCandidate(
                            viewer.lat(), viewer.lng(), row.lat(), row.lng(), viewer.city(), row.city())) {
                ids.add(row.userId());
            }
        }
        return ids;
    }

    @Override
    public Set<UUID> recentCoParticipants(UUID userId, Instant since) {
        if (!eventsPresent()) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(
                    jdbc.query("""
                    SELECT DISTINCT a2.user_id
                    FROM event_applications a1
                    JOIN event_applications a2
                      ON a1.event_id = a2.event_id AND a2.user_id <> a1.user_id
                    JOIN events e ON e.id = a1.event_id
                    WHERE a1.user_id = ?
                      AND a1.status = 'accepted'
                      AND a2.status = 'accepted'
                      AND e.starts_at >= ?
                    """, (rs, row) -> rs.getObject("user_id", UUID.class), userId, Timestamp.from(since)));
        } catch (DataAccessException ignored) {
            return Set.of();
        }
    }

    @Override
    public List<MemberSnapshot> activeMembers() {
        return jdbc.query(MEMBER_SELECT + " WHERE u.status = 'active'", this::mapMember);
    }

    private Set<UUID> pairIds(UUID userId, String status) {
        return new LinkedHashSet<>(
                jdbc.query("""
                SELECT CASE WHEN requester_id = ? THEN addressee_id ELSE requester_id END AS other_id
                FROM friendships
                WHERE status = ? AND (requester_id = ? OR addressee_id = ?)
                """, (rs, row) -> rs.getObject("other_id", UUID.class), userId, status, userId, userId));
    }

    private boolean eventsPresent() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'event_applications'
                """, Integer.class);
        return count != null && count > 0;
    }

    private MemberSnapshot mapMember(ResultSet rs, int rowNum) throws SQLException {
        Array sports = rs.getArray("sports");
        String[] tags = sports == null ? new String[0] : (String[]) sports.getArray();
        return new MemberSnapshot(
                rs.getObject("id", UUID.class),
                rs.getString("handle"),
                rs.getString("display_name"),
                UserStatus.fromWire(rs.getString("status")),
                ProfileVisibility.fromWire(rs.getString("visibility")),
                List.of(tags),
                rs.getString("city"),
                (Double) rs.getObject("lat"),
                (Double) rs.getObject("lng"),
                readWindows(rs.getString("preferred_windows")),
                ExperienceLevel.fromWire(rs.getString("experience_level")),
                rs.getObject("avatar_media_id", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private static String pgTextArray(List<String> sports) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < sports.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"')
                    .append(sports.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return json.append('}').toString();
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static List<PreferredWindow> readWindows(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("[]")) {
            return List.of();
        }
        ArrayList<PreferredWindow> windows = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "\"weekday\"\\s*:\\s*(\\d+)\\s*,\\s*\"start\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"end\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(raw);
        while (matcher.find()) {
            windows.add(new PreferredWindow(Integer.parseInt(matcher.group(1)), matcher.group(2), matcher.group(3)));
        }
        return List.copyOf(windows);
    }
}
