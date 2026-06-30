# AutoDispatch — Project State

## Current phase

**Phase 12 in progress. Deployment artifacts complete. 178 backend tests green. Manual deploy steps (Railway + Vercel + optional WhatsApp LIVE) remaining.**

Phase 12 added (code-complete, committed 2026-06-23):
- WireMock-based integration tests for `WhatsAppCloudApiGateway` (sendRideOffer payload + E.164 stripping). 178 backend tests green.
- Dockerfile (multi-stage jdk-alpine → jre-alpine), `.dockerignore`, `docker-compose.prod.yml`.
- `railway.toml` (Dockerfile builder, liveness healthcheck).
- `frontend/vercel.json` (Next.js framework hint).
- `application-prod.yml` (`POSTGRES_*` / `REDIS_*` env-var bindings; JSON logging on prod profile).
- `WebCorsConfig` (`AUTODISPATCH_CORS_ALLOWED_ORIGINS` env var).

**To deploy (manual steps — run in this order):**
1. Push to `main` → Railway auto-deploys (native GitHub integration at `hemanthballa07/autodispatch`).
2. Railway: Add Plugin → PostgreSQL, Add Plugin → Redis.
3. Railway backend service → Variables → add 13 vars (see plan `rustling-wishing-pinwheel.md`).
   - `POSTGRES_HOST` = `${{Postgres.PGHOST}}` (and PORT, DB, USER, PASSWORD similarly)
   - `REDIS_HOST` = `${{Redis.REDISHOST}}` (and PORT, PASSWORD)
   - `JWT_SECRET` / `ADMIN_API_KEY` / `WHATSAPP_VERIFY_TOKEN` — generate with `openssl rand -hex 32/16/16`
   - `WHATSAPP_MODE` = `STUB` initially; `AUTODISPATCH_CORS_ALLOWED_ORIGINS` = `http://localhost:3000` placeholder
4. Verify: `curl https://<railway-url>/actuator/health/liveness` → `{"status":"UP"}`
5. Vercel: New Project → Import `hemanthballa07/autodispatch` → **Root Directory: `frontend`** → add `NEXT_PUBLIC_API_BASE=https://<railway-url>` for Production.
6. Update Railway: `AUTODISPATCH_CORS_ALLOWED_ORIGINS=https://<vercel-app>.vercel.app`
7. (Optional) WhatsApp LIVE: set `WHATSAPP_MODE=LIVE` + `WHATSAPP_ACCESS_TOKEN` + `WHATSAPP_APP_SECRET` + `WHATSAPP_PHONE_NUMBER_ID` in Railway; register webhook at `https://<railway-url>/webhooks/whatsapp`.

**Phase 11 complete. UI integration + payment auto-flow. 176 backend tests green (178 after Phase 12 WireMock tests). 50 frontend tests green. ArchUnit green.**

Phase 11 added:
- `dispatch.api.RideCompletedEvent` record; `Ride.lockFinalAmount()`; `DispatchService.markCompleted()` now locks final amount and publishes event via `ApplicationEventPublisher`.
- `PaymentEventListener` (`@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`) auto-posts RIDE_FARE payment transaction and driver ledger credit on every ride completion.
- `RideView` gains `scheduledFor` (12th component); propagated through `RideBookingService.toView()` and `RideController.RideResponse` to the HTTP JSON response.
- Frontend `api.ts`: `RatingView`, `PaymentTransaction` types; 6 new API functions (`getRating`, `submitRating`, `triggerSos`, `reportIncident`, `getPayments`, `acknowledgePayment`); `createRide` extended with optional `scheduledFor`; `Ride` type gains `scheduledFor`.
- `RatingModal`: 5-star picker + optional comment; appears in `RideStatusView` when ride COMPLETED and no prior rating (404 probe). Thank-you confirmation on success.
- `SafetyControls`: SOS (confirm → "help is on the way") + incident report form; visible when ride ARRIVED or IN_PROGRESS.
- `RideReceipt`: fetches RIDE_FARE transaction on load; shows pending amount + "Mark as paid" (PENDING→COLLECTED) or "✓ Cash paid" (COLLECTED).
- `BookingFlow`: "Schedule for later" checkbox + datetime-local input (min now+10min); passes ISO scheduledFor to createRide.
- `RideProgressStepper`: SCHEDULED prepended as first step. `RideStatusView` gains SCHEDULED headline.
- 1 new backend test (176 total): `PaymentAutoPostTest` — full-stack `@SpringBootTest` + SQL fixtures.
- 10 new frontend tests (50 total): `RatingModal` (4), `SafetyControls` (4), `ScheduledBooking` (2).

Phase 10 / Phase 3 added:
- Flyway V11: `ride_ratings` table (id, ride_id FK, driver_id FK, rater_rider_id FK, driver_stars INTEGER CHECK 1-5, comment VARCHAR(500) NULL, created_at); UNIQUE(ride_id, rater_rider_id); indexes on ride_id + driver_id.
- Flyway V12: `safety_events` table (id, ride_id FK, rider_id FK, type CHECK ('SOS','INCIDENT_REPORT'), details VARCHAR(1000) NULL, created_at); indexes on ride_id + rider_id.
- `rating.api`: `RatingView` record, `DriverRatingStats` record, `RatingService` interface.
- `rating.internal`: `RideRating` entity (INTEGER driver_stars; package-private accessors), `RideRatingRepository` (custom `findByRideIdAndRaterRiderId`, `countByDriverId`, JPQL `findAverageStarsByDriverId`), `DefaultRatingService` (JdbcTemplate ride validation: COMPLETED + rider ownership + non-null driver; JPA for stats; `IllegalArgumentException` on all failures), `RatingController` (`POST /api/v1/rides/{rideId}/rating?stars=&comment=` → 201; `GET /` → 200 or 404), `RatingApiExceptionHandler` (IllegalArgumentException → 400; DataIntegrityViolationException → 409).
- `safety.api`: `SafetyEventView` record, `SafetyService` interface.
- `safety.internal`: `SafetyEvent` entity, `SafetyEventRepository` (ordered by createdAt desc), `DefaultSafetyService` (JdbcTemplate rider ownership validation; triggerSos + reportIncident + findByRide), `SafetyController` (`POST /api/v1/rides/{rideId}/safety/sos` + `/incident` → 201), `SafetyAdminController` (`GET /api/admin/rides/{rideId}/safety`; guarded by existing AdminAuthFilter), `SafetyApiExceptionHandler` (IllegalArgumentException → 400).
- 21 new tests (175 total, was 154): `RatingServiceTest` (8 service-layer @Transactional), `RatingApiTest` (5 MockMvc HTTP-level), `SafetyServiceTest` (4 service-layer @Transactional), `SafetyApiTest` (4 MockMvc HTTP-level).

Phase 10 / Phase 2 added:
- Flyway V9: `payment_transactions` table (id, ride_id FK, rider_id FK, type CHECK, method CHECK, status CHECK, amount, acknowledged_at, created_at, updated_at); UNIQUE(ride_id, type); indexes on ride_id and rider_id.
- Flyway V10: `driver_ledger` table (id, driver_id FK, ride_id FK nullable, type CHECK, amount, note, created_at); index on driver_id; partial index on ride_id WHERE NOT NULL.
- `payment.api`: `PaymentMethod` enum, `PaymentStatus` enum, `PaymentTransactionView` record, `LedgerEntryView` record, `PaymentService` interface, `DriverLedgerService` interface.
- `payment.internal`: `PaymentTransaction` entity (String fields for type/method/status, `markCollected()` mutator), `PaymentTransactionRepository`, `DriverLedgerEntry` entity, `DriverLedgerEntryRepository`, `DefaultPaymentService` (ownership check on acknowledge), `DefaultDriverLedgerService` (negate amount for CANCELLATION_PENALTY), `PaymentEventListener` (empty skeleton), `PaymentController` (`POST /api/v1/rides/{rideId}/payment/initiate`, `POST /acknowledge`, `GET /`; uses literal `"riderId"` attribute to avoid importing rider.internal), `LedgerAdminController` (`GET /api/admin/drivers/{driverId}/ledger`, page size capped at 100), `PaymentApiExceptionHandler` (IllegalArgumentException → 400, DataIntegrityViolationException → 409).
- `ModuleBoundaryTest.MODULES` extended with `"payment"`, `"rating"`, `"safety"`.
- 16 new tests: `PaymentServiceTest` (9, service-layer `@Transactional` — covers PaymentService + DriverLedgerService), `PaymentAcknowledgmentTest` (7, MockMvc HTTP-level — covers initiate/acknowledge/list endpoints, auth, cross-rider ownership, duplicate conflict, admin ledger endpoint).
- 154 backend tests green (was 138).

Phase 10 / Phase 1 added:
- Flyway V7: `vehicle_types` table; seed "Auto" row; `vehicle_type_id` FK on `drivers` + `fare_rules`; `fare_rules` PK changed to `(pickup_zone, drop_zone, vehicle_type_id)`.
- Flyway V8: 8 new nullable columns on `rides` (`vehicle_type_id`, `pickup_location_id`, `drop_location_id`, `scheduled_for`, `cancelled_by`, `arrived_at`, `started_at`, `final_amount`); `SCHEDULED` added to status CHECK; `idx_rides_scheduled`, `idx_rides_driver_completed` indexes; `uq_rides_one_active_per_rider` updated to include `'SCHEDULED'`.
- `SCHEDULED` status in `RideStatus` with `ALLOWED_TRANSITIONS[SCHEDULED] = {REQUESTED, CANCELLED}`.
- `Ride` entity: 8 new nullable fields, `recordCancelledBy(String)` method, 8-arg scheduled-ride constructor.
- `VehicleType` entity + `VehicleTypeRepository` in `driver.internal`.
- `VehicleTypeCatalog` interface + `VehicleTypeView` record in `driver.api`; `VehicleTypeController` at `GET /api/v1/vehicle-types`.
- `FareService.estimate()` extended to 3-arg overload accepting nullable `vehicleTypeId`; 2-arg becomes default delegate.
- `DefaultFareService` implements 3-arg estimate (null → original query; non-null → adds `AND vehicle_type_id = ?`).
- `CatalogController` fare-estimate endpoint gains `@RequestParam(required = false) UUID vehicleTypeId`.
- `RideBooking.requestRide()` extended to 8-arg overload; 4-arg is default delegate.
- `RideBookingService` implements 8-arg overload; `SCHEDULED` added to `ACTIVE_STATUSES`.
- `RideController`: `CreateRideRequest` gains `vehicleTypeId` + `scheduledFor`; calls 8-arg `requestRide`; `startDispatch` only if not scheduled; `CANCELLABLE` states include `SCHEDULED`.
- `ScheduledRideReleaseSweeper` in `dispatch.internal`: 30s fixedDelay; `DispatchService.releaseScheduledOne()` (affected-rows) + `startDispatch()` on winner; same structural pattern as `BroadcastTimeoutSweeper`.
- `RideTransitionMatrixTest` updated for 9×9 matrix (SCHEDULED row + column).
- New `ScheduledRideReleaseTest`: 2-thread concurrency gate — exactly one `releaseScheduledOne()` wins, ride is REQUESTED.
- 136 backend tests green (was 118).

**Frontend Redesign complete (Phase 9). Tailwind CSS v4 + shadcn/ui (neutral theme, `#106344` brand green) installed; 5 screens redesigned + 4 new features (receipt, profile, bottom nav, pagination). 40 frontend tests pass. Ready to deploy.**

Phase 9 added:
- Tailwind CSS v4 + shadcn/ui neutral theme; `postcss.config.mjs`, `components.json`, `lib/utils.ts`.
- shadcn components: button, card, input, select, badge, separator, skeleton.
- New components: `StatusBadge`, `RideProgressStepper`, `DriverCard`, `RideReceipt`, `BottomNav`.
- Redesigned: landing (hero + How It Works), BookingFlow (Card + localStorage), RideStatusView
  (stepper + receipt), history (card list + pagination), admin pages (Tailwind tables + Card).
- New: `app/profile/page.tsx`, `app/layout.tsx` adds BottomNav + pb-16.
- `lib/api.ts`: `listRides(page: number = 0)`.
- 40 frontend tests green.

Phase 8 added:
- `logback-spring.xml`: JSON logging on `prod` profile, colored console elsewhere.
- Actuator health probes (`/actuator/health/liveness`, `/actuator/health/readiness`)
  + Prometheus metrics (`/actuator/prometheus`).
- Micrometer tracing (`micrometer-tracing-bridge-otel`) — `traceId`/`spanId` in
  every log line automatically.
- `Dockerfile` (repo root, multi-stage jdk-alpine → jre-alpine), `.dockerignore`,
  `docker-compose.prod.yml` (local prod smoke-test), `railway.toml`,
  `frontend/vercel.json`.
- `application-prod.yml` with `POSTGRES_*`/`REDIS_*` env var bindings for Railway.
- 118 backend tests green; no schema changes.

**To deploy:**
1. Push to `main` → Railway auto-deploys (native GitHub integration).
2. Map Railway Postgres plugin vars → `POSTGRES_HOST/PORT/DB/USER/PASSWORD` in Railway dashboard.
3. Map Redis plugin vars → `REDIS_HOST/PORT`.
4. Set `JWT_SECRET`, `ADMIN_API_KEY`, `WHATSAPP_VERIFY_TOKEN`.
5. Deploy frontend to Vercel (root dir = `frontend`); set `NEXT_PUBLIC_API_BASE` (Production only).
6. Set `AUTODISPATCH_CORS_ALLOWED_ORIGINS=https://<vercel-app>.vercel.app` in Railway.
7. Activate WhatsApp LIVE: set `WHATSAPP_MODE=LIVE` + credentials in Railway.

**Admin Dashboard UI complete (Phase 7). Campus operators can log in via `/admin`, view live system stats, manage drivers (register/verify/suspend/unsuspend), and browse rides (filter by status/date, paginate). Auth guard uses sessionStorage; sign-out clears the key.**

Phase 7 added:
- `frontend/lib/admin-api.ts`: typed HTTP client for all admin endpoints; `adminRequest` places `X-Admin-Key` after header spread; 401 without `keyOverride` clears sessionStorage + redirects.
- `components/admin/StatsCard.tsx`: presentational stat tile.
- `components/admin/AdminLoginForm.tsx`: validates key via `getStats(keyOverride)` before writing sessionStorage; no router dependency.
- `app/admin/login/page.tsx`: unguarded login page (avoids redirect loop).
- `app/admin/(protected)/layout.tsx`: auth guard using `useState(false)` + `useEffect([router])` pattern; `router.replace` (not push) so protected URL is not in back-history; nav with sign-out.
- `app/admin/(protected)/page.tsx` → `AdminDashboard.tsx`: live stats (activeRides, completedToday, availableDrivers).
- `app/admin/(protected)/drivers/page.tsx` → `AdminDriversView.tsx`: driver table with per-row verify/suspend/unsuspend actions + inline error display; register form with 409/400 error handling.
- `app/admin/(protected)/rides/page.tsx` → `AdminRidesView.tsx`: rides table with status + date filters (page resets on filter change), pagination (Next disabled when < 20 rows), null-fare guard.
- 26 new Vitest component tests → **40 total tests green** (14 existing + 26 new).

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
