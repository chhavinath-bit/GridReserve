# Progress log

The latest verified checkpoint is the most recent milestone below marked **Done** with recorded results and a matching commit on `main` (CLAUDE.md §1).

| Milestone | Status | Date | Commit |
|---|---|---|---|
| M0.1 Environment inspection | Done | 2026-11-05 | `3f44318` (recorded in the initial commit) |
| M0.2 Repositories | Done | 2026-11-05 | `3f44318` app repo, `a346654` config repo; results recorded in the next commit |
| M0.3 Confirm decisions and assumptions | Done | 2026-11-05 | `74ea039` (merge of PR #1) |
| M1.1 Platform skeleton, CI and Boot 4 spikes | Done (owner reviewed; [review guide](reviews/M1.1-review.md)) | 2026-11-05 | `9d636e0` (merge of PR #2); config repo `ae47541` (merge of PR #1); CI on `main` green, 16 tests |
| M1.2 Booking schema, constraints and seed data | Built; **awaiting owner review** ([review guide](reviews/M1.2-review.md)) | 2026-11-05 | branch `m1.2-booking-schema`; config repo branch `m1.2-booking-datasource` |

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

## M1.2 Booking schema, constraints and seed data — Built, awaiting owner review (2026-11-05)

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

---

## Open issues

- None.
