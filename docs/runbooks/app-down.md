# Runbook: RideAppDown

**Alert:** Prometheus cannot scrape `/actuator/prometheus` for 2 m.

## Triage
1. Is the process up? `kubectl get pods -l app=ride-hailing-tracking` (or `docker compose ps`), then `kubectl logs` / Loki `{service="app"}`.
2. Readiness: `/actuator/health/readiness` shows which dependency (Postgres, Redis, Kafka) is failing.
3. Crash loops: OOMKilled (raise memory limit / tune `MaxRAMPercentage`), failed Flyway migration (first error in the logs).
4. Network: can Prometheus reach the target (Service/port, NetworkPolicy)?

## Mitigation
- Roll back the last deploy, or restart the pods once the dependency is healthy.
- The app degrades to 503 (it does not crash) when Redis or Kafka is unreachable, so a full outage usually means Postgres, config, or a bad release.
