package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.auth.PasswordHasher;
import fr.projetcompensation.gymbuddy.auth.TransactionRunner;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inserts missing {@code demo.admin} and {@code demo.mod} only. Not the fixture
 * generator; safe on {@code prod} when {@code GYM_BUDDY_BOOTSTRAP_STAFF=true}.
 */
public final class StaffBootstrap {

    private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordHasher passwords;
    private final TransactionRunner transactions;
    private final Instant origin;

    public StaffBootstrap(
            UserRepository users,
            ProfileRepository profiles,
            PasswordHasher passwords,
            TransactionRunner transactions,
            Instant origin) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
        this.transactions = transactions;
        this.origin = origin;
    }

    public int ensureMissingStaff(String adminPassword, String modPassword) {
        requirePassword("DEMO_ADMIN_PASSWORD", adminPassword);
        requirePassword("DEMO_MOD_PASSWORD", modPassword);
        UserFactory factory = new UserFactory(
                0L, origin, "unused", "unused", "unused", passwords.hash(modPassword), passwords.hash(adminPassword));
        return transactions.inTransaction(() -> {
            int created = 0;
            created += ensure(factory.one(3));
            created += ensure(factory.one(2));
            return created;
        });
    }

    private int ensure(UserDraft draft) {
        User user = draft.user();
        if (users.findByHandle(user.handle()).isPresent()
                || users.findByEmail(user.email()).isPresent()) {
            log.info("staff bootstrap skipped existing handle={}", user.handle());
            return 0;
        }
        users.save(user);
        profiles.save(draft.profile());
        profiles.update(draft.profile());
        log.info(
                "staff bootstrap inserted handle={} role={}",
                user.handle(),
                user.role().wireValue());
        return 1;
    }

    private static void requirePassword(String key, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(key + " is required when GYM_BUDDY_BOOTSTRAP_STAFF=true");
        }
    }
}
