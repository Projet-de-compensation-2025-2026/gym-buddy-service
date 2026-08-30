package fr.projetcompensation.gymbuddy.suggestions;

public final class GeoScore {

    public static final double D_KM = 25.0;
    public static final double CITY_WITHOUT_COORDS = 0.4;
    public static final double CANDIDATE_RADIUS_KM = 15.0;
    private static final double EARTH_KM = 6371.0;

    private GeoScore() {}

    public static double feature(Double lat1, Double lng1, Double lat2, Double lng2, String city1, String city2) {
        if (hasCoords(lat1, lng1) && hasCoords(lat2, lng2)) {
            double distance = haversineKm(lat1, lng1, lat2, lng2);
            return 1.0 - Math.min(distance, D_KM) / D_KM;
        }
        if (sameCity(city1, city2)) {
            return CITY_WITHOUT_COORDS;
        }
        return 0.0;
    }

    public static boolean nearbyCandidate(
            Double lat1, Double lng1, Double lat2, Double lng2, String city1, String city2) {
        if (hasCoords(lat1, lng1) && hasCoords(lat2, lng2)) {
            return haversineKm(lat1, lng1, lat2, lng2) <= CANDIDATE_RADIUS_KM;
        }
        return sameCity(city1, city2);
    }

    public static boolean sameCity(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String a = left.trim();
        String b = right.trim();
        return !a.isEmpty() && a.equalsIgnoreCase(b);
    }

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private static boolean hasCoords(Double lat, Double lng) {
        return lat != null && lng != null;
    }
}
