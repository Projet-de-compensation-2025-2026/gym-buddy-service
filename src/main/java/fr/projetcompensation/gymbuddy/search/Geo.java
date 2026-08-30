package fr.projetcompensation.gymbuddy.search;

final class Geo {

    private static final double EARTH_KM = 6371.0;

    private Geo() {}

    static Double km(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return EARTH_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
