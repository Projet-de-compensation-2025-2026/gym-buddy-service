package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.media.Media;
import fr.projetcompensation.gymbuddy.media.MediaKind;
import fr.projetcompensation.gymbuddy.media.MediaStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MediaFactory {

    private final long seed;
    private final Instant origin;

    public MediaFactory(long seed, Instant origin) {
        this.seed = seed;
        this.origin = origin;
    }

    public List<Media> create(List<UserDraft> users, int count) {
        if (users.isEmpty() || count <= 0) {
            return List.of();
        }
        List<Media> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UserDraft owner = users.get(i % users.size());
            MediaKind kind = i % 11 == 0 ? MediaKind.AVATAR : MediaKind.POST;
            rows.add(new Media(
                    FixtureIds.of(seed, "media", i),
                    owner.id(),
                    kind,
                    StockImages.MIME,
                    StockImages.bytes(),
                    0L,
                    MediaStatus.READY,
                    StockImages.key(i),
                    origin.plusSeconds(i),
                    null,
                    null,
                    null));
        }
        return List.copyOf(rows);
    }
}
