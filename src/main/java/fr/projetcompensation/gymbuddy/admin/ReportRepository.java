package fr.projetcompensation.gymbuddy.admin;

import fr.projetcompensation.gymbuddy.friends.InstantIdCursor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository {

    void save(Report report);

    void update(Report report);

    Optional<Report> findById(UUID id);

    Optional<Report> findOpen(UUID reporterId, String targetType, UUID targetId);

    List<Report> list(String status, String q, InstantIdCursor after, int limit);
}
