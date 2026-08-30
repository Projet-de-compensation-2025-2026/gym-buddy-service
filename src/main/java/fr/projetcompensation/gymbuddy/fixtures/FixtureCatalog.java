package fr.projetcompensation.gymbuddy.fixtures;

import java.util.List;

/** City × sport clusters so search and matching look right. */
public final class FixtureCatalog {

    public static final List<Cluster> CLUSTERS = List.of(
            new Cluster("Porto", "running", 41.1496, -8.6109),
            new Cluster("Porto", "weightlifting", 41.1621, -8.6300),
            new Cluster("Lisboa", "yoga", 38.7223, -9.1393),
            new Cluster("Lisboa", "swimming", 38.7436, -9.1600),
            new Cluster("Paris", "cycling", 48.8566, 2.3522),
            new Cluster("Paris", "climbing", 48.8744, 2.3526),
            new Cluster("Lyon", "boxing", 45.7640, 4.8357),
            new Cluster("Marseille", "football", 43.2965, 5.3698),
            new Cluster("Bordeaux", "tennis", 44.8378, -0.5792),
            new Cluster("Nantes", "calisthenics", 47.2184, -1.5536),
            new Cluster("Coimbra", "running", 40.2033, -8.4103),
            new Cluster("Braga", "weightlifting", 41.5454, -8.4265));

    public static final String ALEX_HANDLE = "demo.alex";
    public static final String BLAKE_HANDLE = "demo.blake";
    public static final String MOD_HANDLE = "demo.mod";
    public static final String ADMIN_HANDLE = "demo.admin";

    public static final List<String> DEMO_HANDLES = List.of(ALEX_HANDLE, BLAKE_HANDLE, MOD_HANDLE, ADMIN_HANDLE);

    private FixtureCatalog() {}

    public record Cluster(String city, String sport, double lat, double lng) {}

    public static Cluster cluster(int index) {
        return CLUSTERS.get(Math.floorMod(index, CLUSTERS.size()));
    }
}
