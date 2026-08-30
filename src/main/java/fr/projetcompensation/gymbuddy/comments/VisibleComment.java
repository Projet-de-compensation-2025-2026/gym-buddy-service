package fr.projetcompensation.gymbuddy.comments;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;

public record VisibleComment(
        Comment comment, User author, Profile authorProfile, long likeCount, boolean liked, long replyCount) {}
