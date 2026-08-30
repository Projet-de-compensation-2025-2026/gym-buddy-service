package fr.projetcompensation.gymbuddy.admin.http;

import fr.projetcompensation.gymbuddy.admin.AdminPage;
import fr.projetcompensation.gymbuddy.admin.AuditEvent;
import fr.projetcompensation.gymbuddy.admin.ListedAdminMedia;
import fr.projetcompensation.gymbuddy.admin.ListedAdminUser;
import fr.projetcompensation.gymbuddy.admin.Report;
import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.openapi.model.AdminMedia;
import fr.projetcompensation.gymbuddy.openapi.model.AdminMediaPage;
import fr.projetcompensation.gymbuddy.openapi.model.AdminUser;
import fr.projetcompensation.gymbuddy.openapi.model.AdminUserPage;
import fr.projetcompensation.gymbuddy.openapi.model.AuditEventPage;
import fr.projetcompensation.gymbuddy.openapi.model.Page;
import fr.projetcompensation.gymbuddy.users.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class AdminResponses {

    private AdminResponses() {}

    public static AdminUser toUser(ListedAdminUser row) {
        User user = row.user();
        return new AdminUser(
                user.id(),
                user.email(),
                user.handle(),
                row.displayName(),
                AdminUser.RoleEnum.fromValue(user.role().wireValue()),
                AdminUser.StatusEnum.fromValue(user.status().wireValue()),
                OffsetDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC),
                row.lastAdmin());
    }

    public static AdminUserPage toUserPage(AdminPage<ListedAdminUser> page) {
        Page meta = new Page(page.size());
        meta.setNext(page.next());
        return new AdminUserPage(page.data().stream().map(AdminResponses::toUser).toList(), meta);
    }

    public static fr.projetcompensation.gymbuddy.openapi.model.Report toReport(Report row) {
        return new fr.projetcompensation.gymbuddy.openapi.model.Report(
                row.id(),
                row.reporterId(),
                row.reporterHandle(),
                fr.projetcompensation.gymbuddy.openapi.model.Report.TargetTypeEnum.fromValue(row.targetType()),
                row.targetId(),
                row.reason(),
                fr.projetcompensation.gymbuddy.openapi.model.Report.StatusEnum.fromValue(row.status()),
                OffsetDateTime.ofInstant(row.createdAt(), ZoneOffset.UTC));
    }

    public static fr.projetcompensation.gymbuddy.openapi.model.ReportPage toReportPage(AdminPage<Report> page) {
        Page meta = new Page(page.size());
        meta.setNext(page.next());
        return new fr.projetcompensation.gymbuddy.openapi.model.ReportPage(
                page.data().stream().map(AdminResponses::toReport).toList(), meta);
    }

    public static AdminMedia toMedia(ListedAdminMedia row) {
        Media media = row.media();
        AdminMedia body = new AdminMedia(
                media.id(),
                media.ownerId(),
                row.ownerHandle(),
                AdminMedia.KindEnum.fromValue(media.kind().wireValue()),
                media.mime(),
                media.bytes(),
                AdminMedia.StatusEnum.fromValue(media.status().wireValue()),
                media.objectKey(),
                OffsetDateTime.ofInstant(media.createdAt(), ZoneOffset.UTC),
                media.hidden());
        body.setHiddenReason(media.hiddenReason());
        return body;
    }

    public static AdminMediaPage toMediaPage(AdminPage<ListedAdminMedia> page) {
        Page meta = new Page(page.size());
        meta.setNext(page.next());
        return new AdminMediaPage(page.data().stream().map(AdminResponses::toMedia).toList(), meta);
    }

    public static fr.projetcompensation.gymbuddy.openapi.model.AuditEvent toAudit(AuditEvent row) {
        fr.projetcompensation.gymbuddy.openapi.model.AuditEvent body =
                new fr.projetcompensation.gymbuddy.openapi.model.AuditEvent(
                        row.id(),
                        row.actorId(),
                        row.actorHandle(),
                        fr.projetcompensation.gymbuddy.openapi.model.AuditEvent.ActionEnum.fromValue(row.action()),
                        fr.projetcompensation.gymbuddy.openapi.model.AuditEvent.TargetTypeEnum.fromValue(
                                row.targetType()),
                        row.targetId(),
                        OffsetDateTime.ofInstant(row.at(), ZoneOffset.UTC));
        body.setReason(row.reason());
        return body;
    }

    public static AuditEventPage toAuditPage(AdminPage<AuditEvent> page) {
        Page meta = new Page(page.size());
        meta.setNext(page.next());
        return new AuditEventPage(page.data().stream().map(AdminResponses::toAudit).toList(), meta);
    }
}
