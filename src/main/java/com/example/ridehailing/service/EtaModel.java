package com.example.ridehailing.service;

/** Pure distance/speed ETA model. */
public final class EtaModel {
    private EtaModel() {}

    /** Exponential moving average; the first sample (previous == null) seeds the average. */
    public static double ema(Double previous, double sample, double alpha) {
        return previous == null ? sample : previous + alpha * (sample - previous);
    }

    /**
     * Seconds to cover {@code distanceMeters} (straight-line, inflated by {@code roadFactor} to approximate
     * road distance) at {@code speedKmh}, which is floored at {@code minSpeedKmh} so a stuck vehicle
     * does not yield an infinite ETA.
     */
    public static int etaSeconds(double distanceMeters, double speedKmh, double minSpeedKmh, double roadFactor) {
        double v = Math.max(speedKmh, Math.max(minSpeedKmh, 0.1)) / 3.6; // m/s
        return (int) Math.ceil(Math.max(distanceMeters, 0) * Math.max(roadFactor, 1.0) / v);
    }
}
