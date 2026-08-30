package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;

public record VisibleApplicant(EventApplication application, User applicant, Profile profile, double matchingScore) {}
