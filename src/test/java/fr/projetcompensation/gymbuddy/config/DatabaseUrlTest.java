package fr.projetcompensation.gymbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void parsesPostgresqlUri() {
        DatabaseUrl parsed = DatabaseUrl.parse("postgresql://gymbuddy:change-me@postgres:5432/gymbuddy");

        assertThat(parsed.jdbcUrl()).isEqualTo("jdbc:postgresql://postgres:5432/gymbuddy");
        assertThat(parsed.username()).isEqualTo("gymbuddy");
        assertThat(parsed.password()).isEqualTo("change-me");
    }

    @Test
    void decodesPassword() {
        DatabaseUrl parsed = DatabaseUrl.parse("postgresql://gymbuddy:p%40ss@127.0.0.1:5432/gymbuddy");

        assertThat(parsed.password()).isEqualTo("p@ss");
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> DatabaseUrl.parse(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
