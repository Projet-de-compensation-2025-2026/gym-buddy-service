package fr.projetcompensation.gymbuddy.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.projetcompensation.gymbuddy.auth.FakePasswordHasher;
import fr.projetcompensation.gymbuddy.auth.FakeProfileRepository;
import fr.projetcompensation.gymbuddy.auth.FakeUserRepository;
import fr.projetcompensation.gymbuddy.auth.TransactionRunner;
import fr.projetcompensation.gymbuddy.users.UserRole;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaffBootstrapTest {

    private FakeUserRepository users;
    private FakeProfileRepository profiles;
    private StaffBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        users = new FakeUserRepository();
        profiles = new FakeProfileRepository();
        bootstrap = new StaffBootstrap(
                users,
                profiles,
                new FakePasswordHasher(),
                TransactionRunner.immediate(),
                Instant.parse("2026-01-01T08:00:00Z"));
    }

    @Test
    void insertsMissingDemoAdminAndMod() {
        int created = bootstrap.ensureMissingStaff("admin-secret-password", "mod-secret-password");

        assertThat(created).isEqualTo(2);
        assertThat(users.findByHandle(FixtureCatalog.ADMIN_HANDLE))
                .hasValueSatisfying(user -> assertThat(user.role()).isEqualTo(UserRole.ADMIN));
        assertThat(users.findByHandle(FixtureCatalog.MOD_HANDLE))
                .hasValueSatisfying(user -> assertThat(user.role()).isEqualTo(UserRole.MODERATOR));
        assertThat(bootstrap.ensureMissingStaff("admin-secret-password", "mod-secret-password"))
                .isZero();
    }

    @Test
    void blankPasswordFailsClosed() {
        assertThatThrownBy(() -> bootstrap.ensureMissingStaff(" ", "mod-secret-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_ADMIN_PASSWORD");
        assertThat(users.findByHandle(FixtureCatalog.ADMIN_HANDLE)).isEmpty();
    }
}
