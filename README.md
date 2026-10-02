# Ride-Hailing / Delivery Tracking

Spring Boot 3.4 (Java 21, Maven) reference implementation of real-time driver matching and live trip tracking.

## Architecture

```
 driver app / simulator
        | POST /api/drivers/{id}/location        (REST)
        v
  Kafka topic  driver-locations  (key = driverId, 3 partitions)      <- location firehose
        |
        v   @KafkaListener  (LocationIngestService)
  +--------------------------+--------------------------------------------+
  | driver has no active trip| driver has an active trip (Redis registry)  |
  | GEOADD drivers:available | INSERT PostGIS location_history             |
  | (available-driver index) | + push to TripEventBroadcaster              |
  +--------------------------+--------------------------------------------+
                                        |
        +-------------------------------+-----------------------+
        v                                                       v
  GraphQL subscription  tripLocation/tripStatus       raw WebSocket /ws/trips/{tripId}
  (Spring GraphQL over WebSocket, /graphql)            (used by the Leaflet map UI)

 POST /api/rides -> TripService:  save Trip (PostGIS-backed table) -> GEOSEARCH nearest drivers
     -> claim driver atomically (ZREM) -> status DRIVER_ASSIGNED -> Redis pub/sub "dispatch-events"
 Redis pub/sub -> DispatchEventSubscriber -> broadcaster -> subscribers (works across app instances)
```

- **Redis**: `GEOADD`/`GEOSEARCH` (via Spring Data `opsForGeo`) for the available-driver index, `ZREM` as an atomic claim to avoid double-dispatch, `driver:trip:{id}` registry, pub/sub channel `dispatch-events`.
- **Kafka**: `driver-locations` topic, JSON values, keyed by driver for per-driver ordering.
- **PostgreSQL + PostGIS**: Flyway `V1__init.sql`. `trips` (JPA) has generated `geography` columns for pickup/dropoff; `location_history` stores `geography(Point,4326)` with GiST indexes, written and queried with native SQL (`ST_MakePoint`, `ST_MakeLine`, `ST_Length`).
- **Trip state machine**: `REQUESTED -> DRIVER_ASSIGNED -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED` (also `CANCELLED`, `NO_DRIVER_AVAILABLE`); illegal transitions return HTTP 409. Terminal states release the driver, who rejoins the geo index on the next location update.
- Not included: stale-driver expiry from the geo index, auth, retry/DLQ for Kafka.

## Run

```bash
docker compose up -d --wait      # Kafka (KRaft), Redis, postgis/postgis
mvn spring-boot:run              # http://localhost:8080
```

Open http://localhost:8080 for the live map (Leaflet): start the simulator, request a ride, watch the driver move, advance status.

### REST

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/rides` | request ride + match nearest driver |
| GET | `/api/trips/{id}` | trip |
| PATCH | `/api/trips/{id}/status` | `{ "status": "DRIVER_ARRIVED" }` |
| GET | `/api/trips/{id}/history` | PostGIS location history + route length |
| POST | `/api/drivers/{id}/location` | publish location to Kafka |
| GET | `/api/drivers/nearby?lat&lon&radiusKm` | GEOSEARCH |
| POST | `/api/simulator/start?count=10&lat&lon&intervalMs` / `/stop` / GET `/status` | synthetic drivers |

### GraphQL (`/graphql`, GraphiQL at `/graphiql`)

```graphql
subscription { tripLocation(tripId: "…") { driverId lat lon heading speedKmh timestamp } }
subscription { tripStatus(tripId: "…") { type status driverId } }
query { trip(id: "…") { status driverId } nearbyDrivers(lat: 37.77, lon: -122.41) { driverId distanceMeters } }
```

Raw WebSocket: `ws://localhost:8080/ws/trips/{tripId}` sends `{"kind":"location"|"event","data":{…}}`.

## Tests

| Layer | Command | Needs Docker |
|---|---|---|
| Unit (JUnit 5, Mockito, Reactor StepVerifier, MockMvc) | `mvn test` (`make unit`) | no |
| Integration (Testcontainers: PostGIS, Redis, Kafka; REST, geo matching, WebSocket + GraphQL subscription) | `mvn verify` (`make integration`) | yes (auto-skipped without Docker) |
| Playwright e2e UI/API against the running stack | `make up`, `make run`, then `make e2e-install && make e2e`; or `scripts/e2e-local.sh` | yes |

Coverage: Jacoco report at `target/site/jacoco/index.html` after `mvn verify`.
CI (`.github/workflows/ci.yml`): unit job, integration job (+Jacoco artifact), e2e job (compose up, Playwright install, report upload).
