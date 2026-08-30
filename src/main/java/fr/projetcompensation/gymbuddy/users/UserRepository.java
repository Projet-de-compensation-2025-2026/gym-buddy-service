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
}
