package fr.projetcompensation.gymbuddy.friends;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;

public record ListedFriendship(Friendship friendship, User peer, Profile peerProfile, boolean incoming) {}
