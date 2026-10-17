# ADR 0002: ETA from PostGIS distance and recent per-cell speed

Status: accepted

## Context
Riders need an ETA on the ride response and during the trip. There is no routing engine, but PostGIS and a
stream of driver speeds are already available.

## Decision
- ETA = distance x road factor / speed (`EtaModel`). Distance is PostGIS `ST_Distance` on `geography` (`LocationHistoryRepository.distanceMeters`), with an in-process haversine fallback if the query fails.
- Speed is an exponential moving average of reported driver speeds per grid cell, kept in Redis with a TTL (`CellSpeedTracker`, default alpha 0.2, TTL 900 s). Cells with no fresh samples use a default speed; the speed is floored so ETAs stay finite.
- On ride request, `Trip.etaSeconds` is driver-to-pickup and is stored (`V3__trip_eta.sql`). During a trip, location updates emit a throttled `ETA_UPDATED` `TripEvent` (Redis `SET NX EX` per trip) toward the pickup (DRIVER_ASSIGNED) or dropoff (IN_PROGRESS). ETA failures never fail location ingest.

## Consequences
- Cheap and explainable, but straight-line distance ignores the road network and one-way streets; the road factor is a blunt correction.
- The moving average is best-effort (concurrent writers may drop a sample), which is fine for an estimate.
- Replacing the distance source with a routing service only touches `EtaService`.
