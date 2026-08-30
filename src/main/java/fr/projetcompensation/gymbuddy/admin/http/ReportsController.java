package fr.projetcompensation.gymbuddy.admin.http;

import fr.projetcompensation.gymbuddy.admin.AdminService;
import fr.projetcompensation.gymbuddy.auth.AuthException;
import fr.projetcompensation.gymbuddy.auth.AuthPrincipal;
import fr.projetcompensation.gymbuddy.openapi.api.ReportsApi;
import fr.projetcompensation.gymbuddy.openapi.model.CreateReportRequest;
import fr.projetcompensation.gymbuddy.openapi.model.Report;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportsController implements ReportsApi {

    private final ObjectProvider<AdminService> admin;
    private final HttpServletRequest httpRequest;

    public ReportsController(ObjectProvider<AdminService> admin, HttpServletRequest httpRequest) {
        this.admin = admin;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<Report> postReports(CreateReportRequest request, @Nullable String idempotencyKey) {
        AuthPrincipal principal = AuthPrincipal.require(httpRequest);
        String targetType = request.getTargetType() == null ? null : request.getTargetType().getValue();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminResponses.toReport(service()
                        .createReport(principal.userId(), targetType, request.getTargetId(), request.getReason())));
    }

    private AdminService service() {
        AdminService service = admin.getIfAvailable();
        if (service == null) {
            throw AuthException.unauthenticated("admin is not configured");
        }
        return service;
    }
}
