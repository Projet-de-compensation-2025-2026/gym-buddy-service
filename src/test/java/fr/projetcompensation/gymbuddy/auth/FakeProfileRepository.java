package fr.projetcompensation.gymbuddy.auth;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import java.util.ArrayList;
import java.util.List;

final class FakeProfileRepository implements ProfileRepository {

    final List<Profile> saved = new ArrayList<>();

    @Override
    public void save(Profile profile) {
        saved.add(profile);
    }
}
