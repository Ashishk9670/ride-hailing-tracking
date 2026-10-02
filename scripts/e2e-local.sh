#!/usr/bin/env bash
# Boots the full stack (compose + app), runs Playwright, tears everything down.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --wait
mvn -q -DskipTests package
java -jar target/ride-hailing-tracking-0.0.1-SNAPSHOT.jar > target/app.log 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true; docker compose down -v' EXIT
for i in $(seq 1 60); do
  curl -sf localhost:8080/actuator/health >/dev/null && break
  sleep 2
done
(cd e2e && npm install && npx playwright install chromium && npx playwright test)
