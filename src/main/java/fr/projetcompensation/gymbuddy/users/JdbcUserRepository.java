package fr.projetcompensation.gymbuddy.users;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "DATABASE_URL")
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc
                .query(
                        "SELECT id, email, handle, password_hash, role, status, created_at FROM users WHERE id = ?",
                        this::mapUser,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jdbc
                .query(
                        "SELECT id, email, handle, password_hash, role, status, created_at FROM users WHERE email = ?",
                        this::mapUser,
                        email)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<User> findByHandle(String handle) {
        return jdbc
                .query(
                        "SELECT id, email, handle, password_hash, role, status, created_at FROM users WHERE handle = ?",
                        this::mapUser,
                        handle)
                .stream()
                .findFirst();
    }

    @Override
    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void save(User user) {
        try {
            jdbc.update(
                    """
                    INSERT INTO users (id, email, handle, password_hash, role, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    user.id(),
                    user.email(),
                    user.handle(),
                    user.passwordHash(),
                    user.role().wireValue(),
                    user.status().wireValue(),
                    Timestamp.from(user.createdAt()));
        } catch (DuplicateKeyException ex) {
            throw new DuplicateUserException();
        }
    }

    @Override
    public void update(User user) {
        try {
            int updated = jdbc.update(
                    """
                    UPDATE users
                    SET email = ?, handle = ?, password_hash = ?, role = ?, status = ?
                    WHERE id = ?
                    """,
                    user.email(),
                    user.handle(),
                    user.passwordHash(),
                    user.role().wireValue(),
                    user.status().wireValue(),
                    user.id());
            if (updated == 0) {
                throw new IllegalStateException("user not found");
            }
        } catch (DuplicateKeyException ex) {
            throw new DuplicateUserException();
        }
    }

    private User mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("handle"),
                rs.getString("password_hash"),
                UserRole.fromWire(rs.getString("role")),
                UserStatus.fromWire(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
