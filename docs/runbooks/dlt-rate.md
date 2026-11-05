# Runbook: LocationDeadLetterRateHigh / LocationDeadLetterSeen

**SLO:** < 1% of consumed location updates dead-lettered (10 m window). `LocationDeadLetterSeen` is an informational heads-up for any DLT record.

## What it means
Records that failed deserialization/validation or exhausted retries were published to the dead-letter topic (`location_dlt_total`).

## Triage
1. Consume the DLT topic and inspect the headers (exception message and stack trace):
   `kafka-console-consumer.sh --topic driver-locations.DLT --from-beginning --property print.headers=true --bootstrap-server ...`
2. Typical causes: a producer sending a payload that violates `docs/schemas/driver-location.schema.json` (bad coordinates, wrong types), a deploy that changed the DTO, or a transient Redis/Postgres outage that exhausted retries.
3. Find the offending clients: Loki `{service="app"} |= "Invalid location update"`.

## Mitigation
- Bad producers: fix the client; the contract schema in `docs/schemas/` is the source of truth.
- Transient outage: after recovery, replay the DLT records to the main topic if still useful (locations older than the driver TTL are not).
- Regression from our side: roll back the deploy; the contract tests should have caught it.
