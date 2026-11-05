# Runbook: LocationLagSLOBreach

**SLO:** p95 of `driver_location_lag_seconds` < 5 s (time from API receipt to processing). **Alert:** fires after 5 m above.

## What it means
Driver locations reach the Redis geo index / trip tracking late, so matching and live maps use stale positions.

## Triage
1. Compare `rate(driver_location_updates_total{stage="received"})` with `stage="processed"`. Received > processed means the consumer is falling behind.
2. Kafka consumer group `location-ingest`: `kafka-consumer-groups.sh --describe --group location-ingest` - look at LAG per partition.
3. Consumer errors/retries: Loki `{service="app"} |= "location"` and the DLT alert (`dlt-rate.md`). Retries with backoff delay a partition.
4. Downstream latency: Redis and Postgres (history inserts) slow-downs stall consumers; see `match-latency.md`.
5. Broker health: is Kafka reachable? Producers fail fast (503, or 202 with a later send failure) when it is not.

## Mitigation
- Add partitions/consumers (`ridehailing.topics.partitions`, `spring.kafka.listener.concurrency`) or app replicas.
- Fix the slow dependency; lag drains by itself once consumers catch up.
- Poison records are dead-lettered after retries and no longer block a partition.
