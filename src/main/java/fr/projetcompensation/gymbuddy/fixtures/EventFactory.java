package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.events.Event;
import fr.projetcompensation.gymbuddy.events.EventApplication;
import fr.projetcompensation.gymbuddy.events.EventApplicationStatus;
import fr.projetcompensation.gymbuddy.events.EventOccurrence;
import fr.projetcompensation.gymbuddy.events.EventVisibility;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.datafaker.Faker;

public final class EventFactory {

    public record Bundle(List<Event> events, List<EventOccurrence> occurrences, List<EventApplication> applications) {}

    private final Faker faker;
    private final Random random;
    private final long seed;
    private final Instant origin;

    public EventFactory(long seed, Instant origin) {
        this.seed = seed;
        this.origin = origin;
        this.random = new Random(seed + 47);
        this.faker = new Faker(java.util.Locale.ENGLISH, random);
    }

    public Bundle create(List<UserDraft> users, int eventCount, int applicationCount) {
        if (users.isEmpty() || eventCount <= 0) {
            return new Bundle(List.of(), List.of(), List.of());
        }
        List<Event> events = new ArrayList<>(eventCount);
        List<EventOccurrence> occurrences = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) {
            UserDraft organizer = users.get(i % users.size());
            FixtureCatalog.Cluster cluster = FixtureCatalog.cluster(organizer.clusterIndex());
            Instant starts = origin.plus(Duration.ofDays((i % 40) - 5)).plusSeconds(i * 90L);
            int capacity = 4 + (i % 12);
            EventVisibility visibility = i % 7 == 0 ? EventVisibility.FRIENDS : EventVisibility.PUBLIC;
            String title = cluster.sport() + " at " + cluster.city() + " #" + (i + 1);
            if (title.length() > 120) {
                title = title.substring(0, 120);
            }
            String place = faker.address().streetAddress();
            if (place.length() > 200) {
                place = place.substring(0, 200);
            }
            String activity = cluster.sport();
            if (activity.length() < 2) {
                activity = "run";
            } else if (activity.length() > 32) {
                activity = activity.substring(0, 32);
            }
            UUID eventId = FixtureIds.of(seed, "event", i);
            UUID occurrenceId = FixtureIds.of(seed, "occurrence", i);
            events.add(new Event(
                    eventId,
                    organizer.id(),
                    title,
                    faker.lorem().sentence(12),
                    activity,
                    place,
                    cluster.lat(),
                    cluster.lng(),
                    starts,
                    45 + (i % 6) * 15,
                    visibility,
                    capacity,
                    null,
                    List.of(cluster.sport()),
                    null,
                    null,
                    false,
                    origin.plusSeconds(i),
                    null));
            occurrences.add(new EventOccurrence(occurrenceId, eventId, starts, null));
        }
        List<EventApplication> applications = applications(users, events, occurrences, applicationCount);
        return new Bundle(List.copyOf(events), List.copyOf(occurrences), applications);
    }

    private List<EventApplication> applications(
            List<UserDraft> users, List<Event> events, List<EventOccurrence> occurrences, int count) {
        if (count <= 0 || events.isEmpty()) {
            return List.of();
        }
        List<EventApplication> rows = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        Map<UUID, Integer> accepted = new HashMap<>();
        int i = 0;
        int attempts = 0;
        while (rows.size() < count && attempts < count * 20) {
            attempts++;
            int eventIndex = i % events.size();
            Event event = events.get(eventIndex);
            EventOccurrence occurrence = occurrences.get(eventIndex);
            UserDraft applicant = users.get((i * 5 + 3) % users.size());
            i++;
            if (applicant.id().equals(event.organizerId())) {
                continue;
            }
            String key = occurrence.id() + ":" + applicant.id();
            if (!unique.add(key)) {
                continue;
            }
            int taken = accepted.getOrDefault(occurrence.id(), 0);
            EventApplicationStatus status = taken < event.capacity() && rows.size() % 4 != 0
                    ? EventApplicationStatus.ACCEPTED
                    : EventApplicationStatus.PENDING;
            if (status == EventApplicationStatus.ACCEPTED) {
                accepted.put(occurrence.id(), taken + 1);
            }
            Instant at = origin.plusSeconds(9_000L + rows.size());
            rows.add(new EventApplication(
                    FixtureIds.of(seed, "application", rows.size()),
                    event.id(),
                    occurrence.id(),
                    applicant.id(),
                    status,
                    at,
                    status == EventApplicationStatus.PENDING ? null : at.plusSeconds(60)));
        }
        return List.copyOf(rows);
    }
}
