package fr.projetcompensation.gymbuddy.search;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.time.Instant;

public record PersonCandidate(User user, Profile profile, Instant recencyAt) {}
