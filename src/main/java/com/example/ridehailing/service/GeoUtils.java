package com.example.ridehailing.service;

public final class GeoUtils {
    private static final double R = 6_371_000.0;
    private GeoUtils() {}

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    /** Bearing in degrees [0,360). */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** Moves at most stepMeters toward the target; returns {lat, lon}. Snaps to target when within step. */
    public static double[] moveToward(double lat, double lon, double tLat, double tLon, double stepMeters) {
        double d = haversineMeters(lat, lon, tLat, tLon);
        if (d <= stepMeters || d == 0) return new double[]{tLat, tLon};
        double f = stepMeters / d;
        return new double[]{lat + (tLat - lat) * f, lon + (tLon - lon) * f};
    }
}
