package fr.projetcompensation.gymbuddy.events;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.auth.TransactionRunner;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaKind;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.media.MediaStatus;
import fr.projetcompensation.gymbuddy.profiles.Profile;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EventService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = 2000;
    static final int MIN_ACTIVITY = 2;
    static final int MAX_ACTIVITY = 32;
    static final int MAX_PLACE = 200;
    static final int MAX_DURATION = 1440;
    static final int MAX_CAPACITY = 100;
    static final int MAX_TAGS = 8;
    static final int MAX_TAG = 32;
    private static final String NOT_FOUND = "event not found";

    private final EventRepository events;
    private final MediaRepository media;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final TransactionRunner transactions;
    private final Clock clock;

    public EventService(
            EventRepository events,
            MediaRepository media,
            FriendshipRepository friendships,
            UserRepository users,
            ProfileRepository profiles,
            TransactionRunner transactions,
            Clock clock) {
        this.events = events;
        this.media = media;
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
        this.transactions = transactions;
        this.clock = clock;
    }

    public VisibleEvent create(UUID callerId, EventDraft draft) {
        User caller = requireActive(callerId);
        Instant now = clock.instant();
        String title = requireText(draft.title(), "title", 1, MAX_TITLE);
        String activity = requireText(draft.activity(), "activity", MIN_ACTIVITY, MAX_ACTIVITY);
        String place = requireText(draft.place(), "place", 1, MAX_PLACE);
        String description = optionalText(draft.description(), "description", MAX_DESCRIPTION);
        if (draft.startsAt() == null) {
            throw AuthException.validation("start is required", new FieldIssue("startsAt", "required"));
        }
        if (!draft.startsAt().isAfter(now)) {
            throw AuthException.validation("start must be in the future", new FieldIssue("startsAt", "past"));
        }
        int duration = requireRange(draft.durationMin(), "durationMin", 1, MAX_DURATION);
        int capacity = requireRange(draft.capacity(), "capacity", 1, MAX_CAPACITY);
        EventVisibility visibility;
        try {
            visibility = EventVisibility.fromWire(draft.visibility());
        } catch (IllegalArgumentException ex) {
            throw AuthException.validation("visibility is not allowed", new FieldIssue("visibility", "enum"));
        }
        String recurrence = normalizeRecurrence(draft.recurrence());
        List<Instant> starts = occurrenceStarts(draft.startsAt(), recurrence, now);
        List<String> tags = normalizeTags(draft.tags());
        UUID cover = requireCover(caller.id(), draft.coverMediaId());
        List<UUID> invitees = normalizeInvitees(caller.id(), visibility, draft.inviteeIds());
        Double lat = optionalCoord(draft.lat(), "lat", -90, 90);
        Double lng = optionalCoord(draft.lng(), "lng", -180, 180);
        Event row = new Event(
                UUID.randomUUID(),
                caller.id(),
                title,
                description,
                activity,
                place,
                lat,
                lng,
                draft.startsAt(),
                duration,
                visibility,
                capacity,
                recurrence,
                tags,
                cover,
                null,
                false,
                now,
                null);
        List<EventOccurrence> occurrences = new ArrayList<>();
        for (Instant start : starts) {
            occurrences.add(new EventOccurrence(UUID.randomUUID(), row.id(), start, null));
        }
        events.save(row, occurrences, invitees);
        return visible(row, caller, true);
    }

    public EventList list(UUID callerId, String kind, Instant from, Instant until, String after, Integer size) {
        User caller = requireActive(callerId);
        Instant now = clock.instant();
        Instant windowStart = from == null ? now : from;
        Instant windowEnd = until == null ? WeeklyRrule.defaultWindowEnd(windowStart) : until;
        if (!windowEnd.isAfter(windowStart)) {
            throw AuthException.validation("window is not valid", new FieldIssue("until", "range"));
        }
        String kindFilter = normalizeKind(kind);
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw AuthException.validation("size is not valid", new FieldIssue("size", "range"));
        }
        InstantIdCursor cursor = InstantIdCursor.parse(after).orElse(null);
        List<Event> rows = events.listVisible(caller.id(), kindFilter, windowStart, windowEnd, cursor, pageSize + 1);
        String next = null;
        if (rows.size() > pageSize) {
            Event last = rows.get(pageSize - 1);
            next = new InstantIdCursor(last.startsAt(), last.id()).encode();
            rows = rows.subList(0, pageSize);
        }
        List<VisibleEvent> data = new ArrayList<>();
        for (Event row : rows) {
            data.add(visible(row, caller, false));
        }
        return new EventList(data, next, pageSize);
    }

    public VisibleEvent get(UUID callerId, UUID eventId) {
        User caller = requireActive(callerId);
        Event row = requireVisible(caller, eventId);
        extendOccurrences(row);
        return visible(row, caller, true);
    }

    public VisibleEvent patch(UUID callerId, UUID eventId, EventDraft draft) {
        User caller = requireActive(callerId);
        Event row = events.findById(eventId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.hidden() || row.cancelled() || !row.organizerId().equals(caller.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Instant now = clock.instant();
        if (!row.startsAt().isAfter(now)) {
            throw AuthException.validation("event already started", new FieldIssue("startsAt", "past"));
        }
        String title = draft.title() == null ? row.title() : requireText(draft.title(), "title", 1, MAX_TITLE);
        String activity = draft.activity() == null
                ? row.activity()
                : requireText(draft.activity(), "activity", MIN_ACTIVITY, MAX_ACTIVITY);
        String place = draft.place() == null ? row.place() : requireText(draft.place(), "place", 1, MAX_PLACE);
        String description = draft.description() == null
                ? row.description()
                : optionalText(draft.description(), "description", MAX_DESCRIPTION);
        Instant startsAt = draft.startsAt() == null ? row.startsAt() : draft.startsAt();
        if (!startsAt.isAfter(now)) {
            throw AuthException.validation("start must be in the future", new FieldIssue("startsAt", "past"));
        }
        int duration = draft.durationMin() == null
                ? row.durationMin()
                : requireRange(draft.durationMin(), "durationMin", 1, MAX_DURATION);
        List<String> tags = draft.tags() == null ? row.tags() : normalizeTags(draft.tags());
        UUID cover =
                draft.coverMediaId() == null ? row.coverMediaId() : requireCover(caller.id(), draft.coverMediaId());
        Double lat = draft.lat() == null ? row.lat() : optionalCoord(draft.lat(), "lat", -90, 90);
        Double lng = draft.lng() == null ? row.lng() : optionalCoord(draft.lng(), "lng", -180, 180);
        boolean acceptedAnyone = events.applicationsForEvent(row.id()).stream()
                .anyMatch(application -> application.status() == EventApplicationStatus.ACCEPTED);
        boolean updatedAfterAccept = row.updatedAfterAccept() || acceptedAnyone;
        Event updated = row.withDetails(
                title, description, activity, place, lat, lng, startsAt, duration, tags, cover, updatedAfterAccept);
        events.update(updated);
        if (draft.inviteeIds() != null) {
            events.replaceInvitees(row.id(), normalizeInvitees(caller.id(), row.visibility(), draft.inviteeIds()));
        }
        if (!startsAt.equals(row.startsAt())) {
            rescheduleFirst(updated);
        }
        return visible(updated, caller, true);
    }

    public VisibleEvent cancel(UUID callerId, UUID eventId, UUID occurrenceId) {
        User caller = requireActive(callerId);
        Event row = events.findById(eventId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.hidden() || !row.organizerId().equals(caller.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        Instant now = clock.instant();
        if (occurrenceId == null) {
            Event cancelled = row.cancelled(now);
            events.update(cancelled);
            for (EventOccurrence occurrence : events.occurrences(row.id())) {
                if (!occurrence.cancelled() && occurrence.startsAt().isAfter(now)) {
                    cancelOccurrence(occurrence, now);
                }
            }
            return visible(cancelled, caller, true);
        }
        EventOccurrence occurrence =
                events.findOccurrence(occurrenceId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!occurrence.eventId().equals(row.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        if (!occurrence.cancelled()) {
            cancelOccurrence(occurrence, now);
        }
        return visible(row, caller, true);
    }

    public VisibleApplication apply(UUID callerId, UUID eventId, UUID occurrenceId) {
        User caller = requireActive(callerId);
        Event row = requireVisible(caller, eventId);
        if (row.organizerId().equals(caller.id())) {
            throw AuthException.forbidden("organizer cannot apply");
        }
        if (row.cancelled()) {
            throw AuthException.validation("event is cancelled", new FieldIssue("id", "cancelled"));
        }
        EventOccurrence occurrence = resolveOccurrence(row, occurrenceId);
        Instant now = clock.instant();
        if (!occurrence.startsAt().isAfter(now) || occurrence.cancelled()) {
            throw AuthException.validation("occurrence is not open", new FieldIssue("occurrenceId", "past"));
        }
        if (events.findApplication(occurrence.id(), caller.id()).isPresent()) {
            throw AuthException.conflict("already applied", new FieldIssue("id", "duplicate"));
        }
        EventApplication application = new EventApplication(
                UUID.randomUUID(), row.id(), occurrence.id(), caller.id(), EventApplicationStatus.PENDING, now, null);
        events.saveApplication(application);
        return toVisibleApplication(application);
    }

    public void withdraw(UUID callerId, UUID applicationId) {
        User caller = requireActive(callerId);
        EventApplication application =
                events.findApplication(applicationId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!application.applicantId().equals(caller.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        if (application.status() != EventApplicationStatus.PENDING
                && application.status() != EventApplicationStatus.ACCEPTED) {
            throw AuthException.notFound(NOT_FOUND);
        }
        EventOccurrence occurrence =
                events.findOccurrence(application.occurrenceId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Instant now = clock.instant();
        if (!occurrence.startsAt().isAfter(now)) {
            throw AuthException.validation("occurrence already started", new FieldIssue("id", "past"));
        }
        events.updateApplication(application.withStatus(EventApplicationStatus.WITHDRAWN, now));
    }

    public VisibleApplication accept(UUID callerId, UUID applicationId) {
        User caller = requireActive(callerId);
        return transactions.inTransaction(() -> {
            EventApplication application =
                    events.findApplication(applicationId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
            Event row = events.findById(application.eventId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
            if (row.hidden() || !row.organizerId().equals(caller.id())) {
                throw AuthException.notFound(NOT_FOUND);
            }
            if (application.status() != EventApplicationStatus.PENDING) {
                throw AuthException.notFound(NOT_FOUND);
            }
            EventOccurrence occurrence = events.lockOccurrence(application.occurrenceId())
                    .orElseThrow(() -> AuthException.notFound(NOT_FOUND));
            if (occurrence.cancelled()) {
                throw AuthException.validation("occurrence is cancelled", new FieldIssue("id", "cancelled"));
            }
            int accepted = events.countAccepted(occurrence.id());
            if (accepted >= row.capacity()) {
                throw AuthException.conflict("event is full", new FieldIssue("id", "full"));
            }
            Instant now = clock.instant();
            EventApplication updated = application.withStatus(EventApplicationStatus.ACCEPTED, now);
            events.updateApplication(updated);
            return toVisibleApplication(updated);
        });
    }

    public void decline(UUID callerId, UUID applicationId) {
        User caller = requireActive(callerId);
        EventApplication application =
                events.findApplication(applicationId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Event row = events.findById(application.eventId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (row.hidden() || !row.organizerId().equals(caller.id())) {
            throw AuthException.notFound(NOT_FOUND);
        }
        if (application.status() != EventApplicationStatus.PENDING) {
            throw AuthException.notFound(NOT_FOUND);
        }
        events.updateApplication(application.withStatus(EventApplicationStatus.DECLINED, clock.instant()));
    }

    private void cancelOccurrence(EventOccurrence occurrence, Instant now) {
        events.updateOccurrence(occurrence.cancelled(now));
        for (EventApplication application : events.applicationsForEvent(occurrence.eventId())) {
            if (!application.occurrenceId().equals(occurrence.id())) {
                continue;
            }
            if (application.status() == EventApplicationStatus.PENDING
                    || application.status() == EventApplicationStatus.ACCEPTED) {
                events.updateApplication(application.withStatus(EventApplicationStatus.CANCELLED, now));
            }
        }
    }

    private void rescheduleFirst(Event event) {
        List<EventOccurrence> occurrences = events.occurrences(event.id());
        if (occurrences.isEmpty()) {
            events.saveOccurrences(List.of(new EventOccurrence(UUID.randomUUID(), event.id(), event.startsAt(), null)));
            return;
        }
        EventOccurrence first = occurrences.stream()
                .min(Comparator.comparing(EventOccurrence::startsAt))
                .orElseThrow();
        events.updateOccurrence(
                new EventOccurrence(first.id(), first.eventId(), event.startsAt(), first.cancelledAt()));
    }

    private void extendOccurrences(Event event) {
        if (event.instant() || event.cancelled()) {
            return;
        }
        Instant now = clock.instant();
        Instant windowEnd = WeeklyRrule.defaultWindowEnd(now.isAfter(event.startsAt()) ? now : event.startsAt());
        WeeklyRrule rule;
        try {
            rule = WeeklyRrule.parse(event.recurrence());
        } catch (IllegalArgumentException ex) {
            return;
        }
        Set<Instant> existing = new HashSet<>();
        Instant last = event.startsAt();
        for (EventOccurrence occurrence : events.occurrences(event.id())) {
            existing.add(occurrence.startsAt());
            if (occurrence.startsAt().isAfter(last)) {
                last = occurrence.startsAt();
            }
        }
        if (!last.isBefore(windowEnd)) {
            return;
        }
        List<EventOccurrence> extra = new ArrayList<>();
        for (Instant start : rule.occurrences(event.startsAt(), windowEnd)) {
            if (!existing.contains(start)) {
                extra.add(new EventOccurrence(UUID.randomUUID(), event.id(), start, null));
            }
        }
        if (!extra.isEmpty()) {
            events.saveOccurrences(extra);
        }
    }

    private EventOccurrence resolveOccurrence(Event event, UUID occurrenceId) {
        List<EventOccurrence> occurrences = events.occurrences(event.id());
        if (occurrenceId != null) {
            return occurrences.stream()
                    .filter(occurrence -> occurrence.id().equals(occurrenceId))
                    .findFirst()
                    .orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        }
        if (!event.instant()) {
            throw AuthException.validation("occurrenceId is required", new FieldIssue("occurrenceId", "required"));
        }
        if (occurrences.isEmpty()) {
            throw AuthException.notFound(NOT_FOUND);
        }
        return occurrences.getFirst();
    }

    private List<Instant> occurrenceStarts(Instant startsAt, String recurrence, Instant now) {
        Instant windowEnd = WeeklyRrule.defaultWindowEnd(startsAt.isAfter(now) ? startsAt : now);
        if (recurrence == null) {
            return List.of(startsAt);
        }
        try {
            WeeklyRrule rule = WeeklyRrule.parse(recurrence);
            List<Instant> starts = rule.occurrences(startsAt, windowEnd);
            if (starts.isEmpty()) {
                throw AuthException.validation(
                        "recurrence produces no occurrences", new FieldIssue("recurrence", "empty"));
            }
            return starts;
        } catch (IllegalArgumentException ex) {
            throw AuthException.validation("recurrence is not valid", new FieldIssue("recurrence", "format"));
        }
    }

    private VisibleEvent visible(Event row, User caller, boolean detail) {
        User organizer = users.findById(row.organizerId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Profile organizerProfile =
                profiles.findByUserId(organizer.id()).orElse(Profile.created(organizer.id(), organizer.handle()));
        Instant now = clock.instant();
        Instant windowStart = now.minusSeconds(WeeklyRrule.WINDOW_DAYS * 24L * 3600L);
        Instant windowEnd = WeeklyRrule.defaultWindowEnd(now);
        List<VisibleOccurrence> occurrences = new ArrayList<>();
        VisibleOccurrence nextOpen = null;
        for (EventOccurrence occurrence : events.occurrences(row.id())) {
            if (occurrence.startsAt().isBefore(windowStart)
                    || occurrence.startsAt().isAfter(windowEnd)) {
                continue;
            }
            int accepted = events.countAccepted(occurrence.id());
            int remaining = occurrence.cancelled() ? 0 : Math.max(0, row.capacity() - accepted);
            VisibleOccurrence visible = new VisibleOccurrence(occurrence, accepted, remaining);
            occurrences.add(visible);
            if (nextOpen == null
                    && !occurrence.cancelled()
                    && occurrence.startsAt().isAfter(now)) {
                nextOpen = visible;
            }
        }
        occurrences.sort(Comparator.comparing(item -> item.occurrence().startsAt()));
        int remainingSeats = nextOpen == null ? 0 : nextOpen.remainingSeats();
        VisibleApplication viewerApplication = viewerApplication(row, caller.id(), nextOpen);
        List<VisibleApplicant> pending = List.of();
        List<UUID> invitees = List.of();
        if (detail && row.organizerId().equals(caller.id())) {
            pending = rankPending(row, occurrences);
            if (row.visibility() == EventVisibility.PRIVATE) {
                invitees = events.inviteeIds(row.id());
            }
        }
        if (!detail) {
            occurrences = nextOpen == null ? List.of() : List.of(nextOpen);
        }
        return new VisibleEvent(
                row, organizer, organizerProfile, occurrences, remainingSeats, viewerApplication, pending, invitees);
    }

    private List<VisibleApplicant> rankPending(Event event, List<VisibleOccurrence> occurrences) {
        Instant now = clock.instant();
        UUID occurrenceId = occurrences.stream()
                .filter(item -> !item.occurrence().cancelled()
                        && item.occurrence().startsAt().isAfter(now))
                .map(item -> item.occurrence().id())
                .findFirst()
                .orElse(null);
        if (occurrenceId == null) {
            return List.of();
        }
        List<VisibleApplicant> ranked = new ArrayList<>();
        for (EventApplication application : events.pendingForOccurrence(occurrenceId)) {
            User applicant = users.findById(application.applicantId()).orElse(null);
            if (applicant == null || !applicant.active()) {
                continue;
            }
            Profile profile =
                    profiles.findByUserId(applicant.id()).orElse(Profile.created(applicant.id(), applicant.handle()));
            int history = events.countAcceptedCoAttendance(event.organizerId(), applicant.id());
            double score = MatchingScore.score(event, profile, true, history);
            ranked.add(new VisibleApplicant(application, applicant, profile, score));
        }
        ranked.sort(Comparator.comparingDouble(VisibleApplicant::matchingScore)
                .reversed()
                .thenComparing(item -> item.application().createdAt()));
        return ranked;
    }

    private VisibleApplication viewerApplication(Event row, UUID callerId, VisibleOccurrence nextOpen) {
        if (nextOpen != null) {
            return events.findApplication(nextOpen.occurrence().id(), callerId)
                    .map(this::toVisibleApplication)
                    .orElse(null);
        }
        return events.applicationsForEvent(row.id()).stream()
                .filter(application -> application.applicantId().equals(callerId))
                .max(Comparator.comparing(EventApplication::createdAt).thenComparing(EventApplication::id))
                .map(this::toVisibleApplication)
                .orElse(null);
    }

    private VisibleApplication toVisibleApplication(EventApplication application) {
        User applicant = users.findById(application.applicantId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Profile profile =
                profiles.findByUserId(applicant.id()).orElse(Profile.created(applicant.id(), applicant.handle()));
        return new VisibleApplication(application, applicant, profile);
    }

    private Event requireVisible(User caller, UUID eventId) {
        Event row = events.findById(eventId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        if (!EventAccess.canView(row, caller, friendships, users, events)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        return row;
    }

    private UUID requireCover(UUID ownerId, UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        Media row = media.findById(mediaId)
                .orElseThrow(() ->
                        AuthException.validation("media is not allowed", new FieldIssue("coverMediaId", "invalid")));
        if (!row.ownerId().equals(ownerId)
                || row.kind() != MediaKind.EVENT
                || row.status() != MediaStatus.READY
                || row.deletedAt() != null
                || row.mime() == null
                || !row.mime().startsWith("image/")) {
            throw AuthException.validation("media is not allowed", new FieldIssue("coverMediaId", "invalid"));
        }
        events.findByCoverMediaId(mediaId).ifPresent(existing -> {
            throw AuthException.validation("media is not allowed", new FieldIssue("coverMediaId", "attached"));
        });
        return mediaId;
    }

    private List<UUID> normalizeInvitees(UUID organizerId, EventVisibility visibility, List<UUID> inviteeIds) {
        List<UUID> ids = inviteeIds == null ? List.of() : List.copyOf(inviteeIds);
        if (visibility != EventVisibility.PRIVATE) {
            return List.of();
        }
        Set<UUID> unique = new HashSet<>();
        List<UUID> cleaned = new ArrayList<>();
        for (UUID id : ids) {
            if (id == null || id.equals(organizerId) || !unique.add(id)) {
                continue;
            }
            User invitee = users.findById(id).orElse(null);
            if (invitee == null || !invitee.active()) {
                throw AuthException.validation("invitee is not allowed", new FieldIssue("inviteeIds", "invalid"));
            }
            if (friendships.isBlockedEitherWay(organizerId, id)) {
                throw AuthException.validation("invitee is not allowed", new FieldIssue("inviteeIds", "blocked"));
            }
            cleaned.add(id);
        }
        return cleaned;
    }

    private static String normalizeRecurrence(String recurrence) {
        if (recurrence == null || recurrence.isBlank()) {
            return null;
        }
        return recurrence.trim();
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        if (!"instant".equals(kind) && !"recurring".equals(kind)) {
            throw AuthException.validation("kind is not allowed", new FieldIssue("kind", "enum"));
        }
        return kind;
    }

    private static List<String> normalizeTags(List<String> tags) {
        List<String> source = tags == null ? List.of() : tags;
        if (source.size() > MAX_TAGS) {
            throw AuthException.validation("too many tags", new FieldIssue("tags", "max"));
        }
        List<String> cleaned = new ArrayList<>();
        for (String tag : source) {
            if (tag == null || tag.isBlank() || tag.length() > MAX_TAG) {
                throw AuthException.validation("tag is not valid", new FieldIssue("tags", "length"));
            }
            cleaned.add(tag.trim());
        }
        return cleaned;
    }

    private static String requireText(String value, String path, int min, int max) {
        if (value == null || value.isBlank()) {
            throw AuthException.validation(path + " is required", new FieldIssue(path, "required"));
        }
        String trimmed = value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw AuthException.validation(path + " is not valid", new FieldIssue(path, "length"));
        }
        return trimmed;
    }

    private static String optionalText(String value, String path, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw AuthException.validation(path + " is too long", new FieldIssue(path, "max"));
        }
        return trimmed;
    }

    private static int requireRange(Integer value, String path, int min, int max) {
        if (value == null) {
            throw AuthException.validation(path + " is required", new FieldIssue(path, "required"));
        }
        if (value < min || value > max) {
            throw AuthException.validation(path + " is not valid", new FieldIssue(path, "range"));
        }
        return value;
    }

    private static Double optionalCoord(Double value, String path, double min, double max) {
        if (value == null) {
            return null;
        }
        if (value < min || value > max) {
            throw AuthException.validation(path + " is not valid", new FieldIssue(path, "range"));
        }
        return value;
    }

    private User requireActive(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> AuthException.unauthenticated("missing or invalid access token"));
        if (!user.active()) {
            throw AuthException.unauthenticated("missing or invalid access token");
        }
        return user;
    }
}
