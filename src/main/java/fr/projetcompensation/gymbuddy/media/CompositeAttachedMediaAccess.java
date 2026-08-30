package fr.projetcompensation.gymbuddy.media;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompositeAttachedMediaAccess implements AttachedMediaAccess {

    private final List<AttachedMediaAccess> delegates;

    public CompositeAttachedMediaAccess(AttachedMediaAccess... delegates) {
        List<AttachedMediaAccess> copy = new ArrayList<>();
        if (delegates != null) {
            for (AttachedMediaAccess delegate : delegates) {
                if (delegate != null) {
                    copy.add(delegate);
                }
            }
        }
        this.delegates = List.copyOf(copy);
    }

    @Override
    public boolean canRead(UUID viewerId, Media media) {
        for (AttachedMediaAccess delegate : delegates) {
            if (delegate.canRead(viewerId, media)) {
                return true;
            }
        }
        return false;
    }
}
