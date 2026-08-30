package fr.projetcompensation.gymbuddy.posts;

import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;
import java.util.List;
import java.util.UUID;

public record VisiblePost(
        Post post,
        User author,
        Profile authorProfile,
        List<UUID> mediaIds,
        long likeCount,
        long repostCount,
        long commentCount,
        boolean liked,
        boolean reposted) {}
