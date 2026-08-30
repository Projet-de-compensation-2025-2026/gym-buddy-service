package fr.projetcompensation.gymbuddy.feed;

import fr.projetcompensation.gymbuddy.posts.VisiblePost;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.users.User;

public record VisibleFeedItem(FeedActivity activity, User actor, Profile actorProfile, VisiblePost post) {}
