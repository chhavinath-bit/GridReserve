# CLAUDE.md

Standing instructions for this repository. Read this first in every session, then follow "Starting a session" (section 1).

## 0. Project, owner and source documents

GridReserve: one booking platform for many kinds of venues (study rooms and library seats, sports courts, parking, clinics, gyms). A user picks a **Category**, then a **Venue**, then books a **Bookable** in a time slot. Built as **microservices** on Spring Boot 4 / Spring Cloud with a React frontend.

**Who I am and what this is for.** I am a frontend-leaning engineer (React/TypeScript, some Node/GraphQL at work) learning Java/Spring Boot. This is a portfolio project to get hired as a Java full-stack developer. I must be able to explain every part in an interview, so explain design decisions briefly as you go and prefer clear code over clever code.

**Job applications** happen in parallel. The project is never a reason to delay applications.

**Source documents** (read the relevant parts before working on a milestone):

| File | What it is | Authority |
|---|---|---|
| @PROJECT.md | Product spec v0.3 | Highest for product scope. If this file and PROJECT.md disagree, ask me. |
| REQUIREMENTS.md | Testable requirements with IDs (`BR-10`, `PLAT-02`, `RES-03`, …) | Tests cite these IDs |
| TECH_STACK.md | Technologies, why, alternatives | Stack details |
| ARCHITECTURE.md | Services, schemas, transactions, concurrency, resilience, decisions (`D-xx`, §19), known inconsistencies (§20) | Design |
| DESIGN_SYSTEM.md | UI tokens (colors, type, spacing), components, screen layouts, accessibility rules | All frontend work |
| IMPLEMENTATION_PLAN.md | Ordered milestones (`M0.1`, `M1.5`, …) with tests and done criteria | Order of work |
| docs/PROGRESS.md | Log of completed milestones and actual test results (created in M0.2) | Latest verified checkpoint |
| `gridreserve-config` repo | Central configuration (`dev`/`prod`) served by the Config Server | Runtime configuration |

If documents disagree with each other, do not pick one silently: point out the conflict and ask.

## 1. Starting a session: resume from the latest verified checkpoint

Do this at the start of every session, before proposing or changing anything:

1. Read `docs/PROGRESS.md`. The **latest verified checkpoint** is the most recent milestone marked done with recorded test output **and** a matching merge commit on `main`. If `docs/PROGRESS.md` does not exist, Phase 0 is not complete: start at IMPLEMENTATION_PLAN.md M0.1.
2. Check the repository state (application repo, and the config repo if it is checked out): `git status`, `git branch --show-current`, `git log --oneline -15`, `git tag --list 'phase-*'`.
3. If there is uncommitted work or an unmerged milestone branch, **do not assume it works**. Inspect the diff, tell me what you found, and ask whether to continue it, finish it or discard it. Never discard work without my approval.
4. Verify the checkpoint is still green before building on it: start the stack (`docker compose up -d`), then run the backend, frontend and (if they exist) system test commands in section 13. Report the actual results. If something that PROGRESS.md says passed now fails, stop and report it; fixing it comes before new work.
5. Find the next milestone in IMPLEMENTATION_PLAN.md and propose its plan (objective, tasks, files, tests). **Wait for my agreement before coding.**

## 2. How to work

- **Phase by phase, milestone by milestone.** Work strictly in the order of IMPLEMENTATION_PLAN.md. Do not start a milestone until the previous one is done, and do not start a phase until the previous phase is complete (section 3).
- **Agree the plan first.** Do not start coding until the plan for that milestone is agreed.
- **Ask before** installing packages or adding dependencies, adding a new service or module, deleting files, renaming or moving files, changing already-applied migrations, changing CI, deployment or the config repository's structure, or making any large change. Explain what and why, then wait for my approval.
- **Inspect before editing.** Read a file (and the code that calls it) before changing it. Look for existing helpers (including `libs/common-web`) before writing new ones. Match the surrounding style.
- **Stay in scope.** Change only what the current milestone needs. No unrelated rewrites, reformatting, renames or "while I'm here" refactors. If you notice something worth fixing, mention it and add it to `docs/PROGRESS.md` "Open issues" instead of doing it.
- **Small, testable steps.** After each change, run the relevant tests (section 10).
- **Explain as you go**, briefly: the design decision, why, and the alternative you didn't take. This is for interview preparation.
- **Pending decisions** (section 15.2) are not settled. When work reaches one, stop and ask.

### Never claim a test passed without running it

- Report **actual** results only: the command you ran and the real outcome (counts of tests run, passed, failed, skipped, and the relevant failure output).
- If you could not run something (Docker not running, a service not starting, network, missing tool), say so plainly, and say the result is **unverified**.
- Never write "should pass", "tests pass" or "CI is green" based on reasoning. Never mark a milestone done in `docs/PROGRESS.md` without recorded test output.
- If a test fails, show the failure, explain the cause you found, and fix it or ask. Do not delete, skip, `@Disabled` or weaken a failing test to make the build green without my approval.

## 3. Phases (nothing is dropped; work strictly in order)

0 Environment and repositories → 1 Core engine and platform (EXCLUSIVE) → 2 Capacity model → 3 Waitlist and offers → 4 Redis → 5 Payments and refunds (mocked) → 6 Memberships and venue managers → 7 Recurring and no-shows → 8 Queue, notifications, polish.

- Do not start a phase until the previous one is complete: **tests passing, CI green, deployed, README updated**, and tagged `phase-N-complete`.
- Phase 1 only builds the EXCLUSIVE model (rooms, library seats, courts, bays, doctors, trainers), but the data model must not block adding CAPACITY later.
- Inside Phase 1, follow IMPLEMENTATION_PLAN.md: a thin platform skeleton first (M1.1), a thin vertical slice early (M1.6), correctness proven (M1.2, M1.5, M1.8) before UI polish (M1.13), resilience in its own milestone (M1.9). Idempotency and audit (M1.12) are the last engine milestone; if Phase 1 runs long they move to the start of Phase 2. They are not dropped.
- New services appear only in their phase: payment (P5), membership (P6), notification (P8).
- No real payment or personal data, ever. Demo data is fictional.

## 4. Vocabulary (use consistently)

- **Category, Venue, Bookable.** Never use the word "resource" in code, API paths, UI or docs. (Framework-owned names that cannot be changed, such as Maven's `src/main/resources` folder, are the only exception.)
- Each bookable has a **bookable type** (Room, Seat, Hall, Court, Bay, Doctor, Trainer, Class) that belongs to a category and supplies the UI label, booking model and default rules. Never hard-code labels.
- Booking models: **EXCLUSIVE** (one booking per time range, exclusion constraint) and **CAPACITY** (N seats per session, atomic seat claim).
- An **active** booking is `HELD` or `CONFIRMED`. A **hold** is a `HELD` booking with `expires_at`.
- Service names: `config-server`, `discovery-server`, `api-gateway`, `identity-service`, `booking-service`, later `payment-service`, `membership-service`, `notification-service`.

## 5. Fixed stack (do not change or add without asking)

- **Frontend:** React + TypeScript, Vite, Tailwind CSS, TanStack Query, React Router, **shadcn/ui components on Radix primitives** (DESIGN_SYSTEM.md). Tests: Vitest + React Testing Library; Playwright for end-to-end tests. Supporting UI packages (D-UI-3: cva, clsx, tailwind-merge, lucide-react, tw-animate-css, sonner, react-day-picker, TanStack Table, self-hosted Inter) and axe (D-UI-4) are listed but **need approval at install time**.
- **Backend:** Java 21, **Spring Boot 4.x**, Maven multi-module (wrapper `./mvnw`), Spring Security, JWT with jjwt (RS256), Spring Data JPA (Hibernate), Spring Boot Actuator + Micrometer.
- **Microservice platform:** Spring Cloud release train compatible with the Boot version (verify on the official compatibility table): Config Server (Git backend), Netflix Eureka, Gateway, LoadBalancer, CircuitBreaker with **Resilience4j** (Retry, RateLimiter, Bulkhead, CircuitBreaker, TimeLimiter). Service-to-service calls with **Spring Cloud OpenFeign** (`@FeignClient`, resolved through Eureka and load-balanced), wrapped in Resilience4j via an adapter class.
- **Database:** PostgreSQL 16, **one database per service** on one server; migrations with Flyway per service.
- **Redis:** Phase 4 only. Never the source of truth.
- **Events between services:** transactional outbox + HTTP push (Phase 5+). No broker.
- **Tests:** JUnit 5, Mockito, Testcontainers (AssertJ comes with `spring-boot-starter-test`); a stub HTTP server for Feign client tests (WireMock, **pending approval D-28**).
- **Delivery:** Docker + Docker Compose, GitHub Actions.
- **Not allowed unless I ask:** Kafka, RabbitMQ or any message broker, Spring Cloud Bus, Kubernetes, rules engines, extra frameworks, Lombok, MapStruct, real payment SDKs, more services than listed in section 4. Any library not listed here needs my approval first, including the "pending approval" candidates in the docs (ArchUnit, springdoc, WireMock, distributed tracing, a load-test tool).

## 6. Architecture rules

### 6.1 Core rules (unchanged)

- **PostgreSQL is the source of truth.** Double-booking is prevented by an exclusion constraint on (bookable, time range) for active bookings, not only by Java checks. Member overlap is prevented by a second exclusion constraint on (member, time range) (decision Q2: never, in any category). Constraint violations map to `409 Conflict`.
- **Seat claims (Phase 2) must be atomic in the database**; never read-then-write in application code.
- **Category differences are configuration and seed data** (bookable types, labels, slot length, duration, buffer, cutoff, quotas, offer TTL), not separate code paths and not a rules engine. No `if (category == CLINIC)` or `if (type == SEAT)` anywhere.
- **Store timestamps in UTC** (`timestamptz`, Java `Instant`). Each venue stores an ISO country code and an IANA time zone that must belong to that country; rules and display use the venue's zone.
- **DTOs at the API boundary**, never JPA entities. Validate all input. One consistent JSON error format in every service and the gateway: `timestamp, status, code, message, path, requestId, fieldErrors[]` (REQUIREMENTS.md API-05).
- **The application must still work correctly if Redis is down.**
- **Background jobs are safety nets.** Correctness never depends on a job running on time (FR-EXP-04).

### 6.2 Microservice rules

- **The booking core stays in `booking-service`**: catalog, bookable types, countries/zones, availability, bookings, holds, audit, idempotency, sessions (P2), waitlist (P3), recurring (P7), walk-in queue (P8). Never split these into separate services; their guarantees need one transaction.
- **A booking decision is always one booking-service database transaction.** No transaction spans services; no 2PC/XA.
- **Each service owns its database.** Never read or write another service's database. Cross-service references are plain IDs (for example `booking.member_id` = identity user ID).
- **No remote call inside a database transaction.** Enrichment calls happen before or after; reliable side effects go through the outbox (Phase 5+).
- **Every remote call** goes through a load-balanced client (`lb://service-id`) with timeouts and resilience policies (section 9). A call without a timeout is a bug.
- **Booking must keep working when identity-service is down** for users who already hold a valid access token: tokens are verified locally with the public key; booking-service keeps local `member` rows for locking.
- **All client traffic goes through `api-gateway`.** Only the gateway (and the static frontend) are public in `prod`. `/internal/**` and actuator endpoints are never routed by the gateway.
- **Correctness never depends on which instance handles a request.** Any service may run several instances; no in-memory state for business decisions.
- **Shared code** lives only in `libs/common-web` and stays thin: error format, request ID, JWT verification, event envelope, outbox/inbox helpers. No entities, repositories or business rules in shared code.

## 7. Code organization and conventions

### 7.1 Repository layout

```
~/Developer/project/
├── GridReserve/                 application monorepo → github.com/chhavinath-bit/GridReserve
│   ├── backend/                 all Java; Maven runs here (./mvnw)
│   │   ├── pom.xml              parent: Boot 4 parent, Spring Cloud BOM, versions, modules
│   │   ├── platform/{config-server,discovery-server,api-gateway}/
│   │   ├── services/{identity-service,booking-service}/   (+ payment P5, membership P6, notification P8)
│   │   │     each: pom.xml, Dockerfile,
│   │   │           src/main/java/com/gridreserve/<service>/{config,common/exception,<feature>/{controller,dto,service,entity,repository,mapper},client}
│   │   │           src/main/resources/application.yml (name + config import only), db/migration/ (this service's Flyway SQL)
│   │   │           src/test/java/... (mirrors main)
│   │   └── libs/common-web/
│   ├── frontend/                src/{app,api,features/<feature>,components/{ui,patterns},styles,lib}, e2e/
│   ├── infra/                   compose.yaml, .env (git-ignored), .env.example, postgres/init-databases.sh
│   ├── scripts/refresh-config.sh
│   ├── .github/workflows/
│   ├── docs/PROGRESS.md
│   └── *.md                     project docs
└── gridreserve-config/          config repo (sibling, not nested, not a submodule) → github.com/chhavinath-bit/gridreserve-config
      application.yml, application-{dev,prod}.yml, {service}.yml, {service}-{dev,prod}.yml
```

- Backend module paths in the docs (`platform/…`, `services/…`, `libs/…`) are relative to `backend/`.
- Config Server backends: in `dev` the `native` backend reads the sibling clone mounted by Compose at `/config-repo` (the checked-out branch, uncommitted edits included); in `prod` the Git backend reads `CONFIG_REPO_URI` (the public GitHub URL, no token).

```
```

### 7.2 Modules inside services

- `booking-service` modules: `common`, `catalog`, `availability`, `booking`, `member`, `audit`, `client` (calls to other services), later `capacity`, `waitlist`, `cache`, `outbox`, `recurring`, `queue`. `identity-service` modules: `user`, `auth`, `refresh`, `security`, later `venuemanager`. See ARCHITECTURE.md §3.
- Inside a service, a module uses another only through its public **service** and **DTOs**; no cyclic dependencies (lower modules define SPIs such as `SlotReleasedListener`); only the `booking` module writes the `booking` table.
- **Feature, then layer.** Each feature package (`catalog`, `booking`, …) has the folders it needs from: `controller` (REST controllers), `dto` (request/response records + Bean Validation), `service` (`@Transactional` logic, rules, ownership checks), `entity` (JPA entities and enums), `repository` (Spring Data interfaces and explicit SQL), `mapper` (hand-written entity ↔ DTO mapping; no MapStruct), `job` (scheduled jobs). Service-wide: `config/` (security, clock, schedulers, resilience), `common/exception/` (error mapping), `client/` (Feign clients + resilience adapters + fallbacks).
- A service's client-side DTOs for another service are owned by the caller (tolerant reader); do not share API DTO classes between services.

### 7.3 Java and Spring Boot conventions

- Java 21: `record` for DTOs and value objects; `final` fields; constructor injection only.
- Names: classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`; DTOs end with `Request` / `Response` / `Dto`; tests end with `Test` (unit) or `IT` (integration).
- Time: inject `java.time.Clock`; never call `Instant.now()` directly. Pass the clock's `now` into SQL as `:now`. Never use `LocalDateTime` for stored moments.
- Errors: throw `DomainException(ErrorCode.X, …)`; map DB constraint violations **by constraint name** in one place; an unknown constraint is a 500 and a bug. Translate downstream errors into the caller's own error or fallback; never pass them through blindly.
- Configuration: typed, validated `@ConfigurationProperties` (`@Validated`) for anything tunable; mark refreshable groups clearly; no `@Value` sprinkled through business code.
- Spring Boot 4 specifics (Jackson 3, modular starters, etc.): check the official docs instead of copying Boot 3 examples.
- `spring.jpa.open-in-view=false`; `ddl-auto=validate`. Watch for N+1 queries.
- Logging: SLF4J, parameterized messages, `requestId` in MDC and forwarded on outbound calls. No passwords, tokens, emails or names in logs.
- No `synchronized`, Java locks or static mutable state for business correctness.
- Keep methods short and readable. Comment *why*, not *what*.

### 7.4 TypeScript and React conventions

- `strict` TypeScript; no `any`. API types in `src/api/types.ts` mirror backend DTOs.
- All HTTP goes through `src/api/client.ts` (in-memory access token, single-flight refresh on `401 TOKEN_EXPIRED`, `X-GridReserve-Client` header, error parsing into `ApiError`). No `fetch` in components. Never store tokens in `localStorage` or `sessionStorage`.
- Server state in TanStack Query with keys from `src/api/queryKeys.ts`; routing with React Router; no Redux.
- Feature folders under `src/features/<feature>`; shared UI in `src/components/ui`.
- Every data view has loading, error and empty states; 503/504 show "temporarily unavailable, retry". Times shown in the venue's zone with `Intl.DateTimeFormat`.
- Mutations: never auto-retry on `4xx`; booking retries reuse the same `Idempotency-Key`.
- **Follow DESIGN_SYSTEM.md.** Calm, precise SaaS look: neutral surfaces, one indigo accent, light and dark themes from the same tokens.
- **Tokens only:** never hex/rgb values or arbitrary colors (`bg-[#…]`) in components or features. A new color is a new token with a measured contrast ratio added to DESIGN_SYSTEM.md §3.4.
- **Reuse before you build:** screens are composed from `src/components/ui` (shadcn/ui, owned by us) and `src/components/patterns` (GridReserve components such as `SlotGrid`, `HoldPanel`, `BookingCard`, `StatusBadge`, `DataTable`). New looks are new variants (class-variance-authority), not `className` patches in feature code. `components/*` never import from `features/*`.
- Every slot and booking state has its own look **and** a non-color cue (label, icon or pattern); times use tabular numbers and the venue's zone.
- Accessibility (WCAG 2.2 AA): visible focus ring, full keyboard path (including the slot grid with arrow keys), live-region announcements for holds and conflicts, `prefers-reduced-motion` respected.
- New or changed components appear in the dev-only gallery route `/dev/components` in all states, light and dark.

## 8. Database, migrations, transactions and concurrency

### 8.1 Flyway migration rules

- Each service's schema changes **only** through its own Flyway files in `services/<service>/src/main/resources/db/migration`, named `V<n>__<description>.sql`.
- **Never edit a migration that has been committed to `main`.** Fix forward. Ask me before any change that drops or rewrites data.
- Constraints, exclusion constraints, checks and indexes are written explicitly and **named** (`ex_booking_bookable_overlap`, `uq_app_user_email`, `ix_…`), because error mapping depends on the names.
- Seed data is versioned in migrations (categories, bookable types, country/time-zone list from IANA data, demo users with BCrypt hashes, never plaintext).
- Every migration is exercised by Testcontainers tests (Hibernate validates the schema at startup).

### 8.2 Transaction rules

- Isolation: PostgreSQL default **READ COMMITTED**. Do not switch to SERIALIZABLE without asking.
- `@Transactional` on **service** methods; one business transaction per request (plus short idempotency bookkeeping transactions).
- No remote calls inside a transaction (section 6.2).
- Status changes are **conditional UPDATEs** naming the expected current state; 0 rows means refused.
- Audit rows and outbox rows are written in the same transaction as the change.

### 8.3 Concurrency safety rules

- Correctness comes from **database constraints, conditional updates and row locks only**. Never "check in Java, then write" for anything scarce (slots, seats, credits, limits, refresh-token rotation).
- Per-member limits: lock the local `member` row (`SELECT … FOR UPDATE`) before counting.
- **Global lock order** in booking-service: venue row (only when it is locked: hold creation `FOR SHARE`, venue deactivation `FOR UPDATE`) → bookable or session row → `member` rows (ascending id) → `booking` rows → waitlist / idempotency / audit / outbox rows. Every new multi-row flow follows this order; explain it in the PR.
- Background workers and outbox relays use `FOR UPDATE SKIP LOCKED` and must be safe with several instances.
- Thread pools, bulkheads and HTTP connection pools are bounded and named. No unbounded executors or queues; no `@Async` on the default executor.
- Every concurrency guarantee (REQUIREMENTS.md CONC-*) has a real-PostgreSQL test with a `CountDownLatch` start gate; CONC-10 runs through the gateway with two booking-service instances. Run new concurrency tests at least 20 times locally before merging; report the result.

## 9. Resilience, configuration and discovery rules

- **Patterns** (Resilience4j, defaults in the config repo, REQUIREMENTS.md §13): timeouts on every call; **CircuitBreaker** per downstream and per gateway route; **Bulkhead** per downstream; **Retry only for idempotent operations** (GETs, event deliveries, idempotency-keyed commands), exponential backoff with jitter, never on 4xx; **RateLimiter** (per instance) for rate-sensitive downstreams. Decorator order: Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead.
- **Never retry `POST /bookings` server-side** (gateway or service). The client retries with the same `Idempotency-Key`.
- **Every protected call has an explicit, tested fallback.** No silent swallowing of errors.
- **Two kinds of rate limiting, both kept:** Resilience4j RateLimiter (per instance, protects downstreams) and the Redis rate limiter at the gateway (Phase 4, distributed, protects the system from clients). Do not replace one with the other.
- **Configuration** comes from the Config Server (`gridreserve-config` Git repo) with profiles `dev` and `prod`; the `test` profile disables the config client and Eureka. **Never put secrets in the config repo**; use environment variables.
- **Refresh:** refreshable settings are `@ConfigurationProperties` or `@RefreshScope` beans; apply changes with `scripts/refresh-config.sh` (Eureka lookup → `POST /actuator/refresh` per instance). Booking rules are database data, not configuration. Datasources, ports and keys are not refreshable.
- **Discovery:** services register with Eureka under their service ID; callers use `lb://service-id`; never hard-code instance hosts or ports in code.
- **Startup:** fail fast if the Config Server is unreachable; the container platform restarts with backoff (no in-app retry, PLAT-07). Health-based ordering in Compose, no fixed sleeps. New apps imported from config must keep the `${CONFIG_IMPORT:configserver:…}` form so tests can run without a Config Server.

## 10. Testing requirements

- **Run tests after every change** and before every commit; report actual output (section 2).
- Backend unit tests (JUnit 5 + Mockito) for rules, services and fallbacks; integration tests (`*IT`) per service against **real PostgreSQL via Testcontainers**. **Never use H2** or another in-memory database.
- Feign clients are tested against a stub HTTP server (WireMock once approved, D-28): timeout, 5xx, open circuit, bulkhead rejection, no retry on 4xx, fallback output.
- System tests run the Compose stack: the main journey (Playwright), CONC-10, resilience scenarios (FS-32..38), config refresh.
- Test names or `@DisplayName` cite the requirement ID they cover (e.g. `rejectsOverlappingHold_BR10`).
- Time-based behavior uses a fixed or movable `Clock`; no `Thread.sleep` for timing. System tests wait on health checks, not sleeps.
- Concurrency tests assert exact outcomes **and** the resulting database state.
- Frontend: Vitest + React Testing Library for the API client (incl. refresh), error mapping, guards and components; Playwright for end-to-end flows.
- Never weaken, skip or delete a test to make a build pass without my approval. Flaky tests are fixed, not retried.
- A milestone is not done until its tests from IMPLEMENTATION_PLAN.md pass locally **and** in CI.

## 11. Security requirements

- **Never commit secrets, tokens or passwords** to either repository. Use environment variables and `infra/.env.example` with placeholders only (`infra/.env` is git-ignored). **Never print secret values** (including in logs, test output or chat).
- Seeded demo accounts are demo-only and documented in the README.
- Passwords: BCrypt; 8–72 characters; never logged or returned.
- **Authentication:** identity-service signs **RS256** access tokens (15 min) with a private key from an environment variable; the gateway and every service verify them with the public key (distributed via config; not secret). The browser keeps the access token **in memory**. A **refresh token** (opaque, stored hashed, rotated on every use, family revoked on reuse, revoked on logout and on user disable) lives in an `HttpOnly`, `Secure` (prod), `SameSite=Strict` cookie scoped to `/api/v1/auth`. Refresh and logout require a matching `Origin` and the `X-GridReserve-Client` header (CSRF defence). Details: REQUIREMENTS.md FR-AUTH-09..13.
- Roles are enforced by Spring Security in **every** service (the gateway also pre-checks tokens); **ownership** is checked in the owning service's service layer. A member asking for another member's booking gets `404`.
- Admins can cancel any booking but not book on behalf of members; no endpoint accepts a target user ID for booking creation.
- `/internal/**` and actuator endpoints require service or admin credentials (mechanism D-19) and are never exposed through the gateway. Only the gateway's `health` is public.
- CORS allows only the configured frontend origin (at the gateway).
- Validate all input; parameterized SQL only; error responses never contain stack traces, SQL or class names.
- No real personal, patient or payment data anywhere. Phase 5 payments are fully mocked (HMAC-signed mock webhooks).

## 12. Redis usage rules (Phase 4 only)

- Do not add Redis, or code that uses it, before Phase 4.
- Redis is **never the source of truth** and never decides a booking. No Redis locks for booking correctness.
- Uses: (1) availability cache in booking-service (cache-aside, short TTL, per-bookable generation counter bumped after commit); (2) distributed rate limiting at the gateway (Spring Cloud Gateway Redis rate limiter: login by IP, booking creation by user). Build the cache first. See ARCHITECTURE.md §14.
- Keys: our keys use the prefix `gr:v1:…`; no personal data in keys (hash IPs and emails); every key has a TTL except generation counters.
- Every Redis call has a short timeout, a CircuitBreaker and a fallback: cache → read PostgreSQL; rate limiting → fail open with a warning. Redis is excluded from readiness checks.
- A test with Redis stopped must prove that results stay correct.

## 13. Commands

Rows marked ✓ were run successfully in M1.1 (2026-11-05); the others are planned. Verify a command when it first becomes real and keep this list accurate.

| Task | Command | Notes |
|---|---|---|
| Start the whole stack (dev) ✓ | `cd infra && docker compose up -d --build --wait` | Postgres (per-service DBs), config server (`native`, mounts `../../gridreserve-config`), discovery, gateway, services (+ Redis in P4); reads `infra/.env`. Routing can take ~15 s after all are healthy |
| Start only infrastructure | `cd infra && docker compose up -d postgres config-server discovery-server` | Then run services from the IDE/Maven |
| Two booking instances ✓ | `cd infra && docker compose up -d --scale booking-service=2 --wait` | Load-balancing and CONC-10 demos |
| Stop ✓ | `cd infra && docker compose down` | Add `-v` only with my approval (deletes data) |
| Eureka dashboard ✓ | `http://localhost:8761` (JSON: `curl -H 'Accept: application/json' localhost:8761/eureka/apps`) | |
| Smoke test ✓ | `curl -i localhost:8080/api/v1/ping` | Temporary M1.1 endpoint through the gateway |
| Run one service locally | `cd backend && ./mvnw -pl services/booking-service spring-boot:run -Dspring-boot.run.profiles=dev` | Needs config server + Postgres |
| Build and test everything ✓ | `cd backend && ./mvnw -B verify` | Docker must be running for Testcontainers |
| Test one module ✓ | `cd backend && ./mvnw -pl services/booking-service -am test` | `-am` also builds `common-web` |
| Single test ✓ | `cd backend && ./mvnw -pl services/booking-service -am test -Dtest=BookingServiceApplicationTest -Dsurefire.failIfNoSpecifiedTests=false` | |
| Refresh config on all instances | `scripts/refresh-config.sh [service-id]` | Needs admin/service credential from env |
| Install frontend deps | `cd frontend && npm ci` | |
| Run frontend | `cd frontend && npm run dev` | Vite proxies `/api` to the gateway on `:8080` |
| Frontend type check / unit tests / build | `cd frontend && npm run typecheck` · `npm test` · `npm run build` | |
| End-to-end tests | `cd frontend && npm run e2e` | Playwright; needs the Compose stack |
| Full stack from images | `cd infra && docker compose --profile full up --build` | From M1.10 |

## 14. Git workflow and documentation

### 14.1 Git

- Two repositories: **`GridReserve`** (this folder, application monorepo) and **`gridreserve-config`** (configuration), both public on GitHub (decision B-1). On 2026-11-04 this folder was not inside any Git repository; M0.2 initializes it. Do not run Git commands against any parent directory.
- Default branch `main`, always green. One branch per milestone: `m<phase>.<n>-<short-name>`. Config changes are PRs in the config repo, linked from the milestone.
- Commit in small steps with clear messages: imperative subject, body explaining *why* when it isn't obvious. Commit only when I ask or when the agreed milestone plan says so.
- **No AI attribution:** never add `Co-Authored-By: Claude` trailers or "Generated with Claude Code" lines to commits or pull requests. Commits are authored by me only.
- Before committing: tests run and reported, `git status` reviewed, no `.env`, secrets, build output or unrelated files staged.
- Merge to `main` only after CI is green. Never force-push `main`. Never rewrite published history.
- Tag `phase-N-complete` (and `v0.N.0`) only when the phase's definition of done is met.

### 14.2 Documentation maintenance

| When | Update |
|---|---|
| Every milestone | `docs/PROGRESS.md`: milestone, date, what changed, **actual test commands and results**, open issues. `README.md`: how to run and test what now exists. |
| A command changes | Section 13 of this file |
| A pending decision is made | ARCHITECTURE.md §19 (move to settled, with date), section 15 of this file, affected REQUIREMENTS.md items |
| A requirement changes | REQUIREMENTS.md (bump version); keep IDs stable, never reuse a removed ID |
| Config structure changes | `gridreserve-config/README.md` (what is refreshable) |
| End of a phase | README (features, architecture diagram, demo accounts, live URL, how to run concurrency and resilience tests), CHANGELOG, tag |

- Docs describe what **is**, not what is hoped. Mark anything not yet built as planned.
- Do not edit PROJECT.md without my explicit request.

## 15. Decisions

### 15.1 Decided

- Cancellation cutoff: 2 hours before start. Max 3 active future bookings per member (per category). Both configurable per category.
- Admins can cancel any booking but not book on behalf of members.
- Deactivating a bookable or venue is a **soft close** (2026-11-05, replaces the old 409 rule): it stops new bookings at once (`422 BOOKABLE_INACTIVE`, hidden from members); existing HELD and CONFIRMED bookings are never cancelled or changed by it; Phase 3 waitlists for it are closed. Bookables and venues are never deleted.
- Hold duration: 5 minutes. Booking horizon: 7 days. Opening hours per weekday (one interval per day, no row = closed); no booking crosses local midnight in Phase 1.
- (2026-11-05, M0.3) Confirmed: tokens 15 min / refresh 7 days; refresh cookie `SameSite=Strict` + Origin and `X-GridReserve-Client` checks; another member's booking → 404; manual key rotation with `kid`; resilience defaults as in REQUIREMENTS.md §13; 256 MB heap per service; hold-expiry job every 30 s; limit counts HELD + CONFIRMED not yet ended; idempotency keys 24 h; waitlist promotion inside the releasing transaction (D-03).
- Bookable types and defaults (slot / min / max / buffer): Study Room 30 / 30 / 3 h / 0; Study Seat 60 / 60 / 4 h / 0; Sports Court 60 / 60 / 2 h / 0; Parking Bay 60 / 60 / 12 h / 0; Clinic Doctor 30 / 30 / 30 / 0; Gym Trainer 15 / 45 / 45 / 15 min; Study Hall and Gym Class are CAPACITY (Phase 2).
- A member can never hold two overlapping active bookings, in any category.
- Payments (Phase 5): fully mocked behind a `PaymentProvider` interface.
- Waitlist offers (Phase 3): 10 minutes by default, per category.
- Recurring conflicts (Phase 7): skip the conflicting dates and report them; book the rest.
- Repository: own Git repo in this folder; monorepo with Maven modules; separate config repo. Router: React Router. Frontend tests: Vitest + RTL + Playwright. Token storage: in-memory access token + httpOnly refresh-token cookie.
- (2026-11-05) Repository layout: `backend/` (platform, services, libs), `frontend/`, `infra/`; config repo as a sibling folder, read from the local clone in `dev` and from GitHub in `prod`; packages feature-then-layer (`controller`, `dto`, `service`, `entity`, `repository`, `mapper`, `config`); service-to-service calls with Spring Cloud OpenFeign; `gh` CLI installed and logged in.
- (2026-11-04) Microservices from the start; booking core in one service; database per service; outbox + HTTP push (no broker); Config Server with `dev`/`prod`; per-instance refresh script; Spring Boot 4; Resilience4j patterns; venue country + zone; library seats EXCLUSIVE, reading halls CAPACITY.
- Keep the build host-agnostic (Dockerfiles + environment variables + config server); the deploy host is chosen before the first Phase 1 deployment.

### 15.2 Pending decisions: ask before acting

Do not assume an answer for these. When a milestone reaches one, present options with a recommendation and wait.

| Item | Needed by | Where described |
|---|---|---|
| Service-to-service authentication (D-19) | M1.3 / M1.9 | ARCHITECTURE.md §16 |
| Deploy host, backups, rollback; must fit ~5 JVMs + PostgreSQL (D-15) | M1.10 | IMPLEMENTATION_PLAN.md §3 |
| UI supporting packages (D-UI-3) and axe accessibility checks (D-UI-4) | M1.6 / M1.13 | DESIGN_SYSTEM.md §10 |
| Load-test tool (D-13b) | M1.14 | ARCHITECTURE.md §19 |
| Remaining `D-xx` items (e.g. D-05, D-07, D-24 tracing, D-25 membership credits, D-26) | Phase listed in ARCHITECTURE.md §19 | ARCHITECTURE.md §19 |
| WireMock for Feign client tests (D-28) | M1.9 (first Feign client) | ARCHITECTURE.md §19 |
