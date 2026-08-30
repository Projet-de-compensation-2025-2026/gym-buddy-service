package fr.projetcompensation.gymbuddy.feed;

import java.util.List;

public record FeedList(List<VisibleFeedItem> data, String next, int size) {}
