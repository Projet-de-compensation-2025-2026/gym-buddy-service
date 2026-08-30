package fr.projetcompensation.gymbuddy.comments;

import java.util.List;

public record CommentList(List<VisibleComment> data, String next, int size) {}
