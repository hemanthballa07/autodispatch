# Changelog

All notable changes to AutoDispatch are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Fixed — 2026-07-01 — Test suite no longer depends on docker-compose

- **`PaymentAutoPostTest`**: was the only integration test using plain `@SpringBootTest` without
  `@Import(TestcontainersConfiguration.class)`, so it connected to the docker-compose Postgres at
  `localhost:5432` and failed whenever the compose stack wasn't running. Added the Testcontainers import;
  kept it non-`@Transactional` (its assertions depend on the `PaymentEventListener`'s
  `@TransactionalEventListener(AFTER_COMMIT)` actually committing) and its `@Sql` setup/teardown scripts.
- **`DefaultDriverAvailabilityServiceTest`** (12 tests, new): normal-path coverage of `goOnline` / `goOffline`
  / `recordInbound` / `tryMarkOnRide` / `makeAvailable`, and `drivers:available` Redis-set membership
  semantics (DB-wins repair of stale/missing mirror entries, verified/suspended exclusion). Deliberately not
  `@Transactional` — several `DriverRepository` native `@Modifying` updates lack `clearAutomatically`, so a
  shared test transaction reads stale JPA-cached entities after those writes (same reason
  `AdminApiIntegrationTest` and `DriverMarkOnRideConcurrencyTest` avoid it). The `markOnRide` concurrency race
  itself stays covered by `DriverMarkOnRideConcurrencyTest`.
- `DefaultDriverAdminService` (register/verify/suspend/unsuspend, `DriverAlreadyExistsException`,
  `DriverOnRideException`) was found already fully exercised by `AdminApiIntegrationTest` — no duplicate
  tests added.
- **207 backend tests green** (was 195), verified with `docker compose down` — `./gradlew test` is now fully
  self-sufficient via Testcontainers.

### Added — 2026-07-01 — Fare + vehicle-type test coverage

- **`FareServiceTest`** (13 tests, `@SpringBootTest` + Testcontainers + `@Transactional`): first dedicated
  coverage of the fare module. Exercises every `DefaultFareService.estimate()` branch — active zone-pair
  quote, unknown pickup/drop, inactive pickup/drop, missing fare rule for a zone pair, matching vs. unknown
  `vehicleTypeId`, and the 2-arg overload — plus `LocationCatalog.activeLocations()` (inactive exclusion +
  name ordering) and `findById()` (present / unknown / returns inactive).
- **`VehicleTypeControllerTest`** (4 tests, MockMvc): `GET /api/v1/vehicle-types` requires a session token
  (401 without), returns the seeded "Auto" type with id+name, excludes inactive types, and orders by name asc.
- **195 backend tests green** (was 178).
- Notification webhook was already covered (`WhatsAppWebhookSecurityTest`, `WhatsAppWebhookIdempotencyTest`),
  so no duplicate tests were added there.

### Added — 2026-06-30 — Deployment artifacts + WhatsApp LIVE gateway (Phase 12)

- **WireMock tests** (`WhatsAppCloudApiGatewayTest`): covers `sendRideOffer` serialised payload shape and E.164 phone-number stripping; **178 backend tests green**.
- **Dockerfile** (multi-stage `jdk-alpine` build → `jre-alpine` runtime); `.dockerignore`; `docker-compose.prod.yml` for local prod smoke-test.
- **`railway.toml`**: Dockerfile builder, healthcheck path `/actuator/health/liveness`.
- **`frontend/vercel.json`**: Next.js framework hint.
- **`application-prod.yml`**: `POSTGRES_*` / `REDIS_*` env-var bindings for Railway; JSON logging on `prod` profile.
- **`WebCorsConfig`**: `AUTODISPATCH_CORS_ALLOWED_ORIGINS` env var wires Vercel origin into CORS allowlist.
- **GitHub remote**: `https://github.com/hemanthballa07/autodispatch.git`; Railway + Vercel both pull from this repo.
- **Manual deploy steps** (Tasks 2–5): Railway Postgres + Redis plugins, 13 env vars, Vercel project (root dir `frontend`), CORS wiring, optional WhatsApp LIVE activation — see `PROJECT_STATE.md` for the runbook.

### Added — 2026-06-23 — UI integration + payment auto-flow (Phase 11)

- **`dispatch.api.RideCompletedEvent`**: new Spring application event published after `DispatchService.markCompleted()` commits.
- **`Ride.lockFinalAmount()`**: snapshots `fareAmount` → `finalAmount` on completion.
- **`PaymentEventListener`**: `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` transaction — auto-initiates `RIDE_FARE` payment transaction and driver ledger credit on every ride completion.
- **`RideView`** gains `scheduledFor` (12th component); propagated through `RideBookingService.toView()` and `RideController.RideResponse` to the HTTP layer.
- **Frontend `api.ts`**: `RatingView`, `PaymentTransaction` types; `getRating`, `submitRating`, `triggerSos`, `reportIncident`, `getPayments`, `acknowledgePayment` functions; `createRide` extended with optional `scheduledFor`; `Ride` type gains `scheduledFor`.
- **`RatingModal`**: star picker + optional comment; appears in `RideStatusView` when COMPLETED and no prior rating (404 probe). "Thank you for your rating!" on success.
- **`SafetyControls`**: SOS confirm flow + incident report form; visible in `RideStatusView` when ARRIVED or IN_PROGRESS.
- **`RideReceipt`**: fetches RIDE_FARE transaction; shows "Mark as paid" button (PENDING → COLLECTED) or "✓ Cash paid".
- **`BookingFlow`**: "Schedule for later" checkbox + datetime-local input; passes ISO `scheduledFor` to `createRide`.
- **`RideProgressStepper`**: SCHEDULED prepended as first step. `RideStatusView` gains SCHEDULED headline.
- **1 new backend test** (176 total): `PaymentAutoPostTest` — verifies auto-post of payment + ledger after `markCompleted()`.
- **10 new frontend tests** (50 total): `RatingModal` (4), `SafetyControls` (4), `ScheduledBooking` (2).

### Added — 2026-06-21 — Rating + safety modules (Phase 10 / Phase 3)

- **Flyway V11** (`V11__ride_ratings.sql`): `ride_ratings` table with ride_id/driver_id/rater_rider_id FKs,
  INTEGER driver_stars CHECK 1-5, comment VARCHAR(500) nullable, UNIQUE(ride_id, rater_rider_id),
  indexes on ride_id + driver_id.
- **Flyway V12** (`V12__safety_events.sql`): `safety_events` table with ride_id/rider_id FKs,
  type CHECK ('SOS','INCIDENT_REPORT'), details VARCHAR(1000) nullable, indexes on ride_id + rider_id.
- **`rating.api`**: `RatingView` record, `DriverRatingStats` record, `RatingService` interface.
- **`rating.internal`**: `RideRating` entity (INTEGER driver_stars, package-private accessors),
  `RideRatingRepository` (findByRideIdAndRaterRiderId; JPQL countByDriverId + findAverageStarsByDriverId),
  `DefaultRatingService` (JdbcTemplate for ride validation — COMPLETED status + rider ownership + non-null driver;
  JPA repository for stats to respect JPA flush ordering; stars range check),
  `RatingController` (`POST /api/v1/rides/{rideId}/rating?stars=&comment=` → 201; `GET /` → 200 or 404),
  `RatingApiExceptionHandler` (IllegalArgumentException → 400; DataIntegrityViolationException → 409).
- **`safety.api`**: `SafetyEventView` record, `SafetyService` interface.
- **`safety.internal`**: `SafetyEvent` entity, `SafetyEventRepository` (findByRideIdOrderByCreatedAtDesc),
  `DefaultSafetyService` (JdbcTemplate rider ownership validation; triggerSos + reportIncident + findByRide),
  `SafetyController` (`POST /api/v1/rides/{rideId}/safety/sos` + `/incident` → 201),
  `SafetyAdminController` (`GET /api/admin/rides/{rideId}/safety`; guarded by existing AdminAuthFilter),
  `SafetyApiExceptionHandler` (IllegalArgumentException → 400).
- **21 new tests** (175 total, was 154): `RatingServiceTest` (8 service-layer @Transactional),
  `RatingApiTest` (5 MockMvc HTTP-level), `SafetyServiceTest` (4 service-layer @Transactional),
  `SafetyApiTest` (4 MockMvc HTTP-level).

### Added — 2026-06-21 — Payment + ledger module (Phase 10 / Phase 2)

- **Flyway V9** (`V9__payment_transactions.sql`): `payment_transactions` table with
  ride_id/rider_id FKs, type/method/status CHECK constraints, UNIQUE(ride_id, type),
  and indexes on ride_id + rider_id.
- **Flyway V10** (`V10__driver_ledger.sql`): `driver_ledger` table with driver_id FK,
  nullable ride_id FK, type CHECK, and partial index on ride_id WHERE NOT NULL.
- **`payment.api`**: `PaymentMethod` enum, `PaymentStatus` enum,
  `PaymentTransactionView` record, `LedgerEntryView` record, `PaymentService` interface,
  `DriverLedgerService` interface.
- **`payment.internal`**: `PaymentTransaction` entity (String fields to avoid JPA enum
  mapping issues; `markCollected()` mutator), `PaymentTransactionRepository` (custom
  `findByRideIdAndType`), `DriverLedgerEntry` entity, `DriverLedgerEntryRepository`
  (paginated by driver), `DefaultPaymentService` (riderId ownership check on
  acknowledge; `IllegalArgumentException` on mismatch or missing tx),
  `DefaultDriverLedgerService` (amount negated for CANCELLATION_PENALTY),
  `PaymentEventListener` (empty @Component for future ride-completion events),
  `PaymentController` (`POST /api/v1/rides/{rideId}/payment/initiate?amount=`,
  `POST /acknowledge`, `GET`; uses literal `"riderId"` attribute to avoid
  importing rider.internal), `LedgerAdminController`
  (`GET /api/admin/drivers/{driverId}/ledger`, page size capped at 100),
  `PaymentApiExceptionHandler` (`IllegalArgumentException` → 400,
  `DataIntegrityViolationException` → 409).
- **`ModuleBoundaryTest`**: MODULES array extended with `"payment"`, `"rating"`, `"safety"`.
- **16 new tests** (154 total, was 138): `PaymentServiceTest` (9 service-layer tests
  with `@Transactional` rollback — covers PaymentService and DriverLedgerService),
  `PaymentAcknowledgmentTest` (7 MockMvc HTTP-level tests covering initiate/acknowledge
  happy path, auth, cross-rider ownership, duplicate 409, list endpoint, and admin
  ledger endpoint).

### Added — 2026-06-21 — Vehicle types + ride enrichment (Phase 10 / Phase 1)

- **Flyway V7** (`V7__vehicle_types.sql`): `vehicle_types` table; seed "Auto" row;
  `vehicle_type_id` FK column on `drivers` + `fare_rules`; `fare_rules` PK replaced
  with `(pickup_zone, drop_zone, vehicle_type_id)` (irreversible, no external FKs
  existed on `fare_rules`).
- **Flyway V8** (`V8__rides_new_columns.sql`): 8 new nullable columns on `rides`
  (`vehicle_type_id`, `pickup_location_id`, `drop_location_id`, `scheduled_for`,
  `cancelled_by`, `arrived_at`, `started_at`, `final_amount`); `SCHEDULED` added
  to status CHECK; `idx_rides_scheduled`, `idx_rides_driver_completed` indexes;
  `uq_rides_one_active_per_rider` partial-index updated to include `'SCHEDULED'`.
- **`RideStatus.SCHEDULED`** with `ALLOWED_TRANSITIONS[SCHEDULED] = {REQUESTED, CANCELLED}`.
  `Ride` entity gains 8 new nullable fields, `recordCancelledBy(String)`, and an
  8-arg scheduled-ride constructor (sets status SCHEDULED when `scheduledFor` non-null).
- **Vehicle type module** (`driver`): `VehicleType` JPA entity + `VehicleTypeRepository`
  in `driver.internal`; `VehicleTypeCatalog` interface + `VehicleTypeView` record in
  `driver.api`; `VehicleTypeController` at `GET /api/v1/vehicle-types`.
- **`FareService.estimate()`** extended to 3-arg overload with nullable `vehicleTypeId`;
  `DefaultFareService` adds `AND vehicle_type_id = ?` only when non-null (null keeps
  original zone-pair query; backward-compatible). `CatalogController` fare-estimate
  endpoint gains `@RequestParam(required = false) UUID vehicleTypeId`.
- **`RideBooking.requestRide()`** extended to 8-arg overload (vehicleTypeId,
  pickupLocationId, dropLocationId, scheduledFor, notes); 4-arg is default delegate.
  `RideBookingService` implements 8-arg; `SCHEDULED` added to `ACTIVE_STATUSES`.
  `RideController` `CreateRideRequest` gains `vehicleTypeId` + `scheduledFor`;
  `startDispatch` skipped for scheduled rides; `CANCELLABLE` states include `SCHEDULED`.
- **`ScheduledRideReleaseSweeper`** in `dispatch.internal`: `fixedDelay=30_000`;
  queries `findScheduledReadyForRelease()`, claims each via `releaseScheduledOne()`
  (affected-rows, multi-instance safe), calls `startDispatch()` on winner only;
  same guard + error-log pattern as `BroadcastTimeoutSweeper`.
- **Tests**: `RideTransitionMatrixTest` updated for 9×9 matrix (SCHEDULED transitions);
  new `ScheduledRideReleaseTest` proves 2-thread concurrent release has exactly one winner.
  **136 backend tests green** (was 118).

### Added — 2026-06-21 — Frontend Redesign (Phase 9)

- **Tailwind CSS v4 + shadcn/ui**: `tailwindcss`, `@tailwindcss/postcss`, shadcn
  component library (neutral theme, `#106344` brand green); `postcss.config.mjs`;
  `components.json`; `globals.css` rewritten with `@import "tailwindcss"`,
  `@theme inline` variable mapping, OKLCH neutral palette, brand tokens.
- **shadcn components**: button, card, input, select, badge, separator, skeleton.
- **New shared components**: `StatusBadge`, `RideProgressStepper`, `DriverCard`,
  `RideReceipt`, `BottomNav` (hides on `/admin/*`).
- **Redesigned screens**: landing page (hero + How It Works), BookingFlow (Card +
  Input + styled native select + localStorage name/phone), RideStatusView (stepper
  + receipt transition + red cancel), history (card list + pagination + StatusBadge),
  all admin pages (Card tables, brand nav hover).
- **New**: `app/profile/page.tsx` (`'use client'`, localStorage read, sign-out).
- **`app/layout.tsx`**: `<BottomNav />` + `<main className="pb-16">`.
- **`lib/api.ts`**: `listRides(page: number = 0)` adds `&page=` query param.
- All 40 frontend tests pass unchanged.

### Added — 2026-06-20 — Production Hardening + Deployment (Phase 8)

- `backend/build.gradle`: added `logstash-logback-encoder:8.0`,
  `micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`.
- `backend/src/main/resources/logback-spring.xml`: JSON encoder
  (`LogstashEncoder`) for `prod` profile; colored console for `!prod`.
- `backend/src/main/resources/application.yml`: extended management section —
  exposes `prometheus`, enables Actuator health probes
  (`/actuator/health/liveness`, `/actuator/health/readiness`), `show-details:
  always`. Added `logging.pattern.correlation` for MDC trace/span IDs in dev
  console output.
- `backend/src/main/resources/application-prod.yml`: production Spring profile
  — `POSTGRES_*` / `REDIS_*` env vars (no defaults, Railway must supply them),
  `logging.level root=INFO`.
- `Dockerfile` (repo root): multi-stage build — `eclipse-temurin:21-jdk-alpine`
  build stage with Gradle dependency-cache layer, `eclipse-temurin:21-jre-alpine`
  runtime stage; `HEALTHCHECK --start-period=45s`; `-Dspring.profiles.active=prod`
  in ENTRYPOINT. Placed at repo root so Railway's build context resolves
  `backend/` paths correctly.
- `.dockerignore` (repo root): excludes `backend/.gradle/`, `backend/build/`,
  `frontend/`, `.env*`.
- `docker-compose.prod.yml`: full-stack local smoke-test compose — api, db
  (postgres:16-alpine), redis (redis:7-alpine) with healthcheck gates; all
  secrets read from `.env` at runtime; WhatsApp vars default to empty (STUB).
- `railway.toml`: `builder = "DOCKERFILE"`, `dockerfilePath = "Dockerfile"`,
  `healthcheckPath = "/actuator/health/liveness"`, `healthcheckTimeout = 30`.
  Railway's native GitHub integration auto-deploys on push to `main`.
- `frontend/vercel.json`: framework hint only (`{"framework":"nextjs"}`); API
  URL (`NEXT_PUBLIC_API_BASE`) set per-environment in Vercel dashboard.
- 118 backend tests remain green; no schema changes.

### Added — 2026-06-19 — Admin Dashboard UI (Phase 7)

- `frontend/lib/admin-api.ts`: typed HTTP client wrapping all `/api/admin/v1/**`
  endpoints (`getStats`, `listDrivers`, `registerDriver`, `verifyDriver`,
  `suspendDriver`, `unsuspendDriver`, `listRides`). `adminRequest` injects
  `X-Admin-Key` after the header spread; 401 without `keyOverride` clears
  `sessionStorage` and redirects to `/admin/login`.
- `app/admin/login/page.tsx` (unguarded) + `AdminLoginForm` component: verifies
  key via `getStats(keyOverride)` before committing to sessionStorage; 401 →
  "Invalid key"; no sessionStorage write on failure.
- `app/admin/(protected)/layout.tsx`: auth guard using `useState(false)` +
  `useEffect([router])` (no SSR sessionStorage read, no hydration mismatch).
  `router.replace` ensures the protected URL is not in back-history post sign-out.
  Navigation: Dashboard / Drivers / Rides / Sign out.
- `AdminDashboard`: fetches `getStats()` on mount; renders 3× `StatsCard`
  (Active Rides, Completed Today, Available Drivers).
- `AdminDriversView`: driver table (Name, Vehicle, State, Verified, Suspended,
  Actions); per-row Verify / Suspend / Unsuspend buttons with inline error on
  422; register form with 409 → "WhatsApp ID already registered" / 400 → detail.
- `AdminRidesView`: rides table with status dropdown + date input filters;
  page resets to 0 on any filter change; pagination Next disabled at < 20 rows;
  `fareAmount` null → renders "—".
- 26 new Vitest component tests (LoginForm 5, Layout 3, Dashboard 2, Drivers 6,
  Rides 9) → **40 frontend tests green** (14 existing + 26 new).

### Added — 2026-06-18 — Admin module (Phase 6)

- Flyway `V6__admin_module.sql`: `drivers.suspended` column (default FALSE);
  `idx_rides_requested_at` for admin date-range filtering.
- Driver admin API (`driver.api`): `DriverAdminService` interface,
  `DriverAlreadyExistsException`; `DriverSummary` record gains `suspended` +
  `createdAt` fields. `DefaultDriverAdminService` in `driver.internal` handles
  register (duplicate → 409), verify, suspend (ON_RIDE → 422 + Redis SREM),
  unsuspend, listAll. Suspended drivers filtered from `listAvailableVerified`.
- Admin ride queries (`dispatch.api`): `AdminRideView`, `RideStats`,
  `AdminRideQueries` interface. `AdminRideQueryService` in `dispatch.internal`
  implements list/filter/detail via `JdbcTemplate` LEFT JOIN + count methods
  on `RideRepository`.
- Admin auth: `AdminProperties` (`autodispatch.admin.api-key`), `AdminConfig`
  with blank-key fast-fail, `AdminAuthFilter` (`OncePerRequestFilter`,
  `MessageDigest.isEqual` constant-time compare on `X-Admin-Key` header).
- Admin HTTP layer (`admin.internal`): `DriverAdminController` (POST/GET/verify/
  suspend/unsuspend), `RideAdminController` (list + detail with status/date
  filters), `StatsController` (active rides + completed today + available drivers).
  `AdminApiExceptionHandler` maps all domain exceptions to RFC-7807 responses.
  `AdminNotFoundException` kept package-private so `driver.internal` never
  references `admin.internal` types (ArchUnit rule preserved).
- Config: `ADMIN_API_KEY` added to `.env.example`; `X-Admin-Key` added to
  `WebCorsConfig` allowed headers.
- Tests: 14 admin integration gates (auth guard, registration/409, verify,
  suspend/422/unsuspend, ride listing/filtering, stats) → 118 tests green.

### Added — 2026-06-11 — Rider API + PWA booking flow (Phase 5)

- Flyway `V5__rider_flow.sql`: `locations` + `fare_rules` with placeholder
  seeds; partial unique index enforcing one active ride per rider.
- Fare module: `FareService.estimate(pickupId, dropId)` + `LocationCatalog`
  over zone-pair rules; rides capture the fare quote immutably at booking.
- Dispatch `RideBooking` API (request/find/history/cancel-by-rider with
  driver release + notification on assigned-ride cancel).
- Rider API: JWT sessions (HS256, env secret, 30-day), Bearer auth filter,
  locations, fare estimate, ride create/status/cancel/history, Redis rate
  limit (3 creations / 10 min), RFC-7807 errors, masked driver phone.
- PWA booking flow: session capture, pickup/drop selects with live fare
  estimate, double-tap-safe confirm, 4s status polling with full state
  progression, cancel in cancellable states, history page.
- Tests: 6 API integration gates (happy path, active-ride 409, authz 404,
  quote immutability, 429 rate limit, cancel matrix) → 104 backend tests
  green; 14 vitest component tests; CI runs frontend tests + build.

### Added — 2026-06-11 — WhatsApp adapter (Phase 4)

- Flyway `V4__whatsapp_adapter.sql`: `processed_webhook_messages` idempotency
  table; `drivers.last_inbound_at` for the 24h session rule.
- Webhook endpoint with Meta handshake, constant-time HMAC-SHA256 signature
  verification (403 on tamper/missing), atomic idempotency claims, async
  hand-off after parse.
- Sealed `DriverCommand` anti-corruption parser (buttons + text fallback with
  R-code resolution through `DispatchQueries`); state-aware help texts;
  polite unknown-sender reply.
- `whatsapp.mode=STUB|LIVE` flag (STUB default keeps the recording no-op
  gateway); LIVE `WhatsAppCloudApiGateway` with interactive Accept-button
  offers, retry-once-then-log sends that never propagate failures.
- `MessageCatalog` (single message text catalog), `PhoneNumbers`/`RideCodes`
  in the common module; `.env.example` with placeholder names only.
- Tests: signature/handshake, replay idempotency proven at the dispatch-call
  level, 9 Meta-payload fixture parser cases, STUB e2e driver journey
  (ON→offer→accept→arrived→start→done), WireMock failure injection in LIVE
  mode. 98 tests green.

### Added — 2026-06-11 — Dispatch engine (Phase 3)

- Flyway `V3__dispatch_engine_columns.sql`: rides.broadcast_round,
  rides.current_round_expires_at, ride_offers.round (additive only).
- Driver availability service with Redis SET `drivers:available` mirror
  (DB-first writes, repair-on-read, ON_RIDE goOffline rejection).
- Dispatch orchestration: broadcast rounds (45s × 3), atomic accept with
  markOnRide compensation, driver-cancel rebroadcast (guarded, then expiry),
  completion path returning drivers to AVAILABLE.
- Multi-instance-safe timeout sweeper using conditional updates on
  current_round_expires_at (affected-rows claims, no Redis locks).
- Recording WhatsApp stub (in-memory, no external calls) for test assertions.
- 6 integration gates (Testcontainers Postgres + Redis) incl. 5-way accept
  race and accept-vs-sweeper race; ArchUnit rule confining WhatsApp types to
  the notification module. 78 tests green.

### Added — 2026-06-11 — Domain layer (Phase 2)

- Flyway `V2__domain_tables.sql`: `drivers`, `riders`, `rides`, `ride_offers`
  with status CHECK constraints, FKs, `unique(ride_id, driver_id)`, indexes on
  rides(status), rides(rider_id), ride_offers(ride_id), drivers(status, verified).
- JPA entities and Spring Data repositories in `driver`, `rider`, and
  `dispatch` modules (UUID cross-module references, optimistic-lock `version`
  on rides).
- `RideStatus` state machine (explicit transition map) and
  `Ride.transitionTo(...)` with `IllegalRideTransitionException`;
  driver-cancel rebroadcast capped at 1 via `rebroadcast_count`.
- Atomic conditional updates `RideRepository.claimRide` and
  `DriverRepository.markOnRide` (affected-row-count contract).
- Tests: full 8×8 transition matrix (parameterized), 10-thread claim/markOnRide
  races on Testcontainers Postgres, rebroadcast guard, ArchUnit
  `setStatus`-must-be-private rule. 71 tests green.

### Added — 2026-06-11 — Scaffolding

- Spring Boot modular monolith (Java 21, Gradle): empty `rider`, `driver`,
  `dispatch`, `fare`, `notification`, `admin`, `common` modules with
  `api`/`internal` package convention.
- ArchUnit module-boundary tests (no cross-module `internal` access; `common`
  depends on nothing).
- Flyway baseline migration (version table only).
- Postgres + Redis wiring; `GET /health` reporting app/db/redis status.
- `WhatsAppGateway` interface with no-op stub (no external calls).
- Next.js PWA skeleton: manifest, service-worker offline shell, placeholder
  "Book a ride" page.
- Local `docker-compose.yml` (Postgres 16, Redis 7).
- GitHub Actions CI: backend build + tests + ArchUnit, frontend build. No deploy.
