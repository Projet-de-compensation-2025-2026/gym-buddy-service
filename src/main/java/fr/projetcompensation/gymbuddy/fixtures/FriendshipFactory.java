package fr.projetcompensation.gymbuddy.fixtures;

import fr.projetcompensation.gymbuddy.friends.Friendship;
import fr.projetcompensation.gymbuddy.friends.FriendshipStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Power-law-ish accepted graph with city×sport clustering and a few hubs. */
public final class FriendshipFactory {

    public record Edge(UUID lo, UUID hi) {
        public Edge {
            if (lo.compareTo(hi) > 0) {
                UUID swap = lo;
                lo = hi;
                hi = swap;
            }
        }
    }

    private final long seed;
    private final Instant origin;
    private final Random random;

    public FriendshipFactory(long seed, Instant origin) {
        this.seed = seed;
        this.origin = origin;
        this.random = new Random(seed ^ 0x9e3779b97f4a7c15L);
    }

    public List<Friendship> create(List<UserDraft> users, int target) {
        int n = users.size();
        if (n < 2 || target <= 0) {
            return List.of();
        }
        int maxEdges = n * (n - 1) / 2;
        int want = Math.min(target, maxEdges);
        Set<Edge> edges = new LinkedHashSet<>();
        UUID alex = FixtureIds.demo(FixtureCatalog.ALEX_HANDLE);
        UUID blake = FixtureIds.demo(FixtureCatalog.BLAKE_HANDLE);
        edges.add(new Edge(alex, blake));
        int hubs = Math.max(3, n / 20);
        int attempts = 0;
        int attemptCap = Math.max(want * 20, 200);
        while (edges.size() < want && attempts < attemptCap) {
            attempts++;
            int sourceIndex = zipfIndex(n);
            UserDraft source = users.get(sourceIndex);
            boolean sameCluster = random.nextDouble() < 0.75;
            int destIndex = pickPeer(users, sourceIndex, sameCluster, hubs);
            if (destIndex < 0) {
                continue;
            }
            edges.add(new Edge(source.id(), users.get(destIndex).id()));
        }
        List<Friendship> rows = new ArrayList<>(edges.size());
        int i = 0;
        for (Edge edge : edges) {
            Instant at = origin.plusSeconds(60L + i);
            rows.add(new Friendship(
                    FixtureIds.of(seed, "friendship", i),
                    edge.lo(),
                    edge.hi(),
                    FriendshipStatus.ACCEPTED,
                    at,
                    at.plusSeconds(30)));
            i++;
        }
        return List.copyOf(rows);
    }

    private int zipfIndex(int n) {
        double u = random.nextDouble();
        int index = (int) Math.floor(Math.pow(u, 2.2) * n);
        return Math.min(n - 1, Math.max(0, index));
    }

    private int pickPeer(List<UserDraft> users, int sourceIndex, boolean sameCluster, int hubs) {
        UserDraft source = users.get(sourceIndex);
        if (sameCluster) {
            for (int tries = 0; tries < 8; tries++) {
                int candidate = random.nextInt(users.size());
                if (candidate != sourceIndex
                        && users.get(candidate).clusterIndex() % FixtureCatalog.CLUSTERS.size()
                                == source.clusterIndex() % FixtureCatalog.CLUSTERS.size()) {
                    return candidate;
                }
            }
        }
        if (random.nextDouble() < 0.4) {
            int hub = random.nextInt(Math.min(hubs, users.size()));
            if (hub != sourceIndex) {
                return hub;
            }
        }
        int dest = random.nextInt(users.size());
        return dest == sourceIndex ? -1 : dest;
    }
}
