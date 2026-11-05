package com.example.ridehailing.service;

/** Pure surge/fare math and grid-cell mapping (no I/O, easy to property-test). */
public final class SurgePricing {
    private SurgePricing() {}

    /** Grid cell edge in degrees (about 1.1 km of latitude). */
    public static final double CELL_DEG = 0.01;

    private static long latIdx(double lat) { return (long) Math.floor((lat + 90) / CELL_DEG); }
    private static long lonIdx(double lon) { return (long) Math.floor((lon + 180) / CELL_DEG); }

    /** Cell id of the fixed lat/lon grid, geohash-like: "latIndex:lonIndex". */
    public static String cellId(double lat, double lon) {
        return latIdx(lat) + ":" + lonIdx(lon);
    }

    /** Center {lat, lon} of the cell containing the point (derived from the same indices as {@link #cellId}). */
    public static double[] cellCenter(double lat, double lon) {
        return new double[]{(latIdx(lat) + 0.5) * CELL_DEG - 90, (lonIdx(lon) + 0.5) * CELL_DEG - 180};
    }

    /**
     * Multiplier = 1 while demand does not exceed supply; above that it grows linearly with the
     * demand/supply ratio (supply floored at 1), capped at {@code max}. Rounded to 2 decimals.
     */
    public static double multiplier(long demand, long supply, double sensitivity, double max) {
        double ratio = (double) Math.max(demand, 0) / Math.max(supply, 1);
        double m = ratio <= 1.0 ? 1.0 : 1.0 + (ratio - 1.0) * sensitivity;
        m = Math.min(Math.max(m, 1.0), Math.max(max, 1.0));
        return Math.round(m * 100.0) / 100.0;
    }

    public static double fare(double distanceMeters, double baseFare, double perKm, double multiplier) {
        double f = (baseFare + perKm * distanceMeters / 1000.0) * multiplier;
        return Math.round(f * 100.0) / 100.0;
    }
}
