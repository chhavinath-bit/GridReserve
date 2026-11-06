# Progress log

The latest verified checkpoint is the most recent milestone below marked **Done** with recorded results and a matching commit on `main` (CLAUDE.md §1).

| Milestone | Status | Date | Commit |
|---|---|---|---|
| M0.1 Environment inspection | Done | 2026-11-05 | `3f44318` (recorded in the initial commit) |
| M0.2 Repositories | Done | 2026-11-05 | `3f44318` app repo, `a346654` config repo; results recorded in the next commit |
| M0.3 Confirm decisions and assumptions | Done | 2026-11-05 | `74ea039` (merge of PR #1) |
| M1.1 Platform skeleton, CI and Boot 4 spikes | Done (owner reviewed; [review guide](reviews/M1.1-review.md)) | 2026-11-05 | `9d636e0` (merge of PR #2); config repo `ae47541` (merge of PR #1); CI on `main` green, 16 tests |
| M1.2 Booking schema, constraints and seed data | Done (owner reviewed; [review guide](reviews/M1.2-review.md)) | 2026-11-05 | `fae582d` (merge of PR #5); config repo `4209388` (merge of PR #2); CI on `main` (`793a526`) green; local `verify` on `main` 2026-11-06: 16 + 29 tests, 0 failures |
| M1.3a Identity core (register, login, `/me`, RS256 tokens) | Done (owner reviewed; [review guide](reviews/M1.3a-review.md)) | 2026-11-06 | `6dc7ce6` (merge of PR #6); config repo `fd64800` (merge of PR #3); CI on `main` (`6dc7ce6`) green; local `verify` on `main` 2026-11-06: 32 + 44 tests, 0 failures |
| M1.3b Refresh tokens, logout, register logs in | Done (owner reviewed; [review guide](reviews/M1.3b-review.md)) | 2026-11-06 | `7b7edb9` (merge of PR #7); config repo `aa5775a` (merge of PR #4); CI on `main` (`7b7edb9`) green, 36 + 60 tests |
| M1.3c Token checks in the gateway and booking-service | Built; **awaiting owner review** ([review guide](reviews/M1.3c-review.md)) | 2026-11-06 | branch `m1.3c-token-verification`; no config repo change |

---

## M0.1 Environment inspection — Done (2026-11-05)

Read-only checks; no project files changed.

| Check | Command | Result |
|---|---|---|
| Java | `java -version` | OpenJDK 21.0.12.1 (Homebrew) |
| Java used by Maven | `mvn -v` | Initially Java 27 (`JAVA_HOME` unset). Fixed by the owner: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` in `~/.zshrc`; re-checked in a fresh shell → **21.0.12.1** |
| Maven | `mvn -v` | 3.10.0 (project will use the wrapper `./mvnw`) |
| Node / npm | `node -v`, `npm -v` | 26.11.1 / 12.2.0 |
| Git | `git --version` | 2.56.0 |
| GitHub CLI | `gh --version`, `gh auth status` | 2.103.3, logged in as `chhavinath-bit` (scopes `repo`, `workflow`, `gist`, `read:org`) |
| Docker | `docker --version`, `docker compose version`, `docker info` | 29.8.2, Compose v5.5.1, daemon running, 10 CPUs, 7.7 GB memory for containers |
| PostgreSQL image | `docker run --rm postgres:16 postgres --version` | PostgreSQL 16.15 |
| Host | `sw_vers`, `sysctl` | macOS 27.2, 16 GB RAM, 10 CPUs, 344 GB free disk |
| Ports | `lsof -iTCP:<port> -sTCP:LISTEN` | 5432, 5173, 6379, 8080–8085, 8761, 8888 all free |
| Git location | `git rev-parse --show-toplevel` | Folder not inside any Git repository → plain `git init` (B-1) |

Open issues: none.

---

## M0.2 Repositories — Done (2026-11-05)

What was done:
- `git init -b main` in `~/Developer/project/GridReserve`; added `.gitignore`, `.editorconfig`, `.gitattributes`, `LICENSE` (MIT), `README.md`, `infra/.env.example` (placeholders only), this log; committed the planning docs unchanged.
- Created the sibling folder `~/Developer/project/gridreserve-config` with its own `git init -b main`, `README.md`, `LICENSE`, `.gitignore`, `.gitattributes`.
- Created both public repositories with `gh repo create … --public --source=. --push`.
- Bootstrap commits went directly to `main` (an empty repository has no base for a pull request). From M0.3 on, every milestone uses a `m<phase>.<n>-<name>` branch and a pull request.
- Commit author: Chhavi Nath with the personal Gmail address (owner's choice); no AI attribution lines.

Verification (actual results):

| Check | Command | Result |
|---|---|---|
| `.env` files ignored | `git check-ignore -v .env infra/.env` | both matched by `.gitignore:19` |
| IDE and OS files ignored | `git check-ignore -v .idea .DS_Store` | matched by `.gitignore:26` and `:32` |
| Env template tracked | `git ls-files --error-unmatch infra/.env.example` | tracked |
| Secret scan, app repo | `git grep --cached -nIE "<private-key / AWS / GitHub / Slack / Stripe token patterns>"` | no matches (exit 1) |
| Password-like assignments | `git grep --cached -nIE "(PASSWORD\|SECRET\|KEY)[A-Z_]*="` | only `change-me` placeholders in `infra/.env.example` |
| Secret scan, config repo | same token patterns | no matches (exit 1) |
| Remote state | `gh repo view chhavinath-bit/<repo>` | both PUBLIC, default branch `main`, license detected as MIT |
| Remote contents | `gh api repos/chhavinath-bit/<repo>/contents` | app repo: docs, hygiene files, `docs/`, `infra/`; config repo: README, LICENSE, `.gitignore`, `.gitattributes` |
| Local state | `git status -sb` | both `## main...origin/main`, clean |
| No AI attribution | `git log --all --format=%B \| grep -ci "co-authored\|generated with"` | 0 in both repositories |

Note: the first secret-scan command had a wrong argument order (`--cached` after the pattern) and errored; it was re-run correctly with the results above.

Links: https://github.com/chhavinath-bit/GridReserve · https://github.com/chhavinath-bit/gridreserve-config

## M0.3 Confirm decisions and assumptions — Done (2026-11-05)

Owner review in three batches. Recorded in REQUIREMENTS.md v0.4 (§2.3, §6, §17), ARCHITECTURE.md (§5.3 schema, §19 D-03 and D-32, §20), CLAUDE.md §15, IMPLEMENTATION_PLAN.md (M1.2, M1.4, M1.7, P3, P7) and DESIGN_SYSTEM.md (`DateStrip`).

| Item | Decision |
|---|---|
| A-23 Library seats | Confirmed: 60-minute grid, up to 4 h |
| A-06 Opening hours | **Changed**: per-weekday hours from Phase 1 (`venue_opening_hours`, optional `bookable_opening_hours`; no row = closed); still no booking across local midnight |
| A-07 Booking horizon | **Changed**: 7 days (was 14) |
| A-08 Gym Trainer | **Changed**: 15-minute grid, 45-minute sessions, 15-minute buffer (one client per hour) |
| A-02 / A-24 Tokens | Confirmed: access 15 min, refresh 7 days; cookie HttpOnly, Secure (prod), `SameSite=Strict`, path `/api/v1/auth`; Origin + `X-GridReserve-Client` checks |
| A-03 Other member's booking | Confirmed: 404 |
| A-27 Key rotation | Confirmed: manual, with `kid` |
| A-25 Resilience defaults | Confirmed as proposed |
| A-28 Memory | Confirmed: 256 MB heap per service |
| A-05 / A-09 / A-12 | Confirmed: expiry job 30 s; limit counts HELD + CONFIRMED not yet ended; idempotency keys 24 h |
| 2026-11-04 doc fixes (B-2) incl. D-03 | Confirmed; D-03 (promotion inside the releasing transaction) now decided |

Verification (actual results):

| Check | Command | Result |
|---|---|---|
| Every edit applied exactly once | Python replace script asserting one match per change | "all edits applied" |
| No stale values left | `grep -n "14 days\|default 14\|DEFAULT 14\|60 / 60 / 60 / 15\|Trainer 60\|confirm at P3\|confirm D-03\|same every day" *.md` | one match, the intentional "resolved" note in ARCHITECTURE.md §20 |

## M1.1 Platform skeleton, CI and Boot 4 spikes — Done (2026-11-05)

The results below are Claude's first pass. Owner verification: [docs/reviews/M1.1-review.md](reviews/M1.1-review.md).

**Versions** (checked on Maven Central the same day): Spring Boot 4.1.2, Spring Cloud 2025.1.4 (Config 5.0.6, Netflix 5.0.3, Gateway 5.0.4, OpenFeign 5.0.4, CircuitBreaker 5.0.3 with Resilience4j 2.3.0), Maven wrapper 3.10.0, Java 21. Release candidates (Boot 4.2.0-RC1, Cloud 2026.0.0-RC1) not used.

**Built:** `backend/` parent POM (BOM-managed versions), `libs/common-web` (error JSON, request IDs, UTC clock via auto-configuration), `platform/config-server`, `platform/discovery-server` (Eureka, local config), `platform/api-gateway` (WebFlux; request IDs, JSON errors, circuit-breaker fallback), `services/identity-service` and `services/booking-service` skeletons with a temporary ping (`/api/v1/ping` on booking, `/api/v1/auth/ping` on identity) and a Feign spike client; one `backend/Dockerfile` (`MODULE` build arg, non-root); `infra/compose.yaml` and `infra/postgres/init-databases.sh`; `.github/workflows/ci.yml`. Config repo: shared, dev, prod, gateway and service files.

### Automated tests

| Command | Result |
|---|---|
| `cd backend && ./mvnw -B verify` | BUILD SUCCESS; 7 reactor modules; **16 tests, 0 failures, 0 errors, 0 skipped** (common-web 8, config-server 1, discovery-server 1, api-gateway 2, identity-service 2, booking-service 2) |
| `./mvnw -pl services/booking-service -am test` | BUILD SUCCESS (8 + 2 tests) |
| `./mvnw -pl services/booking-service -am test -Dtest=BookingServiceApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` | BUILD SUCCESS (2 tests) |

### Compose stack (`cd infra && docker compose up -d --build --wait`)

| Check | Result |
|---|---|
| All containers healthy | postgres, config-server, discovery-server, api-gateway, identity-service, booking-service: healthy |
| Eureka registry (`/eureka/apps`) | API-GATEWAY, IDENTITY-SERVICE, BOOKING-SERVICE: UP |
| Config server `GET /booking-service/dev` | serves `application-dev.yml`, `booking-service.yml`, `application.yml` |
| `curl -i localhost:8080/api/v1/ping` | 200 from booking-service, one `X-Request-Id` header |
| Request ID round trip | sent `X-Request-Id: trace-m11-0003` → returned unchanged by booking-service; none sent → gateway generates a UUID |
| Unknown path `/api/v1/does-not-exist` | 404 standard error JSON from booking-service (ISO timestamp, requestId) |
| Unknown path `/nope` | 404 standard error JSON from the gateway |
| Explicit routes (review change, 2026-11-05; 3 identity + 3 booking instances) | `/api/v1/ping` → booking 200; `/api/v1/auth/ping` → identity 200, 6 calls rotate over 3 instances; `/api/v1/bookings` → routed to booking (404, proxied: `transfer-encoding: chunked`); `/api/v1/bokings`, `/api/v1/unknown`, `/api/v1/admin/users`, `/internal/ping`, `/actuator/env` → gateway's own 404 (`content-length`). Booking-down check left to the owner review (B3) |
| Databases | `booking_db` owner `booking`, `identity_db` owner `identity` |
| Two booking instances (`--scale booking-service=2`) | both registered UP; 8 pings → **4 / 4** split (round robin) |

### Spikes

| Spike | Result |
|---|---|
| S1 OpenFeign on Boot 4 | **Pass.** `GET /api/v1/ping/identity` → booking calls identity through Eureka + Feign → identity's instance ID |
| S2 Resilience4j (Feign circuit breaker) | **Pass.** identity stopped → 3 calls returned the fallback in ~1.0 s each (1 s connect timeout to the stale instance); recovered automatically ~20 s after identity restarted |
| S3 Gateway circuit breaker | **Pass.** booking stopped → `503 SERVICE_UNAVAILABLE` standard JSON in 3.4 s (after 2 GET retries) |
| S4 Jackson 3 | **Pass.** timestamps serialize as ISO-8601 UTC strings (`2026-11-05T10:15:30Z`) in unit tests and in the running services |
| S5 Artifact names for M1.2 | On Boot 4.1.2 BOM: Flyway 12.4.0, PostgreSQL driver 42.7.14, Testcontainers 2.0.5 (exact artifact IDs to confirm when added in M1.2) |
| S6 Config client fail fast + retry | Fail fast works; **no in-app retry** without `spring-retry` + `spring-boot-starter-aspectj`. Owner decision: no new dependencies, rely on container restart with backoff; verified that identity-service recovered on its own once the config server returned. PLAT-07 updated |

### Problems found and fixed during the milestone

1. Boot 4 moved `ErrorWebExceptionHandler` to `org.springframework.boot.webflux.error` (compile error, fixed).
2. With `spring.cloud.config.enabled=false`, a `configserver:` import fails to resolve, so tests could not start. Fixed with an overridable import `${CONFIG_IMPORT:configserver:…}`; tests set an optional empty location.
3. Config Server with a Git `file:` URI works inside the mounted folder (lock files, checkouts, fetch from GitHub) and failed on the read-only mount. Dev now uses the `native` backend; prod keeps Git (D-33).
4. Gateway answered 503 "No servers available" for up to a minute after start (30 s registry fetch, 35 s load-balancer cache). Dev now refreshes both every 5 s (config repo `application-dev.yml`).
5. Routed responses had two `X-Request-Id` headers. The gateway now adds it only when the service didn't.

### Decisions settled
D-20 fixed config-server URL; D-21 Resilience4j via Spring Cloud CircuitBreaker works on Boot 4; D-22 gateway WebFlux; D-27 thin servlet-only `common-web`; D-33 native backend in dev, fail fast + container restart.

### Open issues for later milestones
- booking-service does not yet forward `X-Request-Id` on Feign calls (Feign request interceptor, M1.9).
- Gateway logs Hibernate Validator deprecation warnings (`@Valid` on containers) from Spring Cloud Gateway's own classes; harmless, upstream.
- Spring Cloud LoadBalancer warns that its default cache is for development; decide on Caffeine (new dependency) before production (M1.10).

## M1.2 Booking schema, constraints and seed data — Done (2026-11-05)

**Built:** Flyway `V1__baseline` (btree_gist), `V2__core_schema` (member, category, bookable_type, country_time_zone, venue, venue_opening_hours, bookable, bookable_opening_hours, booking; every constraint named; both exclusion constraints), `V3__seed_country_time_zones` (418 pairs, 247 countries, generated by `scripts/generate-country-zones.py` from IANA tzdata **2026b** = the JDK's tzdb version), `V4__seed_catalog` (5 categories, 8 bookable types, 5 fictional venues with weekly hours, 30 bookables incl. 12 library seats; IronWorks in `Europe/London`). JPA entities and repositories (`catalog`, `booking`, `member`). `ddl-auto: validate` and `open-in-view: false` in the service's local `application.yml`. Failsafe enabled in the parent POM. Datasource in the config repo (placeholders only) and Compose.

**Dependencies added (approved):** `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql`; test: `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter` (all BOM-managed; Flyway 12.4.0, PostgreSQL driver 42.7.14, Testcontainers 2.0.5).

### Automated tests

| Command | Result |
|---|---|
| `cd backend && ./mvnw -B verify` | BUILD SUCCESS; **45 tests, 0 failures, 0 errors, 0 skipped**: Surefire 16 (common-web 8, config-server 1, discovery-server 1, api-gateway 2, identity-service 2, booking-service 2) + Failsafe 29 (booking-service) |

What the 29 integration tests execute (real `postgres:16` Testcontainer, plain SQL, each failure asserts SQLSTATE **and** constraint name):

| Class | Tests | Covers |
|---|---|---|
| `MigrationIT` | 4 | V1–V4 applied; btree_gist installed; both exclusion constraints exist by name; no generated (unnamed) constraint names |
| `BookingConstraintsIT` · bookable overlap | 5 | same range → `ex_booking_bookable_overlap` (BR-10); partial overlap vs CONFIRMED rejected; back-to-back allowed (BR-12); CANCELLED/EXPIRED don't block; other bookable free |
| `BookingConstraintsIT` · buffer | 3 | trainer 10:00–10:45 + 15 min: start at 10:45 rejected, at 11:00 allowed (A-08); member may book elsewhere at 10:45 (buffer is the bookable's) |
| `BookingConstraintsIT` · member overlap | 3 | seat + room same time → `ex_booking_member_overlap` (BR-11); across categories (room + court); cancelled doesn't block |
| `BookingConstraintsIT` · row checks | 8 | HELD without / CONFIRMED with `expires_at` → `ck_booking_held_expires`; end before start; zone of another country → `fk_venue_country_zone`; opens ≥ closes; weekday 8; slot override 20; min > max override |
| `SeedDataIT` | 6 | 5 categories with defaults; 8 types with table 2.3 values (via JPA repositories); 5 venues, IronWorks in London; 4 rooms + 12 seats; every venue has hours, clinic closed Sunday; every seeded zone is a valid `java.time.ZoneId` |

### Checks that the tests and settings actually bite

| Experiment | Result |
|---|---|
| Renamed `address_line` → `address_lines` in `Venue` (reverted) | startup fails: `Schema validation: missing column [address_lines] in table [venue]`, so `ddl-auto: validate` is active |
| Changed the bookable range from `'[)'` to `'[]'` in V2 (reverted) | exactly `backToBackAllowed_BR12` and `startAfterBufferAllowed_A08` fail |
| Applied V1–V4 with `psql -v ON_ERROR_STOP=1` on a throwaway `postgres:16` | all four apply; 8 types, 30 bookables, 34 opening-hour rows, 418 zones |

### Compose stack

| Check | Result |
|---|---|
| `docker compose up -d --build --wait booking-service` | healthy; log: `booking-db-pool` started, Flyway applied V1–V4 to `jdbc:postgresql://postgres:5432/booking_db`, Hibernate validated |
| Run as non-superuser | `btree_gist` and all 10 tables owned by `booking` (btree_gist is a trusted extension, PostgreSQL 13+) |
| `--scale booking-service=2` | instance 1 migrated, instance 2: `Schema "public" is up to date` (Flyway's lock serializes concurrent starts) |

### Decisions settled
- `ddl-auto: validate` (Flyway alone changes the schema) and `open-in-view: false`, in the local `application.yml` (CLAUDE.md §7.1 updated).
- Bookable override columns get the same CHECKs as the type (ARCHITECTURE.md §5.3 note).
- Venue countries: four in India (`Asia/Kolkata`), IronWorks in `GB` / `Europe/London`.
- Country list from IANA `zone.tab` (one row per country and zone) rather than `zone1970.tab` (which shares zones between countries).

### Open issues for later milestones
- The readiness check does not include the database (Boot default); decide in M1.9 whether `db` belongs in readiness.
- `BookingServiceApplicationTest` now needs Docker (it starts the Testcontainer) although it is a `*Test`; rename to `*IT` if you want the naming rule strict (renames need your approval).

## M1.3c Token checks in the gateway and booking-service — Built, awaiting owner review (2026-11-06)

**Built:**
- api-gateway: Spring Security (reactive) with `common-web`'s `AccessTokenVerification` wrapped as a `ReactiveJwtDecoder`. Public: `/api/v1/auth/**`, `/api/v1/ping/**`, `/actuator/health/**`, `/actuator/info`, `/fallback/**`; every other path (unknown ones included) needs a valid token. 401 in the standard error JSON (`UNAUTHENTICATED`, `TOKEN_EXPIRED`). No role checks at the gateway; the `Authorization` header is forwarded unchanged.
- booking-service: resource-server starter (switches on `common-web`'s verification) and `config/SecurityConfig`: ping and health public, `/api/v1/admin/**` → ADMIN, everything else signed in. `member/service/MemberProvisioner.ensure` (`INSERT … ON CONFLICT DO NOTHING`), called from hold creation in M1.5.
- `common-web`: `spring-boot-starter-webmvc` is now optional (services declare it themselves), so the gateway stays reactive; `isExpired` moved to `AccessTokenVerification` and is shared by both error handlers.
- **Bug fixed (from M1.3b):** Spring's Bearer filter checked a token sent to public paths too, so an expired access token sent along with `/api/v1/auth/refresh` got `401 TOKEN_EXPIRED` and blocked the refresh. The gateway and identity-service now ignore the `Authorization` header on `/api/v1/auth/**`. Found by the gateway mutation check below.
- Docs: D-38 (and D-27 amended), REQUIREMENTS v0.8 (FR-ROLE-01), ARCHITECTURE §16, TECH_STACK, IMPLEMENTATION_PLAN M1.3 tests, CLAUDE.md §15.1, README, review guide, learning file.

**Dependencies added (approved 2026-11-06, option A):** api-gateway: `common-web`, `spring-boot-starter-security-oauth2-resource-server`. booking-service: `spring-boot-starter-security-oauth2-resource-server`. Both BOM-managed; no new library. `./mvnw -pl platform/api-gateway -am dependency:tree` shows no webmvc or Tomcat in the gateway.

### Test results

`cd backend && ./mvnw -B verify` on 2026-11-06: **BUILD SUCCESS**, 114 tests, 0 failures, 0 skipped.

| Module | Surefire (`*Test`) | Failsafe (`*IT`) |
|---|---|---|
| common-web | 20 | — |
| config-server / discovery-server | 1 / 1 | — |
| api-gateway | 2 (`GatewayErrorHandlingTest`, now sends a token to reach the 404) | 8 (new `GatewayTokenCheckIT`) |
| identity-service | 10 | 32 (new `RefreshFlowIT.staleAccessTokenIgnored_FRAUTH10`) |
| booking-service | 2 (unknown-path test now sends a token) | 38 (new `BookingSecurityIT` 7, `MemberProvisionerIT` 2) |

What the new tests execute (behavior): gateway forwards a valid token unchanged to a stub upstream (JDK `HttpServer`); no token, garbage, tampered, foreign key, unknown `kid`, no `kid` → 401 `UNAUTHENTICATED`; expired → 401 `TOKEN_EXPIRED`; auth paths and ping pass without a token and with a stale one; `/me` needs a token; health public. booking: no token / foreign key → 401, expired → `TOKEN_EXPIRED`, member token passes (404, no endpoint yet), MEMBER on `/api/v1/admin/bookings` → 403, ADMIN → 404 (passed security), health and ping public. Member row: created once, second call no change; 10 parallel calls → exactly one creator and one row.

**Repeated runs (CLAUDE.md §8.3):** `MemberProvisionerIT` run 20 times with `-am`: **20 of 20 passed**.

**Mutation checks (all reverted):**
- Gateway `.anyExchange().authenticated()` → `permitAll()`: fails `missingTokenRejected_FS14` and `meNeedsToken`. The bad- and expired-token tests still passed, which showed Spring checks presented tokens even on public paths, and led to the refresh bug fix above (new failing test first, then the fix).
- booking: removing `hasRole("ADMIN")` fails only `memberOnAdminPathForbidden_FS16`.
- Removing ` ON CONFLICT (id) DO NOTHING` fails both `MemberProvisionerIT` tests (duplicate key).

**Through the Compose stack** (`docker compose up -d --build --wait`, all six healthy):

| Check | Result |
|---|---|
| `/categories` without a token / with `x.y.z` | 401 `UNAUTHENTICATED` from the gateway |
| `/categories` with Alice's token | first two calls 503 (gateway routing not ready right after the restart, known), then 404 from booking-service (security passed) |
| `/admin/bookings` Alice / admin | 403 `FORBIDDEN` / 404 |
| `/me` Alice; `/ping`; `/actuator/health` | 200 (id 2, MEMBER); 200; 200 |
| refresh with cookie + headers + a bad `Authorization` header | 200 |
| booking-service called directly inside the Docker network, no token | 401 (it does not rely on the gateway) |
| identity-service stopped: `/categories` / `/admin/bookings` with Alice's token | 404 / 403: booking still verifies tokens locally (RES-07); `/me` → 503 fallback |

### Not covered yet
- `MemberProvisioner` is not called from a request until hold creation (M1.5).
- No real admin endpoint yet; the 403 is proven on the URL rule.
- CORS and browser preflight at the gateway (M1.6); service tokens for `/internal/**` (M1.9).

### Open issues for later milestones
- PROJECT.md §6 says roles are "checked early at the gateway"; the gateway checks only the token (D-38). PROJECT.md unchanged by rule; tell the owner.

## M1.3b Refresh tokens — Done (2026-11-06)

**Built:**
- identity-service: Flyway `V3__refresh_token` (`pk_refresh_token`, `fk_refresh_token_user`, `uq_refresh_token_hash`, `ix_refresh_token_expires`, `ix_refresh_token_user`). One row per login; only the SHA-256 of the 256-bit token is stored.
- `POST /api/v1/auth/refresh`: one conditional `UPDATE … SET expires_at = now + 7d WHERE token_hash = ? AND expires_at > now`; 1 row → new access token and the same cookie with a fresh `Max-Age`; 0 rows, no cookie or a disabled user → `401 REFRESH_TOKEN_INVALID`.
- `POST /api/v1/auth/logout`: deletes the row, clears the cookie, `204` (also without a cookie).
- Refresh and logout require `Origin` = `gridreserve.web.frontend-origin` and `X-GridReserve-Client: web`, else `403 FORBIDDEN` (FR-AUTH-12).
- Login and register start a session through one `AuthService.startSession`; register now answers `201 {accessToken, …}` + cookie (D-36). Expired rows are deleted at every login (no cleanup job).
- Cookie `gr_refresh`: `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age` 7 days, `Secure` by default (off only in `identity-service-dev.yml`).
- Config repo: `gridreserve.auth.refresh` in `identity-service.yml`, new `identity-service-dev.yml`, `gridreserve.web.frontend-origin` in `application-dev.yml` / `application-prod.yml`.

**Decisions settled (owner, 2026-11-06):** D-37 refresh tokens not rotated (one row per login, sliding 7 days, logout deletes it; rotation, families, reuse detection and the cleanup job dropped; FS-39 withdrawn); D-36 register starts a session; D-35 sign-in with Google and GitHub as M1.3d. REQUIREMENTS v0.7, ARCHITECTURE, CLAUDE.md, IMPLEMENTATION_PLAN and TECH_STACK updated. PROJECT.md still says "rotating refresh token" (unchanged by rule; the REQUIREMENTS change note records the owner decision).

**Dependencies added:** none.

### Test results

`cd backend && ./mvnw -B verify` on 2026-11-06: **BUILD SUCCESS**, 96 tests, 0 failures, 0 skipped.

| Module | Surefire (`*Test`) | Failsafe (`*IT`) |
|---|---|---|
| common-web | 20 | — |
| config-server / discovery-server / api-gateway | 1 / 1 / 2 | — |
| identity-service | 10 (new: `RefreshTokensTest` 2, `ClientCheckTest` 2) | 31 (`AuthFlowIT` 15, new `RefreshFlowIT` 14, `UserSchemaIT` 2, new `RefreshTokenSchemaIT` 2) |
| booking-service | 2 | 29 |

What the new tests execute (behavior, not smoke): cookie attributes; only the hash stored; expired rows deleted at login; refresh returns a working access token and slides the expiry; 7 idle days → 401; missing/unknown token → 401; disabled user → 401 with the expiry unchanged; foreign/missing `Origin` or missing header → 403 and the session untouched; 10 parallel refreshes → 10 × 200; an access token is not accepted as a refresh token and vice versa; logout deletes the row, clears the cookie and the copied cookie stops working; logout on one device keeps the other; logout is idempotent; V3 constraints and indexes by name.

**Repeated runs (CLAUDE.md §8.3):** `RefreshFlowIT` (includes the parallel-refresh test) run 20 times with `-am`: **20 of 20 passed**, 14 tests each. (A first loop without `-am` used a stale `common-web` jar from `~/.m2` and failed at startup in all 20 runs; that was the command, not the code.)

**Mutation check:** removing ` AND t.expiresAt > :now` fails only `expiredAfterSevenIdleDays_FRAUTH10`; removing the enabled filter fails only `disabledUserRefused_FRAUTH13`; removing the CSRF check from refresh fails only `foreignCallerForbidden_FS40`. All reverted.

**Through the Compose stack** (`docker compose up -d --build --wait`, all six healthy; Flyway applied V3 to `identity_db`):

| Check | Result |
|---|---|
| Right after the identity restart | gateway answered `503` (fallback) for a few seconds until its circuit breaker closed; then `200`. Known slow routing after a restart. |
| login (alice) | 200; `Set-Cookie: gr_refresh=…; Path=/api/v1/auth; Max-Age=604800; HttpOnly; SameSite=Strict` (no `Secure` in dev) |
| refresh with Origin + header | 200, `expiresIn` 900, token works on `/me` (Alice, id 2); same cookie attributes |
| refresh without the headers | 403 `FORBIDDEN` |
| logout | 204, `Max-Age=0`; refresh afterwards → 401 `REFRESH_TOKEN_INVALID` |
| register Dave | 201 + cookie; its access token works on `/me` |
| `identity_db.refresh_token` | Alice's row gone, Dave's row: 64-character hash, lifetime 7 days |

### Not covered yet
- No browser test of the cookie flow (frontend from M1.6).
- `Secure` cookies only take effect over HTTPS (deployment, M1.10).
- The gateway and booking-service do not check tokens yet (M1.3c).

### Open issues for later milestones
- The frontend must call refresh on start and on `401 TOKEN_EXPIRED` with `X-GridReserve-Client: web` (M1.6).

---

## M1.3a Identity core — Done (2026-11-06)

**Built:**
- `common-web`: shared access-token verification (`AccessTokenVerification`, `JwtProperties`, `PemKeys`, `JsonSecurityErrorHandler`, `CommonWebSecurityAutoConfiguration`). A token is accepted only with a known `kid`, a valid RS256 signature, not expired by the injected `Clock` (30 s skew), our issuer and audience, and `sub` + `role`. Security failures use the standard error JSON: `401 UNAUTHENTICATED`, `401 TOKEN_EXPIRED`, `403 FORBIDDEN`. Active only in services that declare the resource-server starter (optional dependency), so booking-service is unchanged until M1.3c.
- identity-service: Flyway `V1__user_schema` (`app_user`; `pk_app_user`, `uq_app_user_email`, `ck_app_user_email_lower`, `ck_app_user_role`), `V2__seed_demo_users` (ADMIN + 2 MEMBERs, BCrypt cost 10). `POST /api/v1/auth/register` (always MEMBER, email lower-cased, duplicate → `409 EMAIL_TAKEN` decided by the unique constraint), `POST /api/v1/auth/login` (access token only; identical `401 INVALID_CREDENTIALS` and one BCrypt check for unknown email, wrong password and disabled account), `GET /api/v1/me`. RS256 signing with `kid` (`NimbusJwtEncoder`); startup fails if `JWT_PRIVATE_KEY` does not match the published public key. Stateless security; `ddl-auto: validate`, `open-in-view: false`.
- Config repo: `gridreserve.jwt` (issuer, audience, dev public key `dev-2026-11`) in `application.yml`; identity datasource and signing settings in `identity-service.yml` (placeholders only). Compose passes the identity DB credentials and `JWT_PRIVATE_KEY`; `.env.example` explains how to generate a key pair.

**Dependencies added (approved 2026-11-06):** identity-service: `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql`; test: `spring-boot-starter-security-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`. common-web: `spring-boot-starter-security-oauth2-resource-server` (optional). All BOM-managed: Spring Security 7.1.2, Nimbus JOSE 10.9.1. (`spring-boot-starter-oauth2-resource-server` is deprecated in Boot 4 in favour of the `-security-` name used here.)

**Decisions settled:** D-34 JWT library = Spring Security resource server + Nimbus JOSE instead of jjwt; D-19 service-to-service auth = service JWTs signed by identity, built in M1.9; M1.3 split into three PRs (a, b, c). CLAUDE.md, TECH_STACK.md, ARCHITECTURE.md and IMPLEMENTATION_PLAN.md updated.

### Test results

`cd backend && ./mvnw -B verify` on 2026-11-06: **BUILD SUCCESS**, 76 tests, 0 failures, 0 skipped.

| Module | Surefire (`*Test`) | Failsafe (`*IT`) |
|---|---|---|
| common-web | 20 (9 new in `AccessTokenVerificationTest`, 3 in `JsonSecurityErrorHandlerTest`) | — |
| config-server / discovery-server / api-gateway | 1 / 1 / 2 | — |
| identity-service | 6 (`IdentityServiceApplicationTest` 2, `JwtSigningConfigTest` 4) | 15 (`AuthFlowIT` 13, `UserSchemaIT` 2) |
| booking-service | 2 | 29 (unchanged) |

| Class | What it proves |
|---|---|
| `AccessTokenVerificationTest` | valid token accepted with `ROLE_MEMBER`; expired → `token_expired`; 29 s past expiry still accepted, 31 s not; tampered payload, wrong key under a known `kid`, unknown `kid`, missing `kid`, wrong issuer, wrong audience, missing `role` all rejected; PEM with literal `\n` |
| `JsonSecurityErrorHandlerTest` | 401 `UNAUTHENTICATED` (with `WWW-Authenticate: Bearer`, no reason leaked), 401 `TOKEN_EXPIRED`, 403 `FORBIDDEN`, all with path and request ID |
| `JwtSigningConfigTest` | matching key pair builds the signer; mismatched private key and unpublished `kid` stop startup; the key never appears in messages or `toString` |
| `AuthFlowIT` · registration | 201 MEMBER even when the body says `"role":"ADMIN"`; email lower-cased; no hash or password in the response; other-case duplicate → 409; **10 parallel registrations of one email → exactly one 201, nine 409**; short password and invalid email → 400 field errors; 40 × `é` (80 bytes) → 400, not 500 |
| `AuthFlowIT` · login | 15-minute RS256 token with `kid`, `sub` = user ID, `role`, no email or name; wrong password and unknown email give identical status, code and message; all three demo accounts log in, admin token says ADMIN |
| `AuthFlowIT` · `/me` | 200 with the token's user; no token → 401 with request ID; expired → `TOKEN_EXPIRED`; foreign key → 401; validly signed token for a missing user → 401 |
| `UserSchemaIT` | demo accounts seeded with `$2a$10$` hashes; the database rejects a non-lower-case email (`ck_app_user_email_lower`) |

### Checks that the tests bite

| Experiment | Result |
|---|---|
| Changed the constraint name in `ConstraintViolations` to `uq_typo` (reverted) | exactly `duplicateEmailInOtherCase_FRAUTH02` and `concurrentRegistrationsOneWinner_FRAUTH02` fail (duplicate becomes a 500) |

### Compose stack (dev key generated locally; private key only in `infra/.env`)

| Check | Result |
|---|---|
| `docker compose up -d --build --wait` | all six containers healthy; identity log: `identity-db-pool` started, Flyway applied V1–V2 to `identity_db` |
| `POST /api/v1/auth/login` (alice) through the gateway | 200, token of 538 characters |
| `GET /api/v1/me` with / without token | 200 `{"id":2,"email":"alice@gridreserve.test",…,"role":"MEMBER"}` / 401 `UNAUTHENTICATED` with `WWW-Authenticate: Bearer` |
| wrong password | 401 `INVALID_CREDENTIALS` |
| register `Carol.Demo@Example.test`, then `carol.demo@example.test` | 201 (stored lower-cased) / 409 `EMAIL_TAKEN` |

### Not covered yet
- The gateway does not check tokens yet, and booking-service has no security (M1.3c, FS-14/16 at both layers).
- No refresh token or cookie; login returns only the access token (M1.3b).
- `/internal/**` is still open inside the network (D-19, M1.9).
- The login timing equality (dummy BCrypt hash) is a code-review item, not measured by a test.

### Open issues for later milestones
- Social login: **decided 2026-11-06** (D-35): sign-in with Google and GitHub as M1.3d, after M1.3c; link by verified email only. Register starts a session (D-36) in M1.3b. REQUIREMENTS v0.6, ARCHITECTURE §5.4/§16/§19, IMPLEMENTATION_PLAN M1.3, TECH_STACK and CLAUDE.md updated.
- Possible extension: let accounts created through Google or GitHub set a password (A-29).

---

## Open issues

- None.
