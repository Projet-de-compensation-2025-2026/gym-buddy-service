package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.FieldIssue;
import fr.projetcompensation.gymbuddy.comments.Comment;
import fr.projetcompensation.gymbuddy.comments.CommentRepository;
import fr.projetcompensation.gymbuddy.events.Event;
import fr.projetcompensation.gymbuddy.events.EventRepository;
import fr.projetcompensation.gymbuddy.fixtures.FixtureGenerator;
import fr.projetcompensation.gymbuddy.fixtures.FixtureGuard;
import fr.projetcompensation.gymbuddy.fixtures.FixtureMagnitude;
import fr.projetcompensation.gymbuddy.friends.FriendshipRepository;
import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaRepository;
import fr.projetcompensation.gymbuddy.posts.Post;
import fr.projetcompensation.gymbuddy.posts.PostAccess;
import fr.projetcompensation.gymbuddy.posts.PostRepository;
import fr.projetcompensation.gymbuddy.profiles.ProfileRepository;
import fr.projetcompensation.gymbuddy.users.User;
import fr.projetcompensation.gymbuddy.users.UserRepository;
import fr.projetcompensation.gymbuddy.users.UserRole;
import fr.projetcompensation.gymbuddy.users.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class AdminService {

    static final UUID FIXTURES_TARGET = UUID.fromString("00000000-0000-4000-8000-000000000069");
    private static final String NOT_FOUND = "not found";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Set<String> CONTENT_TYPES = Set.of("post", "comment", "event", "media");
    private static final Set<String> REPORT_TYPES = Set.of("user", "post", "comment", "event");

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final EventRepository events;
    private final MediaRepository media;
    private final FriendshipRepository friendships;
    private final ReportRepository reports;
    private final AuditEventRepository audit;
    private final AdminCatalog catalog;
    private final Clock clock;
    private final boolean production;
    private final FixtureGenerator fixtures;

    public AdminService(
            UserRepository users,
            ProfileRepository profiles,
            PostRepository posts,
            CommentRepository comments,
            EventRepository events,
            MediaRepository media,
            FriendshipRepository friendships,
            ReportRepository reports,
            AuditEventRepository audit,
            AdminCatalog catalog,
            Clock clock,
            boolean production,
            FixtureGenerator fixtures) {
        this.users = users;
        this.profiles = profiles;
        this.posts = posts;
        this.comments = comments;
        this.events = events;
        this.media = media;
        this.friendships = friendships;
        this.reports = reports;
        this.audit = audit;
        this.catalog = catalog;
        this.clock = clock;
        this.production = production;
        this.fixtures = fixtures;
    }

    public AdminPage<ListedAdminUser> listUsers(
            UUID callerId, String q, String role, String status, String after, Integer size) {
        requireStaff(callerId);
        int pageSize = pageSize(size);
        return AdminPage.of(
                catalog.listUsers(blankToNull(q), blankToNull(role), blankToNull(status), cursor(after), pageSize + 1),
                pageSize,
                row -> row.cursor().encode());
    }

    public ListedAdminUser lock(UUID callerId, UUID targetId, String reason) {
        User caller = requireStaff(callerId);
        User target = requireUser(targetId);
        if (lastAdmin(target)) {
            throw AuthException.conflict("last admin cannot be locked", new FieldIssue("id", "last_admin"));
        }
        users.update(target.withStatus(UserStatus.LOCKED));
        writeAudit(caller, AuditEvent.LOCK_USER, "user", target.id(), reason);
        return view(users.findById(target.id()).orElseThrow());
    }

    public ListedAdminUser unlock(UUID callerId, UUID targetId, String reason) {
        User caller = requireStaff(callerId);
        User target = requireUser(targetId);
        users.update(target.withStatus(UserStatus.ACTIVE));
        writeAudit(caller, AuditEvent.UNLOCK_USER, "user", target.id(), reason);
        return view(users.findById(target.id()).orElseThrow());
    }

    public ListedAdminUser changeRole(UUID callerId, UUID targetId, String roleWire, String reason) {
        User caller = requireAdmin(callerId);
        User target = requireUser(targetId);
        UserRole role;
        try {
            role = UserRole.fromWire(roleWire);
        } catch (RuntimeException ex) {
            throw AuthException.validation("role is not allowed", new FieldIssue("role", "enum"));
        }
        if (target.role() == UserRole.ADMIN && role != UserRole.ADMIN && lastAdmin(target)) {
            throw AuthException.conflict("last admin cannot be demoted", new FieldIssue("id", "last_admin"));
        }
        users.update(target.withRole(role));
        writeAudit(caller, AuditEvent.CHANGE_ROLE, "user", target.id(), reason);
        return view(users.findById(target.id()).orElseThrow());
    }

    public void hide(UUID callerId, String type, UUID id, String reason) {
        User caller = requireStaff(callerId);
        String normalized = requireReason(reason);
        String kind = requireContentType(type);
        Instant now = clock.instant();
        switch (kind) {
            case "post" -> {
                Post row = posts.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                if (row.deleted()) {
                    throw AuthException.notFound(NOT_FOUND);
                }
                posts.update(row.hide(now, normalized));
            }
            case "comment" -> {
                Comment row = comments.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                comments.update(row.hide(now));
            }
            case "media" -> {
                Media row = media.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                media.update(row.hide(now, normalized));
            }
            case "event" -> {
                Event row = events.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                events.update(row.hide(now));
            }
            default -> throw AuthException.notFound(NOT_FOUND);
        }
        writeAudit(caller, AuditEvent.HIDE_CONTENT, kind, id, normalized);
    }

    public void unhide(UUID callerId, String type, UUID id, String reason) {
        User caller = requireStaff(callerId);
        String kind = requireContentType(type);
        switch (kind) {
            case "post" -> {
                Post row = posts.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                posts.update(row.unhide());
            }
            case "comment" -> {
                Comment row = comments.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                comments.update(row.unhide());
            }
            case "media" -> {
                Media row = media.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                media.update(row.unhide());
            }
            case "event" -> {
                Event row = events.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                events.update(row.unhide());
            }
            default -> throw AuthException.notFound(NOT_FOUND);
        }
        writeAudit(caller, AuditEvent.UNHIDE_CONTENT, kind, id, reason);
    }

    public AdminPage<Report> listReports(UUID callerId, String status, String q, String after, Integer size) {
        requireStaff(callerId);
        int pageSize = pageSize(size);
        String filter = blankToNull(status) == null ? Report.OPEN : status;
        if (!Report.OPEN.equals(filter) && !Report.RESOLVED.equals(filter)) {
            throw AuthException.validation("status is not allowed", new FieldIssue("status", "enum"));
        }
        return AdminPage.of(
                reports.list(filter, blankToNull(q), cursor(after), pageSize + 1), pageSize, row -> row.cursor()
                        .encode());
    }

    public Report resolve(UUID callerId, UUID reportId, String reason) {
        User caller = requireStaff(callerId);
        Report row = reports.findById(reportId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
        Report updated = row.open() ? row.resolved() : row;
        if (row.open()) {
            reports.update(updated);
        }
        writeAudit(caller, AuditEvent.RESOLVE_REPORT, "report", reportId, reason);
        return updated;
    }

    public Report createReport(UUID callerId, String targetType, UUID targetId, String reason) {
        User caller = requireActive(callerId);
        String kind = targetType == null ? "" : targetType.trim().toLowerCase(Locale.ROOT);
        if (!REPORT_TYPES.contains(kind)) {
            throw AuthException.validation("targetType is not allowed", new FieldIssue("targetType", "enum"));
        }
        String normalized = requireReason(reason);
        requireVisibleTarget(caller, kind, targetId);
        if (reports.findOpen(caller.id(), kind, targetId).isPresent()) {
            throw AuthException.conflict("already reported", new FieldIssue("targetId", "duplicate"));
        }
        Instant now = clock.instant();
        Report row = new Report(
                UUID.randomUUID(), caller.id(), caller.handle(), kind, targetId, normalized, Report.OPEN, now);
        reports.save(row);
        return row;
    }

    public AdminPage<ListedAdminMedia> listMedia(UUID callerId, String q, String after, Integer size) {
        requireStaff(callerId);
        int pageSize = pageSize(size);
        return AdminPage.of(
                catalog.listMedia(blankToNull(q), cursor(after), pageSize + 1), pageSize, row -> row.cursor()
                        .encode());
    }

    public AdminPage<ListedAdminContent> listContent(
            UUID callerId, String type, String q, Boolean hidden, String after, Integer size) {
        requireStaff(callerId);
        String kind = requireListContentType(type);
        int pageSize = pageSize(size);
        return AdminPage.of(
                catalog.listContent(kind, blankToNull(q), hidden, cursor(after), pageSize + 1),
                pageSize,
                row -> row.cursor().encode());
    }

    public void generateFixtures(UUID callerId, FixtureMagnitude magnitude) {
        User caller = requireAdmin(callerId);
        FixtureGuard.requireNonProduction(production);
        requireFixtures().generate(magnitude == null ? FixtureMagnitude.demo() : magnitude);
        writeAudit(caller, AuditEvent.GENERATE_FIXTURES, "fixtures", FIXTURES_TARGET, "generate");
    }

    public void resetFixtures(UUID callerId) {
        User caller = requireAdmin(callerId);
        FixtureGuard.requireNonProduction(production);
        requireFixtures().reset(caller.id());
        writeAudit(caller, AuditEvent.RESET_FIXTURES, "fixtures", FIXTURES_TARGET, "reset");
    }

    private FixtureGenerator requireFixtures() {
        if (fixtures == null) {
            throw AuthException.forbidden("fixtures are disabled");
        }
        return fixtures;
    }

    public AdminPage<AuditEvent> listAudit(UUID callerId, String q, String action, String after, Integer size) {
        User caller = requireStaff(callerId);
        int pageSize = pageSize(size);
        boolean admin = caller.role() == UserRole.ADMIN;
        UUID actorFilter = admin ? null : caller.id();
        boolean contentOnly = !admin;
        return AdminPage.of(
                audit.list(actorFilter, contentOnly, blankToNull(q), blankToNull(action), cursor(after), pageSize + 1),
                pageSize,
                row -> row.cursor().encode());
    }

    private User requireStaff(UUID callerId) {
        User caller = requireActive(callerId);
        if (!caller.isStaff()) {
            throw AuthException.notFound(NOT_FOUND);
        }
        return caller;
    }

    private User requireAdmin(UUID callerId) {
        User caller = requireStaff(callerId);
        if (caller.role() != UserRole.ADMIN) {
            throw AuthException.forbidden("admin only");
        }
        return caller;
    }

    private User requireActive(UUID callerId) {
        User caller = users.findById(callerId)
                .orElseThrow(() -> AuthException.unauthenticated("missing or invalid access token"));
        if (!caller.active()) {
            throw AuthException.unauthenticated("missing or invalid access token");
        }
        return caller;
    }

    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
    }

    private boolean lastAdmin(User user) {
        return user.role() == UserRole.ADMIN && catalog.countAdmins() <= 1;
    }

    private ListedAdminUser view(User user) {
        String displayName = profiles.findByUserId(user.id())
                .map(profile -> profile.displayName())
                .orElse(user.handle());
        return new ListedAdminUser(user, displayName, lastAdmin(user));
    }

    private void writeAudit(User actor, String action, String targetType, UUID targetId, String reason) {
        audit.save(new AuditEvent(
                UUID.randomUUID(),
                actor.id(),
                actor.handle(),
                action,
                targetType,
                targetId,
                blankToNull(reason),
                clock.instant()));
    }

    private void requireVisibleTarget(User caller, String kind, UUID targetId) {
        switch (kind) {
            case "user" -> {
                User target = users.findById(targetId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                if (target.closed()) {
                    throw AuthException.notFound(NOT_FOUND);
                }
            }
            case "post" -> {
                Post row = posts.findById(targetId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                if (!PostAccess.canView(row, caller, friendships, users)) {
                    throw AuthException.notFound(NOT_FOUND);
                }
            }
            case "comment" -> {
                Comment row = comments.findById(targetId).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                if (row.hidden() || row.deleted()) {
                    throw AuthException.notFound(NOT_FOUND);
                }
                Post post = posts.findById(row.postId()).orElseThrow(() -> AuthException.notFound(NOT_FOUND));
                if (!PostAccess.canView(post, caller, friendships, users)) {
                    throw AuthException.notFound(NOT_FOUND);
                }
            }
            default -> throw AuthException.notFound(NOT_FOUND);
        }
    }

    private static String requireContentType(String type) {
        String kind = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(kind)) {
            throw AuthException.notFound(NOT_FOUND);
        }
        return kind;
    }

    private static String requireListContentType(String type) {
        String kind = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(kind)) {
            throw AuthException.validation("type is not allowed", new FieldIssue("type", "enum"));
        }
        return kind;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw AuthException.validation("reason is required", new FieldIssue("reason", "required"));
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 1000) {
            throw AuthException.validation("reason is too long", new FieldIssue("reason", "size"));
        }
        return trimmed;
    }

    private static int pageSize(Integer size) {
        int value = size == null ? DEFAULT_SIZE : size;
        if (value < 1 || value > MAX_SIZE) {
            throw AuthException.validation("size is out of range", new FieldIssue("size", "range"));
        }
        return value;
    }

    private static InstantIdCursor cursor(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        return InstantIdCursor.parse(after)
                .orElseThrow(() ->
                        AuthException.validation("after is not a valid cursor", new FieldIssue("after", "format")));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
