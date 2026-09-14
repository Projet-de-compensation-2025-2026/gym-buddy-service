package fr.projetcompensation.gymbuddy.users;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByHandle(String handle);

    long count();

    void save(User user);

    void update(User user);

    default boolean hasOtherActiveAdmin(UUID userId) {
        return false;
    }

    /** Serializes token issuance and revocation for one account. */
    default <T> T withAccountLock(UUID userId, java.util.function.Supplier<T> work) {
        synchronized (this) {
            return work.get();
        }
    }

    /** Serializes changes that can remove the last active administrator. */
    default <T> T withAdministrationLock(java.util.function.Supplier<T> work) {
        synchronized (this) {
            return work.get();
        }
    }
}
