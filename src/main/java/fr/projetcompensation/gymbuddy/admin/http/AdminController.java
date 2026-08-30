package fr.projetcompensation.gymbuddy.admin.http;

import fr.projetcompensation.gymbuddy.admin.AdminService;
import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.fixtures.FixtureMagnitude;
import fr.projetcompensation.gymbuddy.openapi.api.AdminApi;
import fr.projetcompensation.gymbuddy.openapi.model.AdminContentPage;
import fr.projetcompensation.gymbuddy.openapi.model.AdminMediaPage;
import fr.projetcompensation.gymbuddy.openapi.model.AdminUser;
import fr.projetcompensation.gymbuddy.openapi.model.AdminUserPage;
import fr.projetcompensation.gymbuddy.openapi.model.AuditEventPage;
import fr.projetcompensation.gymbuddy.openapi.model.GenerateFixturesRequest;
import fr.projetcompensation.gymbuddy.openapi.model.HideContentRequest;
import fr.projetcompensation.gymbuddy.openapi.model.PatchUserRoleRequest;
import fr.projetcompensation.gymbuddy.openapi.model.Report;
import fr.projetcompensation.gymbuddy.openapi.model.ReportPage;
import fr.projetcompensation.gymbuddy.openapi.model.StaffReasonRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController implements AdminApi {

    private final ObjectProvider<AdminService> admin;
    private final HttpServletRequest httpRequest;

    public AdminController(ObjectProvider<AdminService> admin, HttpServletRequest httpRequest) {
        this.admin = admin;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<AdminUserPage> getAdminUsers(
            @Nullable String q, @Nullable String role, @Nullable String status, @Nullable String after, Integer size) {
        return ResponseEntity.ok(
                AdminResponses.toUserPage(service().listUsers(principal().userId(), q, role, status, after, size)));
    }

    @Override
    public ResponseEntity<AdminUser> postAdminUsersIdLock(UUID id, @Nullable StaffReasonRequest request) {
        return ResponseEntity.ok(
                AdminResponses.toUser(service().lock(principal().userId(), id, reason(request))));
    }

    @Override
    public ResponseEntity<AdminUser> postAdminUsersIdUnlock(UUID id, @Nullable StaffReasonRequest request) {
        return ResponseEntity.ok(
                AdminResponses.toUser(service().unlock(principal().userId(), id, reason(request))));
    }

    @Override
    public ResponseEntity<AdminUser> patchAdminUsersIdRole(UUID id, PatchUserRoleRequest request) {
        String role = request.getRole() == null ? null : request.getRole().getValue();
        return ResponseEntity.ok(
                AdminResponses.toUser(service().changeRole(principal().userId(), id, role, request.getReason())));
    }

    @Override
    public ResponseEntity<AdminContentPage> getAdminContent(
            String type, @Nullable String q, @Nullable Boolean hidden, @Nullable String after, Integer size) {
        return ResponseEntity.ok(AdminResponses.toContentPage(
                service().listContent(principal().userId(), type, q, hidden, after, size)));
    }

    @Override
    public ResponseEntity<Void> postAdminContentTypeIdHide(String type, UUID id, HideContentRequest request) {
        service().hide(principal().userId(), type, id, request.getReason());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> postAdminContentTypeIdUnhide(
            String type, UUID id, @Nullable StaffReasonRequest request) {
        service().unhide(principal().userId(), type, id, reason(request));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ReportPage> getAdminReports(
            String status, @Nullable String q, @Nullable String after, Integer size) {
        return ResponseEntity.ok(
                AdminResponses.toReportPage(service().listReports(principal().userId(), status, q, after, size)));
    }

    @Override
    public ResponseEntity<Report> postAdminReportsIdResolve(UUID id, @Nullable StaffReasonRequest request) {
        return ResponseEntity.ok(
                AdminResponses.toReport(service().resolve(principal().userId(), id, reason(request))));
    }

    @Override
    public ResponseEntity<AdminMediaPage> getAdminMedia(@Nullable String q, @Nullable String after, Integer size) {
        return ResponseEntity.ok(
                AdminResponses.toMediaPage(service().listMedia(principal().userId(), q, after, size)));
    }

    @Override
    public ResponseEntity<Void> postAdminFixtures(@Nullable GenerateFixturesRequest request) {
        service().generateFixtures(principal().userId(), magnitude(request));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> postAdminFixturesReset() {
        service().resetFixtures(principal().userId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AuditEventPage> getAdminAudit(
            @Nullable String q, @Nullable String action, @Nullable String after, Integer size) {
        return ResponseEntity.ok(
                AdminResponses.toAuditPage(service().listAudit(principal().userId(), q, action, after, size)));
    }

    private AdminService service() {
        AdminService service = admin.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("admin is not configured");
        }
        return service;
    }

    private AuthPrincipal principal() {
        return AuthPrincipal.require(httpRequest);
    }

    private static String reason(@Nullable StaffReasonRequest request) {
        return request == null ? null : request.getReason();
    }

    private static FixtureMagnitude magnitude(@Nullable GenerateFixturesRequest request) {
        if (request == null) {
            return FixtureMagnitude.demo();
        }
        return FixtureMagnitude.of(
                request.getUsers(),
                request.getFriendships(),
                request.getPosts(),
                request.getComments(),
                request.getEvents(),
                request.getApplications(),
                request.getMessages(),
                request.getMedia());
    }
}
