package fr.projetcompensation.gymbuddy.friends;

import java.util.List;

public record FriendshipList(List<ListedFriendship> data, String next, int size) {}
