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
| M1.3c Token checks in the gateway and booking-service | Done (owner reviewed; [review guide](reviews/M1.3c-review.md)) | 2026-11-06 | `2792e21` (merge of PR #8); no config repo change; CI on `main` (`2792e21`) green, 36 + 78 tests |
| M1.3d Sign-in with Google and GitHub | **Dropped** (owner, 2026-11-06): too much work for its value; optional extension, design kept in the docs (D-35 withdrawn). M1.3 is complete with a, b, c. | 2026-11-06 | — |
| M1.4 Catalog, countries and availability | Done (owner reviewed; [review guide](reviews/M1.4-review.md)) | 2026-11-06 | `2e8e88f` (merge of PR #10); no config repo change; CI on `main` (`2e8e88f`) green; local `verify` on `main` 2026-11-07: 156 tests, 0 failures |
| M1.5 Booking engine core (hold, confirm, cancel) | Done (owner reviewed; [review guide](reviews/M1.5-review.md)) | 2026-11-07 | `5eb2abc` (merge of PR #11); no config repo change; CI on `main` (`5eb2abc`) green; local `verify` on `main` 2026-11-07: 188 tests, 0 failures |
| M1.6 Thin vertical slice (frontend through the gateway) | Done (owner reviewed in the browser; [review guide](reviews/M1.6-review.md)) | 2026-11-07 | `472a561` (merge of PR #12); no config repo change; CI on `main` (`472a561`) green; local on `main` 2026-11-07: backend `verify` 188 tests, 0 failures; frontend typecheck and lint clean, 36 tests passed, build OK |

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
- Social login: decided 2026-11-06 (D-35) as M1.3d, then **dropped the same day** by the owner (too much work for its value); kept as an optional extension, not planned. REQUIREMENTS v0.9. Register starts a session (D-36) in M1.3b. REQUIREMENTS v0.6, ARCHITECTURE §5.4/§16/§19, IMPLEMENTATION_PLAN M1.3, TECH_STACK and CLAUDE.md updated.
- Possible extension: let accounts created through Google or GitHub set a password (A-29).

---

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

## M1.3c Token checks in the gateway and booking-service — Done (2026-11-06)

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

## M1.4 Catalog, countries and availability — Done (2026-11-06)

**Built (booking-service):**
- Catalog reads (FR-CAT-01..06, FR-CAT-14): `GET /categories` (with bookable types; EXCLUSIVE types first, then by label), `/venues?categoryId=` and `/venues/{venueId}/bookables` (paginated, API-04), `/venues/{id}` (zone + weekly hours), `/bookables/{id}` (effective rules, own or venue hours), `/countries`, `/countries/{code}/time-zones`. Members get only active items (inactive → 404); admins may pass `includeInactive=true`.
- `RuleResolver` → `EffectiveRules`: bookable override → type default → category; own weekly hours replace the venue's (FR-CAT-04/05).
- Availability (FR-AV-01..07): `LocalSlotGrid` (local-time grid anchored at opening, DST gap skipped, repeated hour once), `AvailabilityService` (whole local days today … today + 6, else `422 BEYOND_HORIZON`; past dates all PAST; closed weekday `closed: true`; CAPACITY → `422 WRONG_BOOKING_MODEL`), one occupancy query per request through `booking/service/BookingOccupancy` (CONFIRMED, or HELD with `expires_at > now`; overlap with `[start, occupied_until)`).
- common-web: a malformed or missing query parameter → `400 VALIDATION_FAILED` naming the field (before, the catch-all handler answered 500).
- Docs: D-39, REQUIREMENTS v0.10 (FR-AV-04 clarified, FR-AV-07 added, API-04 exception), CLAUDE.md §15.1, README section "Browsing the catalog and availability", review guide, learning file.

**Plan deviation:** the plan named the CAPACITY error `BOOKING_MODEL_NOT_SUPPORTED`; the existing catalog code `WRONG_BOOKING_MODEL` (BR-09) means the same and is used instead.

**Dependencies added:** none.

### Test results

`cd backend && ./mvnw -B verify` on 2026-11-06: **BUILD SUCCESS**, 156 tests, 0 failures, 0 skipped.

| Module | Surefire (`*Test`) | Failsafe (`*IT`) |
|---|---|---|
| common-web | 22 (new: type mismatch, missing parameter) | — |
| config-server / discovery-server | 1 / 1 | — |
| api-gateway | 2 | 8 |
| identity-service | 10 | 32 |
| booking-service | 14 (new: `LocalSlotGridTest` 8, `RuleResolverTest` 4) | 66 (new: `CatalogIT` 16, `AvailabilityIT` 12; `BookingSecurityIT.memberTokenAccepted` now expects 200 because `/categories` exists) |

What the new tests execute (behavior): grid sizes for 15/30/60-minute slots, anchoring at opening, UTC vs local, all-day hours, DST spring-forward (23 slots, no 01:00) and fall-back (24, no duplicates); resolver layers and own hours replacing venue hours; categories with labels and CAPACITY nulls; venue page and detail with hours (24:00); inactive venue and bookable hidden from members, visible to admins; bad page parameters → 400; bookable page of 16 with labels; effective rules for the trainer (15/45/45/15); Dr. Mehta's own hours; countries sorted, one-zone and multi-zone countries, unknown → 404; ≤ 3 SQL statements for a bookable page; availability day of 17 slots with zone and rules; CONFIRMED → TAKEN; buffer blocks the following slot; expired hold FREE, live hold TAKEN, cancelled FREE; no member data in the response; past date all PAST; horizon edge 200/422; closed Sunday; own hours (Monday closed, Tuesday 8 slots); inactive → 404; CAPACITY → 422; bad and missing date → 400.

**Mutation checks (all reverted):** dropping ` and b.expiresAt > :now` fails `expiredHoldIsFree_FRAV03`; `hasOwnHours = false` fails `ownHoursReplaceVenueHours`; a visibility filter that is always true fails `CatalogIT.inactiveBookableHidden_FRCAT10` and `AvailabilityIT.inactiveBookable_FRCAT10`.

**First runs that failed (fixed, not weakened):** `RuleResolverTest` (Mockito answers 0 for `Integer`; the test now stubs `null`); `CatalogIT.categoriesWithTypes` (types were ordered by seed ID, which the seed's join does not keep; the service now sorts EXCLUSIVE first, then label).

**Through the Compose stack** (`docker compose up -d --build --wait`): gateway answered 503 for about 15 s after the restart (known routing delay), then: categories with types (Study: Room, Seat, Hall); Sports venues → Sunrise Turf; its bookables Court A, Court B (label Court); Court A rules 60/60/120/0, zone Asia/Kolkata; tomorrow's availability 17 slots, first 06:00 local = 00:30Z FREE; today + 7 → 422 `BEYOND_HORIZON`; `date=nope` → 400 with `fieldErrors[date]`; IN → `["Asia/Kolkata"]`; no token → 401.

### Not covered yet
- Booking (hold, confirm, cancel) is M1.5; TAKEN is proven with inserted rows.
- Admin create/edit/deactivate endpoints (M1.7).
- On fall-back night the repeated hour's slot lasts two real hours (documented in `LocalSlotGrid`).

### Open issues for later milestones
- Whether a whole booking (duration + buffer) fits is checked at booking time (M1.5), not in availability; the frontend picker (M1.6) should only offer starts where `minDuration` fits.

---

## M1.5 Booking engine core: hold, confirm, cancel — Done (2026-11-07)

**Built (booking-service):**
- `POST /bookings` (hold, `201` + `Location`, `HELD` for 5 minutes), `POST /bookings/{id}/confirm`, `POST /bookings/{id}/cancel` (optional `reason`), `GET /bookings/me` (newest start first; `status`, `from`, `to`, paged), `GET /bookings/{id}` (FR-BK-01..07). The owner is the token's `sub`; another member's booking is `404` (FR-ROLE-04); admins read and cancel any booking (`cancelledBy: ADMIN`) and book only for themselves.
- Hold transaction (`booking/service/BookingTransactions`): venue and bookable `FOR SHARE` (`CatalogService.lockForBooking`), member `FOR UPDATE` (`member/service/MemberLock`), rules BR-01, 06, 09, 05 in the BR-14 order (`BookingRules`), run-out holds in the way expired in one `ORDER BY id FOR UPDATE` statement (FR-EXP-02), insert with `occupied_until = end + buffer`. The member row is created first in its own short transaction (`MemberProvisioner`, D-38).
- Confirm and cancel are conditional UPDATEs; a run-out hold is saved as `EXPIRED` before the refusal (`noRollbackFor`), so confirm answers `409 HOLD_EXPIRED` and cancel `409 INVALID_STATE`; other wrong states `409 INVALID_STATE` (BR-13).
- `common/exception/ConstraintViolations`: `ex_booking_bookable_overlap` → `409 SLOT_TAKEN`, `ex_booking_member_overlap` → `409 MEMBER_OVERLAP`; unknown constraints stay 500. New codes `SLOT_TAKEN`, `MEMBER_OVERLAP`, `HOLD_EXPIRED`, `INVALID_STATE`, `BOOKABLE_INACTIVE`, `START_IN_PAST`.
- Deadlock retry (D-11): Spring Framework 7 `@Retryable` on `BookingService` (`config/RetryConfig` enables it), 3 attempts, only on `PessimisticLockingFailureException`.
- Docs: D-11 settled (ARCHITECTURE §19.1, CLAUDE.md §15.1), REQUIREMENTS v0.11 (FR-BK-03/04 clarified), IMPLEMENTATION_PLAN M1.5 file names, README "Book with curl" and status, review guide, learning file.

**Plan deviations:** the plan named the mapper `ConstraintViolationMapper` and a `common/tx` package; the code follows identity-service's existing `ConstraintViolations` name, and the retry needs only `config/RetryConfig` plus the annotation. Hold duration is a constant (`HOLD_DURATION`, A-04), not configuration.

**Dependencies added:** none (`@Retryable` is in `spring-context` 7, already present).

### Test results

`cd backend && ./mvnw -B verify` on 2026-11-07 (after the last code commit): **BUILD SUCCESS**, 188 tests, 0 failures, 0 skipped.

| Module | Surefire (`*Test`) | Failsafe (`*IT`) |
|---|---|---|
| common-web | 22 | — |
| config-server / discovery-server | 1 / 1 | — |
| api-gateway | 2 | 8 |
| identity-service | 10 | 32 |
| booking-service | 22 (new: `BookingRulesTest` 3, `BookingRetryTest` 3, `ConstraintViolationsTest` 2) | 90 (new: `BookingLifecycleIT` 24) |

What the new tests execute (behavior): hold 201 with `Location`, names from the catalog, `expiresAt` ≈ now + 5 min, the same timestamps on a later read, member row created; same slot → `SLOT_TAKEN` with one active row; back-to-back allowed (BR-12); trainer buffer blocks 10:45 and allows 11:00; same member elsewhere → `MEMBER_OVERLAP` (FS-02); another member's run-out hold and the member's own run-out hold don't block and end `EXPIRED` (FS-21, FR-EXP-02); BR-01 (reversed times, missing fields with `fieldErrors`), BR-05, BR-06 (bookable and venue), BR-09, BR-06 before BR-05, unknown bookable 404; confirm once then `INVALID_STATE`; confirm after expiry `HOLD_EXPIRED` with the row saved `EXPIRED` (FS-08); admin can't confirm another's hold (404); member cancel with reason then `INVALID_STATE`; admin cancel of a confirmed booking frees the slot at once (CXL-03/05); cancel after expiry; reason > 200 → 400; another member's booking 404 for read, confirm and cancel (FS-15); owner and admin read; `/me` order, paging, status and time filters, bad status 400. Unit: rule order; retry once after a deadlock, give up after 3, never retry `SLOT_TAKEN`; constraint names mapped, unknown name kept.

**Mutation checks (all reverted):** `@Transactional` without `noRollbackFor` on confirm fails `confirmAfterExpiry_FS08`; no stale-hold sweep fails `staleHoldOnBookableDoesNotBlock_FS21` and `ownStaleHoldDoesNotBlock_FREXP02`; not reading the `23P01` name fails both BR-10 tests and `memberOverlapRejected_FS02` (500); an ownership filter that is always true fails `othersBookingNotFound_FS15` and `onlyOwnerConfirms`.

**First runs that failed (fixed, not weakened):** overlaps answered 500: Hibernate does not extract the constraint name for exclusion violations (`23P01`), so `ConstraintViolations` reads it from PostgreSQL's message with Hibernate's template; `/bookings/me` answered 500: PostgreSQL could not type `? is null` for the time filters (`42P18`), fixed with `cast(:from as Instant)`.

**Through the Compose stack** (`docker compose up -d --build --wait booking-service`, demo accounts): alice holds Court B → 201, `Location: /api/v1/bookings/1`; bob same slot → 409 `SLOT_TAKEN`; availability shows 18:00 TAKEN, 17:00 and 19:00 FREE; bob reads alice's booking → 404; alice confirms → 200, again → 409 `INVALID_STATE`; `/me?status=CONFIRMED` → 1 item; admin cancels with a reason → 200 `cancelledBy: ADMIN`; bob holds the slot → 201 and cancels → `cancelledBy: MEMBER`; no token → 401. **Found here:** the 201 showed `createdAt` with nanoseconds and a later GET with microseconds (Linux clock vs PostgreSQL); hold times are now truncated to microseconds, and `holdCreated_FRBK01` moves the clock by 123 ns so it fails on any OS without the fix (checked).

### Not covered yet
- BR-02, 03, 04, 07, 08 and the cancellation cutoff (CXL-02..04) are M1.7; until then a hold outside opening hours or off the grid is accepted.
- No hold-expiry job (M1.7); run-out holds are cleaned up lazily (confirm, cancel, a blocking hold) and ignored by availability.
- Concurrency proofs (CONC-01..05, 09, 10) are M1.8; the deadlock retry is tested with a mocked deadlock only.
- Idempotency key and audit (M1.12); admin booking list (M1.11).

### Open issues for later milestones
- From M1.4: whether a whole booking (duration + buffer) fits the opening hours is BR-02/BR-04, now planned in M1.7 (not M1.5).

---

## M1.6 Thin vertical slice: frontend through the gateway — Done (2026-11-07)

**Built (`frontend/`, new):**
- Vite 8 + React 19 + TypeScript 6 (strict) + Tailwind 4 + TanStack Query + React Router; dev server on `:5173` proxies `/api` to the gateway.
- Design tokens (DESIGN_SYSTEM §3) as CSS variables for light and dark, applied before first paint; theme toggle (System / Light / Dark, the only thing in `localStorage`).
- Base components in the shadcn style on `radix-ui` (Button with loading, Input, Label, Select, Card, Badge, Skeleton, Alert, Toaster).
- Patterns: `AppShell`, `PageHeader`, `StatusBadge`, `DateStrip`, `SlotGrid` (roving tabindex, arrow keys, state in words and with the hatch for TAKEN), `HoldTimer` (announces at 1 minute and expiry), `ConflictNotice`, loading / error / empty states with the "temporarily unavailable" variant.
- `api/client.ts`: access token in memory only, `X-GridReserve-Client` header, single-flight refresh on `401 TOKEN_EXPIRED` with one retry, session end on failed refresh, standard error JSON → `ApiError` with one message per code (`api/errors.ts`), non-JSON 5xx and network errors → temporary codes.
- Pages: Login, Register (session restored on reload through the refresh cookie; `returnTo` limited to same-site paths; Formik + Yup validation mirroring identity-service's rules: required fields, email format, password 8–72 on register, name ≤ 100, no minimum on login; an invalid form sends no request; errors under each field, a summary and focus on the first wrong field; server field errors still shown under their field), Browse (category in the URL → venue cards), Venue (bookables grouped by type label; CAPACITY shown as "Sessions coming soon"), Slot picker (date strip for the horizon, grid in venue time, min/max-aware selection, Hold → countdown → Confirm / Release, 409 → notice + refetch, HOLD_EXPIRED → "Pick again"), My bookings (Upcoming / Past / Cancelled & expired; confirm or release a live hold, cancel with an inline confirmation).
- Dev-only gallery `/dev/components` (every component, light and dark side by side), left out of production builds.
- CI: new `frontend` job (Node 24 from `.nvmrc`; `npm ci`, typecheck, lint, test, build).
- Docs: D-UI-3 decided (DESIGN_SYSTEM §10, CLAUDE.md §15.1), CLAUDE.md §13 frontend commands verified, TECH_STACK versions pinned, README "Run the frontend" and status, review guide, learning file §10.

**Dependencies added (approved 2026-11-07):** runtime `react`, `react-dom` 19.3.1, `react-router` 8.5.1, `@tanstack/react-query` 5.107.1, `radix-ui` 1.8.1, `class-variance-authority` 0.7.1, `clsx` 2.1.1, `tailwind-merge` 3.7.0, `lucide-react` 1.70.0, `sonner` 2.0.8, `@fontsource-variable/inter` 5.3.0; dev `typescript` 6.0.3, `vite` 8.3.6, `@vitejs/plugin-react` 6.2.1, `tailwindcss` + `@tailwindcss/vite` 4.3.5, `tw-animate-css` 1.4.0, `vitest` 5.1.0, `jsdom` 31.0.0, `@testing-library/react` 16.3.3, `@testing-library/jest-dom` 7.0.1, `@testing-library/user-event` 14.6.8, `@types/react`, `@types/react-dom` 19.3.0, `@types/node` 26.6.6, `eslint` 10.14.0, `typescript-eslint` 8.72.1, `eslint-plugin-react-hooks` 7.1.1; later in M1.6, on the owner's request, `formik` 2.4.9 and `yup` 1.7.1 (runtime). npm 12 blocked `fsevents`' install script (optional macOS file watcher); not approved, not needed.

**Plan deviations:** base components were written by hand in the shadcn style instead of with the shadcn CLI (the CLI installs packages on its own; this keeps the approved list exact); `components.json` is present so the CLI can be used later. No `react-day-picker`: the date strip covers the 7-day horizon.

### Test results

`cd frontend` on 2026-11-07: `npm run typecheck` clean; `npm run lint` 0 problems; `npm test` **9 files, 36 tests passed**; `npm run build` succeeds (main bundle 626 kB, 197 kB gzipped, of which Formik + Yup add about 79 kB / 25 kB; gallery not included).

| File | Tests | What it executes |
|---|---|---|
| `api/client.test.ts` | 8 | token and client header sent; standard error JSON → `ApiError` with our message and `requestId`; HTML 502 → `SERVICE_UNAVAILABLE`, failed fetch → `NETWORK_ERROR`; two parallel `TOKEN_EXPIRED` → **one** refresh, both retried with the new token (5 calls); failed refresh → session ended once; no retry on 403/404/409; auth calls use `credentials: include` without the access token; refresh sends the client header |
| `lib/time.test.ts` | 5 | venue zone vs another zone; range format; "today" is the venue's date (20:00Z is tomorrow in Kolkata); month end; countdown |
| `features/availability/selection.test.ts` | 5 | minimum on first click, extend to max, restart beyond max; clear on reselect; not across TAKEN; trainer picks 3 × 15 min; refusal with a reason |
| `components/patterns/SlotGrid.test.tsx` | 3 | state names in words and `aria-pressed`/`aria-disabled`; only FREE selectable; Tab lands on the first free slot, arrows/Home/End move, Enter selects, one cell in the tab order |
| `components/patterns/HoldTimer.test.tsx` | 1 | countdown, one-minute announcement, expiry fired exactly once (fake timers) |
| `lib/bookingStatus.test.ts` | 3 | run-out HELD shown as EXPIRED; tab sorting; countdown from the hold's length despite a wrong device clock |
| `features/auth/schemas.test.ts` | 5 | register: valid form; every field required, spaces are not a name; email format; password 8 / 72 / 73; name 101 → error; login: required only, no minimum |
| `features/availability/SlotPickerPage.test.tsx` | 2 | after a confirm, picking another slot brings back "Hold slot" and sends the next hold, and the confirmed slot is no longer "held by you"; while a hold is live, another pick shows "Confirm or release your current hold first." |
| `features/auth/AuthForms.test.tsx` | 4 | login empty submit: no request, both messages, summary, `aria-invalid` + description, focus on Email; valid login sends trimmed email, wrong credentials shown for the form; register bad email + short password: no request, focus on Email; server field error (72 bytes) shown under Password |

Backend unchanged since M1.5 (local `verify` on `main` 188 tests, 0 failures, see the M1.5 row).

**Mutation checks (all reverted):** no `validationSchema` on the login form fails "an empty submit sends nothing"; no single-flight in `refreshSession` fails "share one refresh"; every slot selectable fails "selects free slots only" and "names every state in words"; no effective expiry fails "shows as EXPIRED" and the tab test.

**First runs that failed (fixed, not weakened):** TypeScript 6 rejects `baseUrl` (deprecated), removed; ESLint's React hooks rules found `Date.now()` read during render in three places (fixed with `useNow` and lazy state) and a `useCallback` the compiler can't keep (removed); the HoldTimer test showed that a timeout re-armed after each render can stall, so the timer now uses `setInterval`; one SlotGrid test had a wrong expectation (6 slots have no second row below the third), corrected; the schema test helper kept Yup's last message per field, while Formik shows the first (an empty password breaks "required" and "min 8"), corrected to match Formik; the gallery chunk was still emitted in production builds, now excluded.

**Found by the owner in the browser (fixed):** after confirming, the panel kept the confirmed card, so a newly selected slot had no "Hold slot" button and no request was sent; the confirmed slot also kept the "held by you" look. Now a new pick after a confirmed or expired booking resets the panel, "held by you" shows only for a live hold, and during a live hold a pick explains what to do. Covered by `SlotPickerPage.test.tsx` (failed before the fix).

**Through the real dev server and stack** (Vite on `:5173`, Compose stack): every module compiles; the browser's requests replayed with curl through the proxy (same origin, `Origin: http://localhost:5173`, `X-GridReserve-Client: web`, cookie jar): refresh without a cookie 401 → login sets the `HttpOnly` cookie on `/api/v1/auth` → `/me` Alice Demo → Sports → Sunrise Turf → Court A (label Court, Asia/Kolkata, 60/60/120, horizon 7) → tomorrow 17 slots → hold 201 → bob same slot 409 `SLOT_TAKEN` → confirm CONFIRMED → "reload": refresh with the cookie gives a new token, session restored → My bookings lists it → refresh without the client header 403 → cancel → logout 204, refresh afterwards 401. The hold answer from the old container still showed nanoseconds; after rebuilding booking-service from M1.5's code it shows microseconds, as fixed in M1.5. Routing after the rebuild took about 48 s.

### Not covered yet
- **No real browser run by Claude** (no headless browser on this machine; Playwright is M1.13): rendering, clicks, the visible countdown, keyboard use, both themes and the two-window race are for the owner's check (review guide §2).
- Playwright end-to-end tests (M1.13); admin screens (M1.11); `Idempotency-Key` on holds (M1.12).
- Bundle splitting (M1.13); mobile bottom sheet for the summary (M1.13).
- The top bar likely overflows at phone width (~460 px of content in one row); not fixed yet, owner to decide.

### Open issues for later milestones
- The gateway took up to ~48 s to route to a rebuilt booking-service (the README says ~15 s); worth measuring in M1.9 (Eureka and load-balancer cache intervals).

---

## Open issues

- None.
