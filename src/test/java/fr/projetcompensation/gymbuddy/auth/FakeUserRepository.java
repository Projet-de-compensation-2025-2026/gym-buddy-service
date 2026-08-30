package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.users.DuplicateUserException;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FakeUserRepository implements UserRepository {

    private final Map<UUID, User> users = new LinkedHashMap<>();

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.email().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public Optional<User> findByHandle(String handle) {
        return users.values().stream()
                .filter(user -> user.handle().equalsIgnoreCase(handle))
                .findFirst();
    }

    @Override
    public long count() {
        return users.size();
    }

    @Override
    public void save(User user) {
        if (findByEmail(user.email()).isPresent() || findByHandle(user.handle()).isPresent()) {
            throw new DuplicateUserException();
        }
        users.put(user.id(), user);
    }

    void replace(User user) {
        users.put(user.id(), user);
    }

    @Override
    public void update(User user) {
        if (!users.containsKey(user.id())) {
            throw new IllegalStateException("user not found");
        }
        boolean duplicate = users.values().stream()
                .anyMatch(existing -> !existing.id().equals(user.id())
                        && (existing.email().equalsIgnoreCase(user.email())
                                || existing.handle().equalsIgnoreCase(user.handle())));
        if (duplicate) {
            throw new DuplicateUserException();
        }
        users.put(user.id(), user);
    }
}
