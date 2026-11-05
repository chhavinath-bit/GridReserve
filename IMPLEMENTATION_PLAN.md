# GridReserve — Implementation Plan

> Version 0.2 (draft). Based on [PROJECT.md](PROJECT.md) v0.3, [REQUIREMENTS.md](REQUIREMENTS.md) v0.2, [TECH_STACK.md](TECH_STACK.md) v0.2, [ARCHITECTURE.md](ARCHITECTURE.md) v0.2 and [CLAUDE.md](CLAUDE.md).
> IDs such as `BR-10`, `PLAT-02`, `RES-03` refer to REQUIREMENTS.md; `D-xx` to ARCHITECTURE.md §19.
> Changes from v0.1: microservices from the start (platform skeleton in M1.1, identity service in M1.3, resilience and config refresh in M1.9, two-instance concurrency proof in M1.8); Spring Boot 4; library seats in Phase 1 and reading halls in Phase 2; separate config repository; payment, membership and notification services in their phases. Nothing from v0.1 was dropped.
> No application code exists yet. This plan describes the order of work; it does not implement anything.

## 1. How this plan is organized

- **Product phases P1–P8 stay as defined in PROJECT.md §9 and CLAUDE.md**, in strict order. **Phase 0** (environment and repositories) comes first.
- Each phase is split into **milestones**. A milestone is one branch, a few small commits, one merge. Each milestone lists: objective, tasks, files/modules, dependencies, tests, acceptance criteria, definition of done.
- **Phase 1 is planned in full detail**; Phases 2–8 at milestone level, detailed at the start of each phase once its `D-xx` decisions are settled.
- Per CLAUDE.md, every milestone starts with an agreed plan; new libraries, deletions and large changes are approved first.

### 1.1 Order of work in Phase 1, and why

```
M1.1 platform skeleton + CI + Boot 4 spikes ─► M1.2 booking schema & constraints proven in DB
 ─► M1.3 identity service + token verification ─► M1.4 catalog + availability ─► M1.5 booking engine core
 ─► M1.6 THIN VERTICAL SLICE (UI through gateway) ─► M1.7 all rules + hold expiry
 ─► M1.8 CONCURRENCY PROOF (incl. 2 instances behind gateway) ─► M1.9 resilience + config refresh
 ─► M1.10 container images + first deploy ─► M1.11 admin ─► M1.12 idempotency + audit
 ─► M1.13 frontend completion + Playwright ─► M1.14 phase close
```

- **Platform first, but thin.** M1.1 makes config server, Eureka, gateway and two empty services run together, because every later milestone depends on that wiring. No business logic yet.
- **Correctness before polish.** The double-booking guarantee is proven in SQL (M1.2), through the API (M1.5), under 50 parallel requests and across two instances (M1.8), before the UI is polished (M1.13).
- **Resilience is a milestone of its own (M1.9)**, after the core works, so failures are tested against real behavior.
- **First deploy at M1.10**, so the phase-end deploy is a repeat.
- **Idempotency and audit (M1.12)** are the last engine milestone (PROJECT.md §5.13); if Phase 1 runs long they move to the start of Phase 2.

### 1.2 Size scale

`S` ≈ up to half a day, `M` ≈ 1–2 days, `L` ≈ 3–5 days. Rough sizes to plan around job applications, not commitments. Phase 1 is now noticeably larger than in v0.1 because of the platform.

## 2. Git and documentation conventions

| Item | Convention |
|---|---|
| Repositories | `GridReserve` (application monorepo) and `gridreserve-config` (configuration), both public on GitHub |
| Default branch | `main`, always green |
| Milestone branch | `m<phase>.<n>-<short-name>`, e.g. `m1.9-resilience` |
| Config changes | PRs to `gridreserve-config`; a milestone that needs config changes links both PRs |
| Commits | Small, imperative subject, *why* in the body; no secrets ever |
| Merge | PR after CI is green (GitHub web UI, or `gh` if approved) |
| Milestone checkpoint | Merge commit on `main` + entry in `docs/PROGRESS.md` with actual test output |
| Phase checkpoint | Tag `phase-N-complete` / `v0.N.0` |

| When | Update |
|---|---|
| Every milestone | `docs/PROGRESS.md`, `README.md` (how to run/test what exists) |
| A command changes | CLAUDE.md §12 |
| A `D-xx` decision is made | ARCHITECTURE.md §19, CLAUDE.md §14, affected REQUIREMENTS.md items |
| A requirement changes | REQUIREMENTS.md (bump version, keep IDs stable) |
| End of a phase | README (features, diagram, demo accounts, live URL, how to run concurrency and resilience tests), CHANGELOG, tag |

## 3. Decisions needed before or during Phase 1

| # | Decision | Needed by | Status / recommendation |
|---|---|---|---|
| B-1 | Repository location | M0.2 | **Decided**: own repo in this folder. Note: on 2026-11-04 this folder was no longer inside any Git repository, so the "ignore in parent repo" step may be unnecessary; re-check in M0.1. |
| B-2 | Doc fixes from ARCHITECTURE v0.1 §17 | — | **Confirmed** by the owner in M0.3 (2026-11-05). |
| B-3/4/5 | Router, frontend tests, token storage | — | **Decided** (React Router; Vitest + RTL + Playwright; refresh-token cookie). |
| B-6 | Deploy host, backups, rollback (D-15); must fit ~5 JVMs + PostgreSQL | M1.10 | Decide at M1.10 after checking current limits. |
| B-7 | Load-test tool (D-13b) | M1.14 | JUnit harness first. |
| B-8 | Resilience4j on Spring Boot 4 (D-21), gateway variant (D-22), config lookup (D-20), shared lib scope (D-27) | M1.1 | Settle by the M1.1 spike. |
| B-9 | Service-to-service authentication (D-19) | M1.3 / M1.9 | Service JWT signed by identity (recommended). |

## 4. Phase 0 — Environment and repositories

### M0.1 Environment inspection `S`

1. **Objective.** Confirm the machine can build and run the whole stack; record versions.
2. **Tasks.** Record tool versions (2026-10-26 check: OpenJDK 21.0.12, Maven 3.10.0, Docker 29.8.2 running, Node 26.11.1, npm 12.2.0, `gh` missing). Confirm Docker can run `postgres:16` and has enough memory for ~6 containers (check Docker Desktop memory limit; recommend ≥ 6 GB). Confirm free ports 5432, 8080–8085, 8761, 8888, 5173 (6379 for P4). Re-check the Git situation (B-1 note).
3. **Files.** None; results go to `docs/PROGRESS.md` in M0.2.
4. **Dependencies.** None.
5. **Tests.** `java -version`, `mvn -v`, `docker info` (memory), `docker run --rm postgres:16 postgres --version`, `node -v`, `npm -v`, port checks.
6. **Acceptance.** All succeed; gaps listed with fixes.
7. **Done.** Reviewed with the owner.

### M0.2 Repositories `S`

1. **Objective.** Two public repositories with hygiene files and the planning documents.
2. **Tasks.** `git init` in this folder (branch `main`); create the public GitHub repo `chhavinath-bit/GridReserve` with `gh` and push. Create the sibling folder `~/Developer/project/gridreserve-config` (own `git init`, README "what is refreshable; never put secrets here") and its public repo `chhavinath-bit/gridreserve-config`. In the app repo add `.gitignore` (Maven `target/`, `node_modules/`, `dist/`, IDE files, `.DS_Store`, `.env`), `.editorconfig`, `.gitattributes`, `infra/.env.example` (placeholders: `POSTGRES_*`, per-service DB users/passwords, `JWT_PRIVATE_KEY`, `CONFIG_SERVER_URL`, `CONFIG_REPO_URI`, `APP_CORS_ORIGIN`), `README.md` skeleton, `docs/PROGRESS.md`, `LICENSE` (owner's choice). Commit the existing docs unchanged, with no AI attribution lines. `backend/` and `frontend/` are created in M1.1 with their first files (Git does not track empty folders).
3. **Files.** Hygiene files above; all `*.md` docs.
4. **Dependencies.** M0.1.
5. **Tests.** `git status` clean; `git check-ignore .env`; search for anything secret-looking.
6. **Acceptance.** Both remotes show their content; no secrets.
7. **Done.** Pushed. Checkpoint: `Add project docs and repository hygiene files`.

### M0.3 Confirm decisions and assumptions `S`

1. **Objective.** The owner confirms the 2026-11-04 doc changes and the open assumptions that affect Phase 1 (A-23 seat defaults, A-24 refresh-token details, A-25 resilience defaults, A-27 key rotation, A-28 memory budget).
2. **Tasks.** Review; record answers in REQUIREMENTS.md §17, ARCHITECTURE.md §19 and CLAUDE.md §15.
3. **Files.** `REQUIREMENTS.md`, `ARCHITECTURE.md`, `CLAUDE.md`.
4. **Dependencies.** M0.2.
5. **Tests.** Docs consistent (no contradictions found by a search for the changed items; the word "resource" unused).
6. **Acceptance.** Every Phase 1 assumption confirmed or changed.
7. **Done.** Merged; PROGRESS updated.

## 5. Phase 1 — Core engine and platform (EXCLUSIVE)

Phase goal (PROJECT.md §8): EXCLUSIVE booking end to end through the gateway; 50-parallel-request test passes (also with two booking-service instances); resilience tests pass; CI green; deployed; README complete.

### M1.1 Platform skeleton, CI and Boot 4 spikes `L`

1. **Objective.** Config server, discovery server, gateway and two minimal services (identity, booking) start together, read `dev` config from the config repo, register in Eureka, and are reachable through the gateway; CI builds everything.
2. **Tasks.**
   - `backend/pom.xml` parent: Spring Boot 4.x parent, Spring Cloud BOM in `<dependencyManagement>` (compatible train, verified against the official table), version properties for non-BOM libraries, modules. **Dependency list shown for approval first.** Paths below are relative to `backend/`.
   - `platform/config-server` (Git backend → `gridreserve-config`), `platform/discovery-server` (Eureka), `platform/api-gateway` (explicit path prefixes per service, no catch-all: `/api/v1/auth/**`, `/api/v1/me` → identity; `/api/v1/{categories,countries,venues,bookables,bookings,admin/bookings,ping}/**` → booking; unknown paths → gateway 404; `X-Request-Id`; JSON error handler).
   - `services/identity-service`, `services/booking-service`: config client (fail fast; container restart with backoff, PLAT-07), Eureka client, actuator health with liveness/readiness, a temporary ping (`/api/v1/ping` on booking, `/api/v1/auth/ping` on identity).
   - `libs/common-web`: `ErrorResponse`, `ErrorCode`, `DomainException`, global handler, `RequestIdFilter`, `Clock` bean.
   - `infra/compose.yaml`: postgres:16 + `infra/postgres/init-databases.sh` (identity_db, booking_db, users), config server (`native` profile, mounting `../../gridreserve-config` read-only), discovery, gateway, services; health-based `depends_on`.
   - Config repo: `application.yml`, `application-{dev,prod}.yml`, per-service files.
   - Profiles `dev`, `prod`, `test` (config client and Eureka off in `test`).
   - **Spikes (timeboxed, results in PROGRESS):** Spring Cloud OpenFeign on Boot 4 (a Feign call from booking-service to identity-service's `/internal/ping` through Eureka); Resilience4j through Spring Cloud CircuitBreaker on Boot 4 (D-21); gateway WebFlux variant (D-22); Testcontainers and Flyway artifact names on Boot 4; Jackson 3 effect on DTO serialization.
   - GitHub Actions `ci.yml`: `./mvnw verify` for all modules.
3. **Files/modules.** `backend/pom.xml`, `backend/platform/*`, `backend/services/{identity,booking}-service` skeletons, `backend/libs/common-web`, `infra/compose.yaml`, `infra/postgres/init.sql`, `.github/workflows/ci.yml`; config repo files.
4. **Dependencies.** Phase 0.
5. **Tests.** Context-load test per module (`test` profile); `ErrorResponseTest`; `RequestIdFilterTest`; manual: `docker compose up -d`, Eureka dashboard shows gateway, identity, booking; `curl localhost:8080/api/v1/ping` routed to booking; stop config server → a new service start fails fast after retries (FS-37 preview).
6. **Acceptance.** Whole platform starts with one command; routing through the gateway works; unknown path returns the JSON error; CI green; spike results recorded and D-20/21/22/27 decided.
7. **Done.** Merged; README "Run locally"; CLAUDE.md §12 commands verified; PROGRESS with test output.

### M1.2 Booking schema, constraints and seed data — prove the guarantee in the database `M`

1. **Objective.** The full P1 `booking_db` schema exists, and the database itself rejects double-booking and member overlap.
2. **Tasks.** Flyway in booking-service: `V1__baseline` (`btree_gist`), `V2__core_schema` (`member`, `category`, `bookable_type`, `country_time_zone`, `venue`, `venue_opening_hours`, `bookable`, `bookable_opening_hours`, `booking` with both exclusion constraints, ARCHITECTURE §5.3), `V3__seed_countries` (from IANA `zone1970.tab`/`zone.tab`), `V4__seed_catalog` (5 categories, bookable types table 2.3, Central Library with rooms and ≥ 10 seats, Sunrise Turf, Palm Apartments, CityCare Clinic, IronWorks; fictional; country + zone). JPA entities and repositories.
3. **Files/modules.** `services/booking-service/src/main/resources/db/migration/V1..V4`, `catalog/domain`, `booking/domain`, `member`, repositories.
4. **Dependencies.** M1.1.
5. **Tests.** `MigrationIT`; `BookingConstraintsIT` with plain SQL: overlap → `23P01` on `ex_booking_bookable_overlap` (BR-10); back-to-back allowed (BR-12); Trainer 10:00–10:45 + 15-min buffer blocks starts until 11:00 (A-08); opening-hours checks reject `opens >= closes` and weekday outside 1–7; cancelled/expired don't block; same member on two bookables overlapping → `ex_booking_member_overlap` (BR-11); seat and room by the same member at the same time → rejected; HELD without `expires_at` → check violation; venue with a zone of another country → FK violation. `SeedDataIT`: categories, types, labels, ≥ 10 seats.
6. **Acceptance.** All pass in CI; `occupied_until` approach confirmed.
7. **Done.** Merged; PROGRESS lists each constraint test.

### M1.3 Identity service and token verification `L`

1. **Objective.** Register, login, refresh, logout work through the gateway; access tokens are verified by the gateway and the booking service; the booking service provisions local member rows.
2. **Tasks.** identity_db migrations (`app_user`, `refresh_token`, seeded ADMIN + 2 MEMBERs with BCrypt hashes); `JwtSigner` (RS256, `kid`, private key from env); refresh tokens (opaque, hashed, rotation with conditional UPDATE, family revocation, cookie attributes, Origin + `X-GridReserve-Client` check); `/auth/register|login|refresh|logout`, `/me`; JWT verification filter in `common-web` (public key from config) used by booking-service; gateway JWT pre-check filter; `MemberProvisioner` (`INSERT … ON CONFLICT DO NOTHING`); decide D-19 (service credentials) for later internal calls.
3. **Files/modules.** `services/identity-service/**`, `libs/common-web/security/**`, `platform/api-gateway` filters, `services/booking-service/member/**`, config repo (public key, cookie settings), `.env.example` (`JWT_PRIVATE_KEY` placeholder).
4. **Dependencies.** M1.2.
5. **Tests.** Unit: signer/verifier (valid, expired, tampered, wrong key, unknown `kid`); refresh rotation logic. Integration (identity): register 201/409 (case-insensitive); login 200 + cookie attributes; wrong password vs unknown email identical 401; refresh rotates; reuse of a rotated token revokes the family (FS-39); foreign Origin or missing header → 403 (FS-40); logout revokes. Integration (booking): request with valid token → member row created; invalid/expired → 401 (FS-14); member → admin URL 403 (FS-16). Manual through gateway with `curl`.
6. **Acceptance.** FR-AUTH-01..13 and FR-ROLE-01..03 covered; booking-service never calls identity to verify a token.
7. **Done.** Merged; README "Log in with curl"; PROGRESS updated.

### M1.4 Catalog, countries and availability `M`

1. **Objective.** Members browse categories → venues → bookables (grouped by type) and see slots for a date; admins can list countries and zones.
2. **Tasks.** `RuleResolver` (bookable → venue hours → type → category, FR-CAT-05); `LocalSlotGrid`; catalog read endpoints (FR-CAT-06); `GET /countries`, `/countries/{code}/time-zones` (FR-CAT-14); availability (FR-AV-01..06) incl. stale holds as FREE and horizon check.
3. **Files/modules.** booking-service `catalog`, `availability`, `common/time`.
4. **Dependencies.** M1.3.
5. **Tests.** Unit: resolver (each override layer), slot grid (15/30/60 grids, edges, per-weekday hours, closed weekday, bookable hours replacing venue hours, a DST zone date doesn't crash). Integration: only active items for members; labels from type; availability TAKEN/FREE/PAST, buffer, stale hold FREE, closed weekday → `closed: true`, beyond 7-day horizon 422; countries list and single-zone country.
6. **Acceptance.** FR-CAT-01..06, FR-CAT-14, FR-AV-01..06 covered; no per-category or per-type `if`s.
7. **Done.** Merged; README example requests through the gateway; PROGRESS updated.

### M1.5 Booking engine core `L`

1. **Objective.** Hold, confirm, cancel through the gateway with database-backed conflict protection.
2. **Tasks.** `BookingRules` (BR-01, 05, 06, 09 now; BR-14 framework); `createHold` (bookable `FOR SHARE`, member `FOR UPDATE`, stale-hold sweep for bookable and member, `occupied_until`, insert HELD); confirm/cancel as conditional UPDATEs; `404` for others' bookings; admin cancel path; `ConstraintViolationMapper`; `GET /bookings/me`, `/bookings/{id}`; transaction retry on `40P01` (D-11).
3. **Files/modules.** booking-service `booking/**`, `common/error`, `common/tx`.
4. **Dependencies.** M1.4.
5. **Tests.** Unit: rules, service with Mockito. Integration `BookingLifecycleIT`: hold 201; confirm; second confirm 409; confirm after expiry (moved clock) 409 + EXPIRED (FS-08); overlap 409 `SLOT_TAKEN`; same member elsewhere overlapping 409 `MEMBER_OVERLAP` (FS-02); stale hold doesn't block (FS-21); other member's booking 404 (FS-15); admin cancel.
6. **Acceptance.** FR-BK-01..09, BR-10..13 verified through the API; conflicts never return 500.
7. **Done.** Merged; README "Book with curl"; PROGRESS updated.

### M1.6 Thin vertical slice (frontend through the gateway) `M`

1. **Objective.** Login → category → venue → bookable → slot → hold → confirm → My bookings, in a browser, through the gateway; plain UI.
2. **Tasks.** Scaffold `frontend/` (Vite React TS, Tailwind, TanStack Query, React Router, Vitest + RTL, shadcn/ui init with Radix; **package list incl. D-UI-3 for approval**); design tokens for light and dark from DESIGN_SYSTEM.md §3 in `src/styles/globals.css`, theme toggle; base components needed by the slice (Button, Input, Label, Select, Card, Badge, Skeleton, Alert, Toast) and first patterns (`AppShell`, `CategorySelect`, `SlotGrid`, `HoldPanel`, `StatusBadge`, `ConflictNotice`, empty/error/loading states); dev proxy `/api` → `:8080`; `api/client.ts` with in-memory token, single-flight refresh, error parsing; pages Login, Browse, SlotPicker, HoldPanel, MyBookings; "just taken" handling; CI frontend job.
3. **Files/modules.** `frontend/**`, `.github/workflows/ci.yml`.
4. **Dependencies.** M1.5.
5. **Tests.** `npm run typecheck`, `npm test` (client error parsing, refresh single-flight, time formatting), `npm run build`. Manual two-window race (US-06), page reload keeps the session (refresh cookie).
6. **Acceptance.** US-02..07 demonstrable; labels from types; no token in localStorage; slice screens use only design tokens and shared components (no raw colors), in light and dark.
7. **Done.** Merged; README "Run the frontend"; PROGRESS updated; optional tag `slice-1`.

### M1.7 All booking rules and hold expiry `M`

1. **Objective.** Every P1 rule enforced in order; expired holds cleaned up on any number of instances.
2. **Tasks.** BR-02, 03, 04, 07, 08, 14; CXL-01..06; `SchedulingConfig` (bounded `gr-sched-*`); `HoldExpiryJob` with `SKIP LOCKED` and per-row transactions; metrics by expiry layer; graceful shutdown.
3. **Files/modules.** booking-service `booking/service/*`, `common/config`.
4. **Dependencies.** M1.6.
5. **Tests.** Table-driven `BookingRulesTest` per rule and per type default (Room 30/3h, Seat 60/4h, Doctor 30 exact, Bay 12h, Trainer 45 + 15 buffer on a 15-min grid, booking on a closed weekday, booking outside that weekday's hours), boundaries, order; `HoldExpiryJobIT` (two job runs in parallel do not double-process); `CancellationIT` with a fixed clock.
6. **Acceptance.** BR-01..14, CXL-01..06, FR-EXP-01..04 covered by named tests.
7. **Done.** Merged; README lists defaults per type; PROGRESS updated.

### M1.8 Concurrency proof (single and two instances) `M`

1. **Objective.** Prove the guarantees under real parallelism, including with two booking-service instances behind the gateway.
2. **Tasks.** `ConcurrencyTestSupport` (pre-created members, `CountDownLatch` gate, HTTP via `RANDOM_PORT`); CONC-01..05, CONC-09 (or in M1.11); **CONC-10** system test: Compose with `--scale booking-service=2`, 50 parallel requests through the gateway, assert 1 × 201 + 49 × 409 and one active row; verify both instances handled requests (logs/metrics). Run each 20 times; deadlock counter stays 0.
3. **Files/modules.** booking-service `src/test/.../concurrency/*`; `system-tests/` (Compose-based, JUnit or script — decide in milestone plan); CI system-test job.
4. **Dependencies.** M1.7.
5. **Tests.** The suite above; full `./mvnw verify`; CONC-01 < 10 s (NFR-PERF-03).
6. **Acceptance.** All CONC tests 20/20 locally and in CI; PROJECT.md §8 criterion met including two instances.
7. **Done.** Merged; README "How double-booking is prevented (even with two instances)"; PROGRESS with actual output.

### M1.9 Resilience patterns and configuration refresh `L`

1. **Objective.** Retry, RateLimiter, Bulkhead, CircuitBreaker and timeouts are in place with fallbacks; refreshable config works across all instances.
2. **Tasks.**
   - Resilience defaults in the config repo (A-25), as validated `@ConfigurationProperties`, refreshable.
   - Gateway: per-route CircuitBreaker with JSON fallback (`503`), response timeout (`504`), Retry for GET routes only.
   - booking-service → identity `IdentityClient` (`GET /internal/users?ids=`): `@FeignClient` + `IdentityGateway` adapter with Resilience4j (timeouts in config, Bulkhead, CircuitBreaker, Retry; Feign retryer off; `ErrorDecoder` 4xx vs 5xx), fallback (IDs only); service credential per D-19 via a Feign request interceptor; WireMock approved first (D-28).
   - Rate limiter (Resilience4j) wired as a reusable policy (first real use in P5), with a test.
   - `scripts/refresh-config.sh` (Eureka lookup → `POST /actuator/refresh` per instance, authenticated); actuator refresh not routed by the gateway.
   - Resilience metrics exposed (RES-08).
3. **Files/modules.** `platform/api-gateway`, booking-service `client/**`, identity-service `/internal/users`, `libs/common-web` (resilience helpers if any), `scripts/refresh-config.sh`, config repo.
4. **Dependencies.** M1.8.
5. **Tests.** Feign client tests against WireMock: timeout → fallback; 5xx sequence opens circuit (FS-35); half-open recovery; 11 parallel calls → bulkhead rejection (FS-36); no retry on 4xx; retry on 503 then success. System tests: stop identity → hold/confirm still work, login 503 (FS-32, US-12); admin list degraded (FS-33); kill one booking instance under load (FS-34); stop Eureka (FS-38); stop config server (FS-37). Refresh test: change a threshold in the config repo, run the script, both instances report the new value (US-13).
6. **Acceptance.** RES-01..10, PLAT-05, FS-32..38 covered; no remote call inside a transaction (reviewed).
7. **Done.** Merged; README "Resilience demo" and "Change config without restart"; PROGRESS updated.

### M1.10 Container images and first deployment `L`

1. **Objective.** All services run from images on the chosen host, configured by env vars and the config server; only the gateway and frontend are public.
2. **Tasks.** Decide B-6 (host must fit ~5 JVMs + PostgreSQL; check current limits; backups/rollback). Multi-stage Dockerfile per service (non-root, heap cap, health check). Frontend static build on the same site as the gateway (cookie `SameSite=Strict`). `prod` profile files in the config repo. CI builds all images; CD for the chosen host with secrets in GitHub encrypted secrets.
3. **Files/modules.** `platform/*/Dockerfile`, `services/*/Dockerfile`, `frontend/Dockerfile` (if needed), `.github/workflows/{ci,deploy}.yml`, config repo `*-prod.yml`, `.env.example`.
4. **Dependencies.** M1.9; B-6.
5. **Tests.** Compose `full` run from images + manual journey; deployed smoke test: gateway health, login, refresh after reload, hold + confirm + cancel; `docker history` shows no secrets.
6. **Acceptance.** Live URL works; internal services not reachable publicly; restart keeps data; the host restarts a crashed container with backoff (PLAT-07: services fail fast when the Config Server is down).
7. **Done.** Merged; README "Live demo"; D-15 recorded; tag `first-deploy`.

### M1.11 Admin catalog management and oversight `M`

1. **Objective.** Admins manage venues (country + zone) and bookables and oversee all bookings.
2. **Tasks.** Venue/bookable create/update with validation (FR-CAT-07..09, 12, 15); country/zone validation; activate/deactivate as **soft close** with locks (FR-CAT-10/11/13/16: stops new bookings, existing ones untouched, response reports active future bookings); `@Version` → `409 STALE_UPDATE`; `GET /admin/bookings` enriched through `IdentityClient` with fallback (FR-ADM-01, RES-06).
3. **Files/modules.** booking-service `catalog/api`, `booking/api/AdminBookingController`, `client`.
4. **Dependencies.** M1.10.
5. **Tests.** `AdminCatalogIT` (field errors, wrong-country zone 400, member 403, FS-13: deactivate with a future CONFIRMED booking and an open hold → 200, booking unchanged, hold can still be confirmed, new hold 422, bookable hidden from members; venue deactivate/reactivate restores bookables' own flags; stale version 409); `AdminBookingsIT` (filters, pagination, degraded mode); CONC-09 if moved here.
6. **Acceptance.** US-09, US-10, FR-CAT-07..15, FR-ADM-01..03.
7. **Done.** Merged; deployed; PROGRESS updated.

### M1.12 Idempotency and audit log `M`

1. **Objective.** Retried booking requests never duplicate; every state change is audited.
2. **Tasks.** Migrations `booking_audit`, `idempotency_record`; `AuditService` in every transition transaction; `GET /admin/bookings/{id}/audit`; `IdempotencyService` (lease, replay, 422, 409, takeover); cleanup job; frontend key per "Book" click.
3. **Files/modules.** booking-service `audit/**`, `booking/service/Idempotency*`, migrations; `frontend/src/lib/idempotency.ts`.
4. **Dependencies.** M1.11.
5. **Tests.** `IdempotencyIT` (FS-17, FS-18, lease takeover, 5xx not stored), CONC-08, `AuditIT` (one row per transition, rollback leaves none, SYSTEM actor on expiry).
6. **Acceptance.** IDEM-01..07, CONC-08, FR-AUD-01..03.
7. **Done.** Merged; deployed; PROGRESS updated.

### M1.13 Frontend completion and end-to-end tests `L`

1. **Objective.** Full P1 frontend and Playwright coverage of the main flows.
2. **Tasks.** Complete the DESIGN_SYSTEM.md component inventory (Dialog, AlertDialog, Sheet, Combobox, DatePicker, DropdownMenu, Tabs, Tooltip, Pagination, `DataTable`, `CountryZonePicker`, `AuditTimeline`, `DegradedBanner`, `DateStrip`, `BookingCard`, `PageHeader`); dev-only gallery `/dev/components` showing every component state in light and dark; Register page; admin pages (venues with country → zone select, bookables with overrides, all bookings with degraded indicator, audit drawer); guards; messages for every error code incl. 503/504 (DESIGN_SYSTEM.md §6); loading/error/empty everywhere; hold-expired flow; accessibility pass (keyboard-only, screen reader, axe if D-UI-4 approved); Playwright light/dark screenshots of key screens; Playwright specs: journey, race (two contexts), refresh after reload, degraded mode (identity stopped).
3. **Files/modules.** `frontend/src/**`, `frontend/e2e/**`, CI system-test job.
4. **Dependencies.** M1.12.
5. **Tests.** Vitest/RTL component tests; `npm run e2e` against Compose; keyboard-only pass.
6. **Acceptance.** Every P1 user story demonstrable on the deployed site; e2e green in CI.
7. **Done.** Merged; deployed; README screenshots; PROGRESS updated.

### M1.14 Phase 1 close `M`

1. **Objective.** Meet the Phase 1 definition of done.
2. **Tasks.** Performance check through the gateway (availability p95, hold p95, hot slot with two instances, Hikari sizing, D-09); `EXPLAIN (ANALYZE)` on key queries; memory per service measured (NFR-PERF-05); README final (Mermaid architecture, run/test instructions, concurrency and resilience demos, demo accounts, live URL); CHANGELOG `v0.1.0`; traceability check (REQUIREMENTS §19).
3. **Files/modules.** `README.md`, `CHANGELOG.md`, `docs/PROGRESS.md`, perf harness under tests.
4. **Dependencies.** M1.1–M1.13.
5. **Tests.** Full `./mvnw verify`, frontend checks, system tests, deployed smoke test, perf harness.
6. **Acceptance.** PROJECT.md §8 criteria met.
7. **Done.** Tag `phase-1-complete` / `v0.1.0`.

### Phase 1 definition of done

- [ ] All P1 requirements traced to passing tests or documented manual checks
- [ ] CONC-01 and CONC-10 pass (1 × 201, 49 × 409; also with two instances)
- [ ] Resilience system tests FS-32..38 pass
- [ ] CI green on `main` (modules, frontend, images, system tests)
- [ ] Deployed and smoke-tested
- [ ] README, CHANGELOG, PROGRESS, CLAUDE.md commands up to date
- [ ] Tag `phase-1-complete`

## 6. Phases 2–8

Each phase: settle its `D-xx`, agree a detailed milestone plan, implement, then close with tests, CI, deploy, README, CHANGELOG, tag.

### Phase 2 — Capacity model (gym classes and reading halls) `L`

1. **Objective.** CAPACITY sessions that never oversell, for Gym CLASS and Study HALL types.
2. **Milestones.** M2.1 `session` table, `booking.session_id` FK, partial unique seat index, CHECK, session overlap rule (D-17), proven in SQL. M2.2 `SeatService` atomic claim/release; `POST /bookings {sessionId}`; expiry and cancel release seats; admin session CRUD/cancel; reading-hall session defaults (A-26). M2.3 Concurrency: CONC-06, CONC-07, member overlap across models, plus two-instance run. M2.4 Frontend: class schedule and reading-hall sessions with seats left; seed Riverside Reading Hall and IronWorks classes. M2.5 close.
3. **Modules.** booking-service `capacity/**`, `booking/service`, migrations; `frontend/src/features/sessions/**`.
4. **Dependencies.** Phase 1 complete (M1.12 first if it slipped).
5. **Tests.** `SeatClaimIT`, CONC-06/07, FS-24/25, regression of all P1 tests.
6. **Acceptance.** CAP-01..09; `booked` always within `[0, capacity]`.
7. **Done.** Tag `phase-2-complete`.

### Phase 3 — Waitlist and offers `L`

1. **Objective.** Fair FIFO waitlists with expiring offers.
2. **Milestones.** M3.0 spike D-05 (D-03 decided in M0.3). M3.1 `waitlist_entry`, join/leave/list. M3.2 `PromotionService` via `SlotReleasedListener` in the releasing transaction; offers as HELD bookings; skip/cascade. M3.3 close job, accept/decline. M3.4 concurrency (WL-10, CONC-07), deadlock regression test, two instances. M3.5 frontend. M3.6 close.
3. **Modules.** booking-service `waitlist/**`; frontend `features/waitlist/**`.
4. **Dependencies.** Phase 2.
5. **Tests.** `WaitlistIT`, `PromotionIT`, FS-26/27, deadlock test 50×.
6. **Acceptance.** WL-01..10; no lost promotion when the process dies mid-cancel.
7. **Done.** Tag `phase-3-complete`.

### Phase 4 — Redis `M`

1. **Objective.** Faster availability and distributed rate limiting; Redis optional for correctness.
2. **Milestones.** (Cache first.) M4.0 decide D-06/D-07; add Redis to Compose/Testcontainers (approval). M4.1 booking-service `AvailabilityCache` with generation counter, after-commit bump, CircuitBreaker + timeout, fallback. M4.2 Redis-down tests (FS-20). M4.3 gateway `RequestRateLimiter` (login by IP, booking by user), `429` + `Retry-After` in the standard error format; identity-service per-email failed-login throttling (ARCHITECTURE §20 item 3). M4.4 deploy with Redis; close.
3. **Modules.** booking-service `cache/**`; api-gateway; identity-service; config repo.
4. **Dependencies.** Phase 3.
5. **Tests.** Cache hit/miss/invalidation/stale-writer race; `RedisDownIT` and system test; rate-limit tests; perf with Redis on/off.
6. **Acceptance.** Same results with Redis on, off or failing; NFR-REL-01, NFR-SEC-06.
7. **Done.** Tag `phase-4-complete`.

### Phase 5 — Payment service, outbox and refunds `L`

1. **Objective.** Mocked payments and refunds, with reliable events between services.
2. **Milestones.** M5.0 settle D-19 for `/internal/**` if not done. M5.1 outbox + delivery tables, relay (`SKIP LOCKED`, backoff, dead letter, bounded executor, resilience), receiver `processed_event` helper (in `common-web`). M5.2 `payment-service` (new module, `payment_db`, Eureka, config, gateway route); `PaymentProvider` + mock with Resilience4j RateLimiter; checkout hold duration (A-04); payment state machine. M5.3 webhook (HMAC, dedupe). M5.4 booking-service emits `BookingCancelled`/`BookingConfirmed`; payment-service refunds via events (CXL-08). M5.5 frontend checkout/refund status; close.
3. **Modules.** `services/payment-service/**`, booking-service `outbox/**`, `libs/common-web` (event envelope), config repo (subscriptions), compose.
4. **Dependencies.** Phase 4.
5. **Tests.** Relay tests (at-least-once, two relays don't double-send, dead letter, open circuit for one subscriber only), receiver dedupe, FS-28/29/41, crash between commit and delivery.
6. **Acceptance.** EVT-01..07, IDEM-08/11; one refund per cancellation in effect; no real money or card data.
7. **Done.** Tag `phase-5-complete`.

### Phase 6 — Membership service and venue managers `M`/`L`

1. **Objective.** Plans, limits and credits; venue managers scoped to their venues.
2. **Milestones.** M6.0 decide D-25 (reservation saga vs credits in booking-service) and D-26. M6.1 `membership-service` (plans, memberships, per-plan limit overrides exposed to booking-service). M6.2 credits (saga per §11.3 with idempotent commands and compensation, or in-booking alternative). M6.3 VENUE_MANAGER role in identity, assignments, scope checks in booking-service. M6.4 frontend; close.
3. **Modules.** `services/membership-service/**`, identity-service, booking-service, frontend.
4. **Dependencies.** Phase 5.
5. **Tests.** Credit race (parallel bookings with 1 credit → 1 success), compensation when booking fails, reservation timeout, scope 403 tests, US-24/25.
6. **Acceptance.** Credits never negative and never lost; managers confined to their venues.
7. **Done.** Tag `phase-6-complete`.

### Phase 7 — Recurring bookings and no-shows `L`

1. **Objective.** Weekly series with skip-and-report; no-show penalties.
2. **Milestones.** (Per-weekday hours moved to Phase 1, A-06.) M7.1 holiday/closure dates (optional, if approved). M7.2 `booking_series`, local wall-clock occurrences across DST (A-20, D-16b), skip-and-report, idempotent creation (IDEM-09). M7.3 no-shows and block rule (A-21). M7.4 frontend; close.
3. **Modules.** booking-service `recurring/**`, `catalog`; frontend.
4. **Dependencies.** Phase 6.
5. **Tests.** FS-30, FS-31, series idempotency, block window with a fixed clock.
6. **Acceptance.** US-26/27; DST tests pass.
7. **Done.** Tag `phase-7-complete`.

### Phase 8 — Queue, notification service and polish `L`

1. **Objective.** Walk-in queue, notifications, analytics, API docs, final polish.
2. **Milestones.** M8.1 walk-in queue in booking-service. M8.2 `notification-service` consuming outbox events (in-app + simulated email, dedupe). M8.3 admin analytics. M8.4 OpenAPI per service aggregated at the gateway (springdoc, approval); distributed tracing if approved (D-24). M8.5 final README and interview demo script; close.
3. **Modules.** booking-service `queue/**`, `services/notification-service/**`, gateway, frontend.
4. **Dependencies.** Phase 7.
5. **Tests.** Queue ordering under parallel joins; notification dedupe (US-29); OpenAPI generated in CI.
6. **Acceptance.** US-28/29; docs published; demo script runs on the live site.
7. **Done.** Tag `phase-8-complete` / `v1.0.0`.

## 7. Checklist for every milestone

Before starting:
- [ ] Milestone plan agreed; new libraries or large changes approved
- [ ] Relevant files inspected first

While working:
- [ ] Tests written with the code, naming the requirement IDs they cover
- [ ] No secrets in either repository; new env vars added to `infra/.env.example`
- [ ] No remote call inside a DB transaction; every remote call has a timeout and a resilience policy
- [ ] No "resource" in code, API paths, UI or docs

Before merging:
- [ ] `./mvnw verify` passes locally (Docker running); frontend checks pass; system tests if affected
- [ ] Actual test output in `docs/PROGRESS.md` (never claim a pass without running it)
- [ ] README / CLAUDE.md commands / affected docs / config repo updated
- [ ] CI green
- [ ] Review guide `docs/reviews/M<x.y>-review.md` written (key files, commands, explain-it-back questions, gaps)
- [ ] Owner has reviewed the code, rerun the tests and acceptance checks, and signed off

After merging:
- [ ] Deployed (from M1.10) and smoke-tested
- [ ] PROGRESS marked done

## 8. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Platform work delays the core | No showable booking engine for a long time | M1.1 kept thin; vertical slice by M1.6 |
| Spring Boot 4 ecosystem gaps (Resilience4j, other libs) | Blocked or hacky integration | M1.1 spikes; fallback options in D-21 |
| Memory: ~5 JVMs + PostgreSQL | Laptop slowness; hosting cost / free-tier limits | Heap caps; measure in M1.14; choose host at M1.10 with this in mind |
| Distributed failures are hard to test | Untested resilience claims | Dedicated M1.9 with stub-server tests and Compose system tests |
| Flaky concurrency or system tests | Lost trust | Start gates, DB-state assertions, 20× local runs, no sleeps, health-based waits |
| Config drift between repos | Services fail at startup | Linked PRs; fail-fast startup; config repo README |
| Job search time | Less project time | S/M/L sizing; the project never delays applications |
