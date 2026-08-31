package fr.projetcompensation.gymbuddy.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcProfileRepository implements ProfileRepository {

    private static final String SELECT = """
            SELECT user_id, display_name, bio, visibility, sports, experience_level, city, lat, lng,
                   preferred_windows::text AS preferred_windows, avatar_media_id
            FROM profiles
            WHERE user_id = ?
            """;

    private static final ObjectMapper WINDOWS_JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public JdbcProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Profile profile) {
        jdbc.update(
                "INSERT INTO profiles (user_id, display_name) VALUES (?, ?)", profile.userId(), profile.displayName());
    }

    @Override
    public void update(Profile profile) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    UPDATE profiles
                    SET display_name = ?, bio = ?, visibility = ?, sports = ?, experience_level = ?,
                        city = ?, lat = ?, lng = ?, preferred_windows = ?::jsonb, avatar_media_id = ?
                    WHERE user_id = ?
                    """);
            ps.setString(1, profile.displayName());
            ps.setString(2, profile.bio());
            ps.setString(3, profile.visibility().wireValue());
            ps.setArray(4, connection.createArrayOf("text", profile.sports().toArray(String[]::new)));
            ps.setString(
                    5,
                    profile.experienceLevel() == null
                            ? null
                            : profile.experienceLevel().wireValue());
            ps.setString(6, profile.city());
            if (profile.lat() == null) {
                ps.setObject(7, null);
            } else {
                ps.setDouble(7, profile.lat());
            }
            if (profile.lng() == null) {
                ps.setObject(8, null);
            } else {
                ps.setDouble(8, profile.lng());
            }
            ps.setString(9, writeWindows(profile.preferredWindows()));
            ps.setObject(10, profile.avatarMediaId());
            ps.setObject(11, profile.userId());
            return ps;
        });
    }

    @Override
    public Optional<Profile> findByUserId(UUID userId) {
        return jdbc.query(SELECT, this::mapProfile, userId).stream().findFirst();
    }

    private Profile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        Array sports = rs.getArray("sports");
        String[] tags = sports == null ? new String[0] : (String[]) sports.getArray();
        return new Profile(
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("bio"),
                ProfileVisibility.fromWire(rs.getString("visibility")),
                List.of(tags),
                ExperienceLevel.fromWire(rs.getString("experience_level")),
                rs.getString("city"),
                (Double) rs.getObject("lat"),
                (Double) rs.getObject("lng"),
                readWindows(rs.getString("preferred_windows")),
                rs.getObject("avatar_media_id", UUID.class));
    }

    private static String writeWindows(List<PreferredWindow> windows) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            PreferredWindow window = windows.get(i);
            json.append("{\"weekday\":")
                    .append(window.weekday())
                    .append(",\"start\":\"")
                    .append(window.start())
                    .append("\",\"end\":\"")
                    .append(window.end())
                    .append("\"}");
        }
        return json.append(']').toString();
    }

    private static List<PreferredWindow> readWindows(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("[]")) {
            return List.of();
        }
        try {
            JsonNode arr = WINDOWS_JSON.readTree(raw);
            if (!arr.isArray()) {
                return List.of();
            }
            List<PreferredWindow> windows = new ArrayList<>();
            for (JsonNode node : arr) {
                windows.add(new PreferredWindow(
                        node.path("weekday").asInt(),
                        node.path("start").asText(),
                        node.path("end").asText()));
            }
            return List.copyOf(windows);
        } catch (Exception ex) {
            throw new IllegalStateException("preferred_windows is not valid JSON", ex);
        }
    }
}
