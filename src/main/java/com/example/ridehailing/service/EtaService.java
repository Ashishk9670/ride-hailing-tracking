package com.example.ridehailing.service;

import com.example.ridehailing.config.AppProperties;
import com.example.ridehailing.redis.CellSpeedTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** ETA = PostGIS distance / recent average speed of the origin cell (default speed when unknown). */
@Service
public class EtaService {
    private static final Logger log = LoggerFactory.getLogger(EtaService.class);
    private final LocationHistoryRepository repo;
    private final CellSpeedTracker speeds;
    private final AppProperties.Eta cfg;

    public EtaService(LocationHistoryRepository repo, CellSpeedTracker speeds, AppProperties props) {
        this.repo = repo;
        this.speeds = speeds;
        this.cfg = props.eta();
    }

    public int estimateSeconds(double fromLat, double fromLon, double toLat, double toLon) {
        double meters;
        try {
            meters = repo.distanceMeters(fromLat, fromLon, toLat, toLon);
        } catch (RuntimeException e) {
            log.warn("PostGIS distance failed, falling back to haversine", e);
            meters = GeoUtils.haversineMeters(fromLat, fromLon, toLat, toLon);
        }
        double speed;
        try {
            speed = speeds.averageKmh(fromLat, fromLon).orElse(cfg.defaultSpeedKmh());
        } catch (RuntimeException e) {
            speed = cfg.defaultSpeedKmh();
        }
        return EtaModel.etaSeconds(meters, speed, cfg.minSpeedKmh(), cfg.roadFactor());
    }
}
