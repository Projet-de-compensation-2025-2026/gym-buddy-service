package fr.projetcompensation.gymbuddy.profiles;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcProfileRepository implements ProfileRepository {

    private final JdbcTemplate jdbc;

    public JdbcProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Profile profile) {
        jdbc.update(
                "INSERT INTO profiles (user_id, display_name) VALUES (?, ?)", profile.userId(), profile.displayName());
    }
}
