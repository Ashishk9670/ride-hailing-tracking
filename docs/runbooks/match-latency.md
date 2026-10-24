# Runbook: MatchLatencySLOBreach

**SLO:** p95 of `rides_match_latency_seconds` < 500 ms (5 m window). **Alert:** fires after 10 m above.

## What it means
`TripService.requestRide` (find nearest drivers in Redis GEO, claim, persist, ETA) is slow. Riders wait longer for an assignment.

## Triage
1. Grafana "Ride hailing" dashboard: match latency p50/p95/p99, HTTP rate by status. Is it all requests or only `result=no_driver`?
2. Redis: `redis-cli --latency`, `SLOWLOG GET 10`, size of `drivers:available` (`ZCARD`). A huge or stale geo set slows GEOSEARCH.
3. Postgres: connection pool saturation (`hikaricp_connections_pending`), slow `trips` inserts/updates.
4. Traces (Tempo): open a slow `POST /api/rides` trace, find the dominant span (Redis, JDBC, Kafka).
5. Logs (Loki): `{service="app"} |= "Dependency unavailable"` - timeouts to Redis show up here.

## Mitigation
- Redis slow or failing: restart/failover Redis; the app returns 503 with `Retry-After` while it is down and recovers automatically.
- Pool exhausted: scale the app out or raise `spring.datasource.hikari.maximum-pool-size`.
- Too many stale drivers: verify `StaleDriverSweeper` is running (`drivers_expired_total` increasing).
