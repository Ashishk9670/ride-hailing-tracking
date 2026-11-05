#!/usr/bin/env bash
# One-command demo: infra up, app running, 10 simulated drivers moving. Stop with `make demo-down`.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --wait
if curl -sf localhost:8080/actuator/health >/dev/null; then
  echo "app already running on :8080"
else
  mvn -q -DskipTests package
  mkdir -p target
  nohup java -jar target/ride-hailing-tracking-0.0.1-SNAPSHOT.jar > target/demo-app.log 2>&1 &
  echo $! > target/demo-app.pid
  for i in $(seq 1 60); do curl -sf localhost:8080/actuator/health >/dev/null && break; sleep 2; done
  curl -sf localhost:8080/actuator/health >/dev/null || { echo "app failed to start, see target/demo-app.log"; exit 1; }
fi
TOKEN=$(curl -sf -X POST localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d '{"subject":"demo-ops","roles":["ADMIN"]}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl -sf -X POST "localhost:8080/api/simulator/start?count=10" -H "Authorization: Bearer $TOKEN" >/dev/null
echo
echo "Demo is running:"
echo "  Live map : http://localhost:8080  (sign in as operator, request a ride)"
echo "  GraphiQL : http://localhost:8080/graphiql"
echo "  Stop     : make demo-down"
