package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FakeProfileRepository implements ProfileRepository {

    final List<Profile> saved = new ArrayList<>();
    private final Map<UUID, Profile> byUser = new LinkedHashMap<>();

    @Override
    public void save(Profile profile) {
        saved.add(profile);
        byUser.put(profile.userId(), profile);
    }

    @Override
    public void update(Profile profile) {
        byUser.put(profile.userId(), profile);
    }

    @Override
    public Optional<Profile> findByUserId(UUID userId) {
        return Optional.ofNullable(byUser.get(userId));
    }
}
