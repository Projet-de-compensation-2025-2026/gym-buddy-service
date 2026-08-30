package fr.projetcompensation.gymbuddy.profiles;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {

    void save(Profile profile);

    void update(Profile profile);

    Optional<Profile> findByUserId(UUID userId);
}
