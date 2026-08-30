package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.ErrorCode;
import org.junit.jupiter.api.Test;

class FixtureGuardTest {

    @Test
    void fsAdm05_prodProfileIsForbidden() {
        assertThatThrownBy(() -> FixtureGuard.requireNonProduction(true))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void fsAdm05_nonProdIsAllowed() {
        assertThatCode(() -> FixtureGuard.requireNonProduction(false)).doesNotThrowAnyException();
    }
}
