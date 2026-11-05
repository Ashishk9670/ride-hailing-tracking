# ADR 0003: Trip replay as a paced Server-Sent Events stream

Status: accepted

## Context
Operators want to replay a finished trip on the map at adjustable speed. The stored `location_history`
already has timestamps; the UI already draws live points.

## Decision
- `GET /api/trips/{id}/replay?speed=N` streams `start`, `point*` and `done` SSE events, paced by the recorded timestamp gaps divided by `speed` (clamped to 0.1..100). Gaps longer than 5 s are compressed. Unknown trips return 404.
- Pacing lives in a pure function (`ReplayService.replay`) so it is unit-tested without a server; emission runs on virtual threads and stops when the client disconnects.
- The speed is chosen per request; the Leaflet UI restarts the stream with the new speed. The UI reads the stream with `fetch` rather than `EventSource` so the normal `Authorization: Bearer` header works under the existing JWT rules.

## Consequences
- One-directional and simple; no seek/pause on the server (the client aborts and restarts instead).
- The history is loaded in full at the start of a replay, which is fine for trips of a few thousand points.
