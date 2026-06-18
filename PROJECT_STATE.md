# AutoDispatch — Project State

## Current phase

**Admin module complete. Campus operator can register, verify, suspend, and unsuspend drivers; query rides by status/date; and view system stats. All behind a static API key.**

Phase 6 added:
- `drivers.suspended` column (Flyway V6); verified + unsuspended guard in
  `listAvailableVerified()`.
- Driver admin API: register (upsert-safe, duplicate → 409), verify, suspend
  (rejects ON_RIDE, Redis SREM), unsuspend, listAll. All via
  `DriverAdminService` (driver.api) implemented in driver.internal; no
  `admin.internal` types leak into other modules.
- Admin ride queries: list (status + date filters, paginated), single-ride
  detail, stats (active / completed-today / available-drivers). JdbcTemplate
  LEFT JOIN query, `RideRepository` count methods.
- Admin auth: `AdminAuthFilter` with constant-time API key check (`X-Admin-Key`
  header); blank-key fast-fail on startup; ADMIN_API_KEY env var.
- HTTP layer: `DriverAdminController`, `RideAdminController`, `StatsController`
  at `/api/admin/v1/**`; `AdminApiExceptionHandler` (RFC-7807).
- 14 new admin integration tests → 118 backend tests green.

**Rider flow complete end-to-end in STUB mode.**

Phase 5 added:
- `locations` (12 seeded placeholder stops, 4 zones) and `fare_rules`
  (symmetric placeholder matrix) behind the fare module
  (`FareService.estimate`, `LocationCatalog`). Rides store the quoted fare at
  request time; the quote is immutable per ride.
- One-active-ride-per-rider guaranteed server-side by a partial unique index
  on rides (double-tap safe).
- Rider HTTP API (`/api/v1`): sessions (phone→E.164, upsert, HS256 JWT 30d,
  secret from JWT_SECRET), locations, fare estimate (422 without a rule),
  ride create (rate-limited 3/10min via Redis; invokes dispatch via its
  public API), ride status view (driver name/vehicle/masked phone — raw
  wa_id never exposed), cancel (RFC-7807 409 when the state machine says
  no), history (newest first, page 20). AuthZ: foreign rides are 404.
- PWA: first-run session capture, booking page (location selects, live fare
  estimate, double-tap-safe confirm), ride status screen polling every 4s
  with full state progression and friendly EXPIRED/CANCELLED screens, ride
  history. No maps, no geolocation. Component tests via vitest.

Phase 4 added (adapter only — zero business rules in the notification module):
- `whatsapp.*` config from environment via @ConfigurationProperties
  (`.env.example` documents names; secrets never hardcoded/logged).
  Feature flag `whatsapp.mode=STUB|LIVE`, default STUB (recording gateway, no
  external calls). LIVE wires `WhatsAppCloudApiGateway` (HTTP/1.1, retry once,
  failures logged and dropped — never block/roll back domain transitions).
- Webhook `GET/POST /webhooks/whatsapp`: Meta handshake; HMAC-SHA256
  signature verification (constant-time) → 403 on invalid; idempotency via
  `processed_webhook_messages` (duplicate delivery acknowledged, never
  reprocessed); processing handed off post-parse via executor.
- Anti-corruption layer: sealed `DriverCommand` (GoOnline/GoOffline/
  AcceptRide/MarkArrived/StartTrip/CompleteTrip/CancelRide/Unrecognized) from
  interactive button replies ("ACCEPT:rideId") and text fallback (ON/OFF/
  R-codes/ARRIVED/START/DONE/CANCEL resolved via dispatch public API).
  Unknown senders get a canned reply; unrecognized commands get state-aware
  help. `PhoneNumbers.toE164` (common) used everywhere.
- `MessageCatalog` centralizes all user-facing texts. 24h session rule:
  offers only to drivers with `last_inbound_at` within 24h (template fallback
  is a config-flag stub, not implemented).

Phase 3 added (locked design):
- `DriverAvailabilityService` (driver.api): goOnline/goOffline (ON_RIDE
  rejected with `DriverOnRideException`), listAvailableVerified with Redis SET
  `drivers:available` mirroring the DB (DB writes first; DB wins and Redis is
  repaired on disagreement).
- `DispatchApi`/`DispatchService` (dispatch): startDispatch (REQUESTED only;
  zero drivers → EXPIRED via the legal BROADCASTING→EXPIRED two-step),
  handleDriverAccept (claimRide → markOnRide → compensation revert via
  conditional update on failure), handleDriverCancel (one guarded rebroadcast,
  then EXPIRED via conditional update), markArrived/Started/Completed
  (COMPLETED returns driver to AVAILABLE).
- Timeout sweeper (`@Scheduled` 10s, off in tests): rounds 1–3 of 45s; round
  advance and final expiry are claimed via conditional updates on
  current_round_expires_at (multi-instance safe, no Redis locks). Offers are
  reissued per round (unique(ride_id, driver_id) respected); unanswered offers
  IGNORED on expiry; rider notified.
- All outbound messages still go through `WhatsAppGateway`; the stub now
  records messages in memory (`RecordingWhatsAppGateway`) — still no external
  calls.

Phase 2 added (locked design — do not redesign):
- Tables `drivers`, `riders`, `rides`, `ride_offers` (Flyway `V2__domain_tables.sql`)
  with CHECK-constrained status enums, FKs, unique(ride_id, driver_id), and the
  specified indexes.
- JPA entities + Spring Data repositories per module (`driver`, `rider`,
  `dispatch`). Cross-module references are plain UUID columns, never entity
  associations.
- `RideStatus` state machine with explicit allowed-transitions map;
  `Ride.transitionTo` is the only way to change status
  (`IllegalRideTransitionException` on illegal edges; ASSIGNED→BROADCASTING
  driver-cancel guarded to max 1 via `rebroadcast_count`). ArchUnit enforces
  `setStatus` stays private.
- Atomic conditional updates: `RideRepository.claimRide` (BROADCASTING→ASSIGNED)
  and `DriverRepository.markOnRide` (AVAILABLE→ON_RIDE), both returning affected
  row counts; concurrency proven by 10-thread race tests on Testcontainers
  Postgres (exactly one winner).

## What exists

- `backend/` — Spring Boot 4.x modular monolith (Java 21, Gradle).
  - Empty bounded-context modules: `rider`, `driver`, `dispatch`, `fare`,
    `notification`, `admin`, `common`. Each exposes only a public `api`
    package; `internal` packages are module-private (enforced by ArchUnit:
    `backend/src/test/java/com/autodispatch/architecture/ModuleBoundaryTest.java`).
  - Flyway baseline `V1__baseline.sql` (no business tables).
  - Postgres (Spring Data JPA) + Redis wired; `StringRedisTemplate` bean
    reserved for later availability/locking.
  - `GET /health` → `{app, db, redis}` status.
  - `WhatsAppGateway` interface (notification module) + no-op stub. No external
    API is called anywhere.
- `frontend/` — Next.js PWA skeleton (manifest, service-worker offline shell,
  installable). Placeholder `/book` page, no form logic.
- `docker-compose.yml` — local Postgres 16 + Redis 7.
- `.github/workflows/ci.yml` — build + unit tests + ArchUnit on push/PR. No deploy.

## Explicitly NOT built (undesigned — do not start without approval)

- Dispatch/matching engine, ride state machine, fare calculation
- WhatsApp Cloud API integration (only the gateway interface exists)
- Any business entities/tables
- Rider booking flow logic

## Open decisions

- Domain model & ride lifecycle (design phase pending)
- WhatsApp conversation design for drivers
- Fare policy

## How to run locally

```bash
docker compose up -d                 # Postgres + Redis
cd backend && ./gradlew bootRun      # app on :8080, check GET /health
cd frontend && npm run dev           # PWA on :3000 (use Node 22)
```
