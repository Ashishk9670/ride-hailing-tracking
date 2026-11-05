# ADR 0001: Surge pricing from per-cell demand and Redis geo supply

Status: accepted

## Context
Fares must react to local imbalance between ride requests and available drivers, at request time, without
a new datastore and without per-request scans of the whole driver set.

## Decision
- The map is a fixed lat/lon grid (0.01 degree cells, about 1.1 km), cell id `latIndex:lonIndex` (`SurgePricing`).
- Demand = requests in the pickup cell during a sliding window: Redis `INCR surge:demand:{cell}` with the TTL refreshed on each request (default 300 s).
- Supply = available drivers inside the cell, a `GEOSEARCH BYBOX` on the existing driver geo index (`DriverGeoIndex.countInCell`).
- Multiplier = 1 while demand <= supply, otherwise `1 + (demand/max(supply,1) - 1) * sensitivity`, capped (default 0.5 and 3.0). The multiplier and fare estimate are stored on the trip (`V2__surge_pricing.sql`) so the quote is stable for the trip's life.

## Consequences
- O(1) counter plus one bounded geo query per request; no extra infrastructure.
- Grid cells have hard edges (no neighbour smoothing) and demand counts requests, not unmet requests; both are acceptable for a first model and isolated in the pure `SurgePricing` class, which is property-tested.
- A true H3 index could replace `cellId` later without touching callers.
