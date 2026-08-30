package fr.projetcompensation.gymbuddy.media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {

    void save(Media media);

    void update(Media media);

    void delete(UUID id);

    Optional<Media> findById(UUID id);

    long usedBytes(UUID ownerId);

    List<Media> findPendingCreatedBefore(Instant cutoff);

    List<Media> findPending();

    List<Media> findDeletedBefore(Instant cutoff);
}
