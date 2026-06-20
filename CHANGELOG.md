# Changelog

All notable changes to AutoDispatch are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

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
