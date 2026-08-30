package fr.projetcompensation.gymbuddy.posts;

import java.util.List;

public record PostLikerList(List<PostLiker> data, String next, int size) {}
