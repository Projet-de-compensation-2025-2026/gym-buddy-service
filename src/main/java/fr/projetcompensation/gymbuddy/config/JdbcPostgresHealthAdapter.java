package fr.projetcompensation.gymbuddy.config;

import fr.projetcompensation.gymbuddy.health.PostgresHealthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcPostgresHealthAdapter implements PostgresHealthPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcPostgresHealthAdapter.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcPostgresHealthAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean reachable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (RuntimeException ex) {
            log.warn("PostgreSQL readiness check failed: {}", ex.getMessage());
            return false;
        }
    }
}
