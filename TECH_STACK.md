# GridReserve — Tech Stack

> Version 0.2 (draft). Based on [PROJECT.md](PROJECT.md) v0.3 and [REQUIREMENTS.md](REQUIREMENTS.md) v0.2.
> Requirement IDs (`BR-10`, `PLAT-02`, `RES-03`, …) refer to REQUIREMENTS.md.
> Changes from v0.1: modular monolith replaced by **microservices from the start** (owner decision 2026-11-04); Spring Boot 3 → **Spring Boot 4** with **Spring Cloud** (Config Server, Eureka, Gateway, LoadBalancer, CircuitBreaker/Resilience4j); `dev`/`prod` profiles; RS256 JWT with refresh-token cookie; React Router, Vitest, RTL and Playwright added. Nothing from v0.1 was removed (Redis cache and rate limiting, Flyway, Actuator/Micrometer, outbox all remain).
> Exact versions are pinned at scaffold time after checking the official compatibility tables; nothing here invents version numbers beyond the major lines.

## 1. Summary

| Layer | Technology | Responsibility | Phase |
|---|---|---|---|
| Language | Java 21 (LTS) | All backend services | P1 |
| Framework | **Spring Boot 4.x** (Spring Framework 7) | Every service: web, DI, config, validation, scheduling, transactions | P1 |
| Microservice platform | **Spring Cloud** release train matching Boot 4 (2025.1.x "Oakwood" at the time of writing) | Config, discovery, gateway, load balancing, circuit breakers | P1 |
| Central configuration | Spring Cloud Config Server + separate Git repo | `dev`/`prod` configuration for all services; `@RefreshScope` | P1 |
| Service discovery | Spring Cloud Netflix **Eureka** | Registry of running instances | P1 |
| Load balancing | Spring Cloud **LoadBalancer** | Client-side round robin across instances (`lb://…`) | P1 |
| API gateway | Spring Cloud **Gateway** (Server WebFlux) | Single entry point, routing, JWT pre-check, CORS, resilience filters, Redis rate limiting (P4) | P1 |
| Fault tolerance | **Resilience4j** via Spring Cloud CircuitBreaker | Retry, RateLimiter, Bulkhead, CircuitBreaker, TimeLimiter | P1 |
| Service-to-service HTTP | **Spring Cloud OpenFeign** (`@FeignClient`, load-balanced via Eureka) | Synchronous calls between services | P1 |
| Build | Maven (wrapper `./mvnw`), multi-module | Build all services from one parent POM | P1 |
| Database | PostgreSQL 16, **one database per service** on one server | Source of truth; exclusion constraints, atomic updates | P1 |
| Data access | Spring Data JPA (Hibernate) + explicit SQL where needed | Entities, repositories, transactions | P1 |
| Migrations | Flyway, per service | Versioned SQL schema and seed data | P1 |
| Security | Spring Security | Filter chains, roles, BCrypt, CORS | P1 |
| Tokens | Spring Security OAuth2 resource server + Nimbus JOSE (RS256) | Sign (identity) and verify (all) JWT access tokens (replaced jjwt, D-34) | P1 |
| Social sign-in | Spring Security OAuth2 client | Sign in with Google (OIDC) and GitHub (OAuth2) in identity-service (D-35 withdrawn) | Optional extension, not planned |
| Frontend | React + TypeScript + Tailwind CSS | Single-page app | P1 |
| Frontend tooling | Vite, TanStack Query, React Router | Build, server state, routing | P1 |
| Forms | Formik + Yup | Form state and client-side validation mirroring the backend rules (owner request, M1.6) | P1 |
| UI components | shadcn/ui (copied into the repo) on Radix primitives; design tokens as CSS variables | Accessible, reusable, themeable components (DESIGN_SYSTEM.md) | P1 |
| Frontend tests | Vitest + React Testing Library; Playwright | Unit/component tests; end-to-end tests | P1 |
| Cache / distributed rate limits | Redis 7 | Availability cache (booking service); gateway rate limiting | **P4 only** |
| Events between services | Transactional outbox + HTTP push (no broker) | Reliable side effects across services | P5 |
| Containers | Docker + Docker Compose | All services and infrastructure locally; images for any host | P1 |
| Backend tests | JUnit 5, Mockito, Testcontainers, WireMock 3.13 (`wiremock-standalone`, test scope, D-28 approved 2026-11-07) for Feign clients | Unit, integration, concurrency, resilience tests | P1 |
| CI | GitHub Actions | Build and test every module and the frontend | P1 |
| Observability | Spring Boot Actuator + Micrometer | Health, metrics (incl. resilience metrics) | P1 |

**Not used:** Kafka, RabbitMQ or any message broker; Kubernetes; Spring Cloud Bus (it needs a broker); rules engines; Lombok; MapStruct; real payment SDKs. Anything not in this table needs the owner's approval first. Distributed tracing (Micrometer Tracing + Zipkin/OpenTelemetry), springdoc-openapi, ArchUnit, WireMock and a load-test tool are **candidates pending approval** (ARCHITECTURE.md §16).

## 2. Architecture: microservices from the start

### 2.1 Services

| Service | Port (dev) | Owns | Database | Phase |
|---|---|---|---|---|
| `config-server` | 8888 | Serves configuration from the `gridreserve-config` Git repo | — | P1 |
| `discovery-server` | 8761 | Eureka registry | — | P1 |
| `api-gateway` | 8080 | Public entry point, routing, edge security, resilience, rate limits (P4) | — (Redis in P4) | P1 |
| `identity-service` | 8081 | Users, passwords, roles, login, refresh tokens, JWT signing, venue-manager assignments (P6) | `identity_db` | P1 |
| `booking-service` | 8082 | Categories, bookable types, venues, bookables, countries/time zones, availability, bookings, holds, audit, idempotency, sessions (P2), waitlist (P3), cache (P4), recurring (P7), walk-in queue (P8) | `booking_db` | P1 |
| `payment-service` | 8083 | Checkout, mock provider, webhooks, refunds | `payment_db` | P5 |
| `membership-service` | 8084 | Plans, memberships, credits | `membership_db` | P6 |
| `notification-service` | 8085 | In-app notifications, simulated email outbox | `notification_db` | P8 |

### 2.2 Why this split

- **The booking core stays together.** Double-booking (BR-10), member overlap across categories (BR-11), the per-member limit lock (BR-08), last-seat claims (CAP-02) and waitlist promotion (WL-04) all depend on **one PostgreSQL transaction** over bookings, bookables, sessions and waitlist rows. Splitting catalog, booking, capacity and waitlist into separate services with separate databases would turn each guarantee into a saga with compensation and windows of inconsistency. Keeping them in one service is the standard "split by business capability, not by entity" rule.
- **Identity is a natural boundary.** It has its own data (credentials, refresh tokens), its own security concerns (signing keys), and other services only need to verify tokens, which they can do locally with the public key (RES-07).
- **Payment, membership and notification** are separate capabilities with their own data and lifecycles. They talk to the booking service through events (outbox) and, where needed, idempotent commands with resilience patterns.
- **No message broker.** Owner decision D4: events use a transactional outbox pushed over HTTP, so there is no extra infrastructure to run. If a broker is needed later, the outbox is exactly what a relay would publish from.

### 2.3 How the parts interact

```
Browser ──HTTPS──► api-gateway ──lb://identity-service──► identity-service ──► identity_db
                       │   ▲         (Eureka + LoadBalancer)
                       │   └── JWT verify (public key), CORS, CircuitBreaker/Retry(GET)/RateLimit(P4, Redis)
                       └──────lb://booking-service───────► booking-service ×N ──► booking_db
                                                                │  OpenFeign (lb) + Resilience4j
                                                                └──► identity-service (member details, admin views)
All services ──startup/refresh──► config-server ──► gridreserve-config (Git)
All services ──register/heartbeat──► discovery-server (Eureka)
(P5+) each service: outbox table ──relay POST /internal/events──► subscriber services (lb://…)
```

## 3. Technologies

Each section: **role**, **why**, **how it interacts**, **alternatives rejected**.

### 3.1 Java 21 and Spring Boot 4

- **Role.** Every backend service is a Spring Boot application.
- **Why Spring Boot 4 (not 3).** Boot 4 is the current major line (built on Spring Framework 7, Jakarta EE 11) and the Spring Cloud release train that matches it (2025.1.x) supports Config, Eureka, Gateway and CircuitBreaker. Starting a new project on the current major avoids an early migration and shows current knowledge in interviews. The earlier docs said Boot 3 only because the original stack list fixed it.
- **What to expect from Boot 4 (verify each at scaffold, M1.1):** Jackson 3 as the default JSON library (new package names); more modular starters and auto-configuration; JSpecify null-safety annotations; Spring Framework 7 features such as HTTP interface client groups, API versioning support, and built-in `@Retryable` / `@ConcurrencyLimit`.
- **Risks.** Fewer tutorials and Q&A answers than Boot 3; some third-party libraries may lag. In particular, an official Resilience4j Boot 4 starter could not be confirmed when this was written, so M1.1 includes a **compatibility spike** (Resilience4j through Spring Cloud CircuitBreaker on Boot 4). If it fails, the fallback options are listed in ARCHITECTURE.md D-21.
- **Java 21:** LTS; records for DTOs; virtual threads available (off by default, D-09).
- **Alternatives rejected.** *Spring Boot 3.5*: still works and is better documented, but it is the previous major. *Quarkus/Micronaut*: smaller job market for the target role.

### 3.2 Maven (multi-module)

- **Role.** One parent `backend/pom.xml` inherits the Spring Boot parent and imports the Spring Cloud BOM in `<dependencyManagement>`, so module POMs declare dependencies **without versions**; libraries no BOM covers are pinned once as properties in the parent; plugin versions live in `<pluginManagement>`. Modules: `platform/config-server`, `platform/discovery-server`, `platform/api-gateway`, `services/identity-service`, `services/booking-service`, later services, and `libs/common-web` (shared error format, request ID, JWT verification, event envelope).
- **Why.** One command builds and tests everything (`./mvnw verify`); versions are consistent across services; Maven is the default in Spring docs.
- **Interactions.** CI builds all modules; each service module produces its own executable JAR and Docker image.
- **Alternatives rejected.** *Gradle*: flexible but extra learning cost. *Repo per service*: owner decision D6 (monorepo).

### 3.3 Spring Cloud Config Server

- **Role.** Central configuration for all services from a separate Git repository (`gridreserve-config`), with files `application.yml` (shared), `application-{dev,prod}.yml`, `{service}.yml`, `{service}-{dev,prod}.yml`.
- **Why.** One place to change configuration for every service and environment, versioned in Git with history and review; profiles `dev` and `prod` (PLAT-04); runtime changes without redeploying (PLAT-05).
- **Refresh.** Beans that should pick up changes use `@RefreshScope` (or `@ConfigurationProperties`, which is rebound on refresh). Without a broker there is no Spring Cloud Bus, so an admin **refresh script** lists instances from Eureka and calls `POST /actuator/refresh` on each (owner decision D5). What is refreshable: resilience thresholds, rate-limit settings, CORS origins, job intervals, feature toggles. What is not: booking rules (database data), datasource URLs (restart needed).
- **Secrets.** Never in the config repo (PLAT-06). Services read secrets from environment variables; the config repo may reference them as `${DB_PASSWORD}` placeholders.
- **Interactions.** Services import config at startup with `spring.config.import=configserver:` and fail fast; the container platform restarts them with backoff (PLAT-07). The config server is reached by a fixed URL (`CONFIG_SERVER_URL`, D-20). In `dev` it uses its `native` backend on the mounted local clone (Git `file:` URIs would make it work inside the folder); in `prod` the Git backend reads GitHub.
- **Alternatives rejected.** *Local `application.yml` per service*: owner wants central config. *Config folder inside the monorepo (native mode)*: simpler, but owner chose a separate repo. *Spring Cloud Bus*: needs RabbitMQ/Kafka. *Consul / Kubernetes ConfigMaps*: other infrastructure.

### 3.4 Spring Cloud Netflix Eureka (discovery) and Spring Cloud LoadBalancer

- **Role.** `discovery-server` runs Eureka; every service registers itself and sends heartbeats. Clients (gateway routes, Feign clients) resolve `lb://booking-service` through the registry and pick an instance with client-side round robin.
- **Why.** Instances can be added or removed without changing configuration (PLAT-02/03); running two booking-service instances is how CONC-10 proves that correctness does not depend on which instance handles a request.
- **Behavior on failure.** If Eureka is down, clients keep using their cached registry (NFR-REL-05).
- **Alternatives rejected.** *Fixed hostnames / Docker DNS only*: no instance health awareness, not what the owner asked for. *Consul*: extra infrastructure. *Server-side load balancer (nginx)*: an extra component; client-side balancing is the Spring Cloud idiom.

### 3.5 Spring Cloud Gateway

- **Role.** The only public entry point: routes `/api/v1/auth/**` and `/api/v1/me` to `identity-service` and the rest of P1 to `booking-service`; verifies the JWT signature early (services verify again); applies CORS; adds `X-Request-Id`; per-route CircuitBreaker with a JSON fallback, Retry for GET routes only, response timeouts; from P4, the **Redis `RequestRateLimiter`** for login and booking creation (NFR-SEC-06).
- **Variant.** The reactive variant (Spring Cloud Gateway Server WebFlux) is chosen because its Redis rate limiter is built in and it is the most widely used. Only the gateway is reactive; it contains almost no business code. All other services are servlet-based (Spring MVC).
- **Alternatives rejected.** *Gateway Server WebMVC*: same programming model as the services, but rate limiting would need a different backend; revisit if the WebFlux learning cost is a problem (D-22). *nginx / Kong*: not Spring, no Eureka integration out of the box.

### 3.6 Resilience4j (via Spring Cloud CircuitBreaker)

- **Role.** The four requested patterns plus timeouts, on every call between services and at the gateway (section 13 of REQUIREMENTS.md):

| Pattern | Where | Purpose |
|---|---|---|
| **CircuitBreaker** | Each downstream client (booking → identity, outbox relay → each subscriber, payment → mock provider); each gateway route; Redis calls (P4) | Stop calling a failing service; fail fast to a fallback; recover via half-open |
| **Retry** | Only idempotent calls: GETs, event deliveries, idempotency-keyed commands; GET routes at the gateway | Ride out short blips; exponential backoff + jitter; never on 4xx |
| **Bulkhead** | Each downstream client | Cap concurrent calls so one slow dependency cannot exhaust request threads |
| **RateLimiter** | Calls to rate-sensitive downstreams (mock payment provider, P5) | Per-instance protection of a downstream |
| **TimeLimiter / HTTP timeouts** | All outbound calls | Bound waiting time |

- **Two kinds of rate limiting (both kept).** The Resilience4j RateLimiter is **per instance** and protects downstream services. The Redis rate limiter at the gateway (P4) is **distributed** and protects the system from clients (per user / per IP). They solve different problems.
- **Order of decorators:** Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → call (RES-09).
- **Interactions.** Configured in the config repo (refreshable); metrics exported through Micrometer (RES-08).
- **Alternatives considered.** *Spring Framework 7 built-in `@Retryable` / `@ConcurrencyLimit`*: no extra library, but no circuit breaker or rate limiter; may be used for simple retries if the Resilience4j spike fails. *Hystrix*: discontinued.

### 3.7 Service-to-service HTTP: Spring Cloud OpenFeign (owner decision 2026-11-05)

- **Role.** Declarative clients such as `@FeignClient(name = "identity-service") interface IdentityClient { @GetMapping("/internal/users") List<MemberDto> getMembers(@RequestParam List<Long> ids); }`. The service name is resolved through **Eureka** and load-balanced by **Spring Cloud LoadBalancer**; we do not implement discovery ourselves.
- **Resilience.** Timeouts per client in config; Feign's own retryer off; Retry/CircuitBreaker/Bulkhead/RateLimiter as Resilience4j annotations on an adapter class that owns the fallback (ARCHITECTURE.md §11.1).
- **Why.** Very common in Spring microservice codebases and job descriptions; reads exactly like a Spring MVC controller, so it is easy to explain.
- **Trade-offs.** The Spring Cloud team treats OpenFeign as feature-complete (maintenance mode) and recommends Spring's HTTP interface clients for new code; Boot 4 compatibility is verified in the M1.1 spike. Feign uses its own HTTP stack, so client tests need a stub HTTP server (WireMock, pending approval D-28) instead of `MockRestServiceServer`.
- **Alternatives considered.** *HTTP interface + `@LoadBalanced RestClient`*: same declarative style, built into Spring Framework; kept as the fallback if the spike shows Feign problems on Boot 4. *WebClient*: reactive, unnecessary in servlet services.

### 3.8 PostgreSQL (database per service)

- **Role.** Source of truth. One PostgreSQL 16 server (container) with separate databases `identity_db`, `booking_db`, later `payment_db`, `membership_db`, `notification_db`, each with its own user (PLAT-08).
- **Why PostgreSQL:** `tstzrange` + `btree_gist` **exclusion constraints** (BR-10, BR-11), conditional atomic updates (CAP-02), row locks, `SKIP LOCKED` for workers and outbox relays, partial indexes, `timestamptz`.
- **Why one server, several databases:** real data ownership per service (no cross-service joins) at the memory cost of a single PostgreSQL process.
- **Alternatives rejected.** *MySQL*: no exclusion constraints. *MongoDB*: no relational constraints. *H2 in tests*: cannot run these features. *One shared database for all services*: breaks service ownership.

### 3.9 Spring Data JPA (Hibernate)

- **Role.** Entities and repositories inside each service. Explicit SQL (`@Modifying @Query`, native queries or `JdbcTemplate`) where exact semantics matter: conditional status transitions, seat claims, `FOR UPDATE` locks, `SKIP LOCKED` batches.
- **Rules.** Entities never leave a service; DTOs only. `open-in-view=false`. `ddl-auto=validate`. Constraint violations mapped to `409` by constraint name.
- **Alternatives rejected.** *jOOQ / MyBatis*: extra libraries.

### 3.10 Flyway (per service)

- **Role.** Each service has its own `db/migration` folder and migrates its own database at startup.
- **Why.** Exclusion constraints, extensions, partial indexes and seed data must be hand-written SQL; every environment gets the same schema; seed data (categories, bookable types, country/time-zone list, demo users) is versioned.
- **Rules.** Never edit a migration that reached `main`; fix forward.
- **Alternatives rejected.** *Liquibase*: XML/YAML changelogs, less readable. *`ddl-auto=update`*: cannot create exclusion constraints.

### 3.11 Redis (Phase 4 only)

- **Role.** (1) **Availability cache** in the booking service: cache-aside with a per-bookable generation counter and a 30 s TTL. (2) **Distributed rate limiting** at the gateway with the built-in Redis `RequestRateLimiter` (token bucket, executed atomically by a Lua script inside Redis).
- **Rules.** Never the source of truth; never decides a booking; short timeouts; failures fall back (cache → PostgreSQL; rate limiting fails open) and are covered by a CircuitBreaker (NFR-REL-01).
- **Alternatives rejected.** *In-process cache only*: not shared across instances. *Redis locks for booking*: unsafe and unnecessary.

### 3.12 Spring Security and JWT (resource server + Nimbus JOSE)

- **Identity service:** BCrypt password hashing; login issues an **RS256** access token (15 min) signed with the private key (environment variable) and sets the **refresh-token cookie** (httpOnly, `SameSite=Strict`, path `/api/v1/auth`; opaque, stored hashed, sliding 7 days, not rotated, D-37); refresh and logout endpoints with Origin + custom-header CSRF checks (FR-AUTH-09..12).
- **Gateway:** verifies the access token before routing (cheap early rejection) with the same `common-web` rules as the services, through Spring Security's reactive resource server (D-38, M1.3c); roles are left to the services; CORS from M1.6.
- **All services:** stateless Spring Security filter chain; Spring's Bearer-token filter verifies the token with the **public keys** (from config, keyed by `kid`) through a decoder built in `common-web`, and the principal carries `sub` (user ID) and the `role` authority; URL and method security; ownership checks in services; `401/403` in the shared error format (`401 TOKEN_EXPIRED` for an expired token).
- **Why RS256 now (was HS256).** With several services verifying tokens, a shared HMAC secret would let any service mint tokens. With RS256 only the identity service can sign; everyone else verifies.
- **Why Spring's resource server + Nimbus (D-34, 2026-11-06; replaced jjwt).** It is the standard Spring Security way; versions are managed by Spring Boot; the Bearer filter exists for servlet services and for the reactive gateway, so we write no token filter ourselves; signing uses `NimbusJwtEncoder`. jjwt's JSON module needs Jackson 2, which would sit next to Boot 4's Jackson 3.
- **Alternatives considered.** *jjwt*: explicit API, but two Jackson versions and hand-written filters (servlet and reactive). *Spring Authorization Server* (full OAuth2): more than this project needs. *A JWKS endpoint* on identity for automatic key rotation: a later improvement (D-23); keys come from the config repo for now.
- **Sign-in with Google and GitHub (optional extension, not planned; D-35 withdrawn 2026-11-06).** Spring Security's **OAuth2 client** does the redirect, the code exchange and OIDC ID-token checks; identity-service only decides which account the provider identity belongs to (verified email only) and then starts a normal session. *Alternatives:* hand-written OAuth2 code exchange (more code, easy to get state/PKCE wrong); an external identity provider such as Keycloak or Auth0 (another system to run or depend on).

### 3.13 React, TypeScript, Tailwind, Vite, TanStack Query, React Router

- **Role.** The SPA talks only to the gateway. React Router for pages and guards; TanStack Query for server state (loading/error/empty, invalidation after `409`); Tailwind for styling; Vite for dev server (proxy `/api` → gateway `:8080`) and build.
- **Auth handling.** Access token in memory; on start and on `401 TOKEN_EXPIRED`, one call to `/auth/refresh` (cookie sent automatically), then retry the original request once.
- **Alternatives rejected.** *Next.js*: SSR not needed. *Redux for server data*: boilerplate. *localStorage tokens*: owner chose the refresh-cookie design.

### 3.13a UI component system: shadcn/ui + Radix (decided 2026-11-05)

- **Role.** Reusable, accessible components with a professional, consistent look. Visual rules, tokens and the component inventory live in **DESIGN_SYSTEM.md**.
- **How it works.** shadcn/ui is not a runtime library: its CLI copies component source (Button, Dialog, Select, …) into `src/components/ui`, where we own and adapt it. Behavior that is hard to get right (focus trapping, keyboard navigation, ARIA for menus, dialogs, selects, popovers) comes from **Radix primitives**. Styling is Tailwind utilities reading **design tokens** (CSS variables for light and `.dark`), so dark mode is a token swap.
- **Supporting packages (D-UI-3, approval at install):** `class-variance-authority`, `clsx`, `tailwind-merge` (variant styling), `lucide-react` (icons), `tw-animate-css` (enter/exit animations), `sonner` (toasts), `react-day-picker` (calendar), `@tanstack/react-table` (admin tables), `@fontsource-variable/inter` (self-hosted font). `@axe-core/playwright` for automated accessibility checks (D-UI-4).
- **Why.** Accessible primitives without building them ourselves; full control of the code and look (unlike a closed component library); the most common React setup in current job listings alongside Tailwind.
- **Alternatives rejected.** *MUI / Ant Design / Chakra*: heavier, harder to make look custom, runtime theming. *Fully hand-built components*: accessible dialogs, menus and comboboxes are hard to get right. *Headless UI*: smaller component set than Radix.

### 3.14 Frontend tests: Vitest, React Testing Library, Playwright

- **Vitest + RTL:** API client (error parsing, refresh-and-retry), error-code messages, route guards, hold countdown.
- **Playwright:** end-to-end through the real gateway and services in Docker Compose: the main journey, the "just taken" race with two browser contexts, refresh-token flow, degraded mode when identity is down.

### 3.15 Docker and Docker Compose

- **Role.** `infra/compose.yaml` (with `infra/.env`) runs PostgreSQL (init script creates one database and user per service), config server, discovery server, gateway, identity and booking services (and Redis from P4) with health checks and `depends_on: condition: service_healthy`. `docker compose up --scale booking-service=2` demonstrates load balancing. In `dev` the config server mounts the sibling `gridreserve-config` clone.
- **Images.** One multi-stage Dockerfile per service (Maven build → JRE 21 runtime, non-root, heap capped, NFR-PERF-05).
- **Memory reality.** Phase 1 runs about 5 JVMs plus PostgreSQL. With ~256 MB heap each that is roughly 2–3 GB RAM including overhead. This matters for the deploy host choice (D-15).
- **Alternatives rejected.** *Kubernetes*: not allowed; Compose is enough for the demo and for interviews about the concepts.

### 3.16 Backend testing: JUnit 5, Mockito, Testcontainers, stub HTTP server

| Level | Tools | Proves |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Rules, resolver, fallbacks, token logic (fixed `Clock`) |
| Slice | `@WebMvcTest` + Spring Security test | Validation, error format, 401/403 |
| Integration (per service) | `@SpringBootTest` + Testcontainers PostgreSQL; config client and Eureka disabled (`test` profile) | Constraints, migrations, transactions |
| Client / resilience | Feign client against WireMock (booking-service `IdentityGatewayIT`); gateway routes against a JDK `HttpServer` stub (`GatewayResilienceIT`) | Timeouts, retries, circuit opening, bulkhead rejection, fallbacks |
| Concurrency | Testcontainers + `ExecutorService` + `CountDownLatch` | CONC-01..09 |
| System | Docker Compose stack (+ Playwright) | CONC-10, FS-32..38, main journey |

- **Why no H2:** the key guarantees live in PostgreSQL features H2 lacks.
- **WireMock** (D-28, approved 2026-11-07): the standalone build bundles its own Jetty and Jackson, so it can't clash with Boot 4's versions; test scope only.

### 3.17 GitHub Actions

- **CI on every push/PR:** Java 21 Temurin, Maven cache, `./mvnw verify` for all modules (Testcontainers works on Ubuntu runners); frontend `npm ci`, type check, lint, Vitest, build; build all Docker images.
- **System-test job** (on PRs to `main` and nightly): `docker compose up`, Playwright and resilience scenarios (FS-32..38) — kept separate because it is slower.
- **CD** added once the deploy host is chosen (M1.10).

### 3.18 Spring Boot Actuator and Micrometer

- **Role.** Per service: `/actuator/health` with liveness/readiness groups (used by Eureka, Compose and the host), `/actuator/refresh` (admin/internal only), metrics: HTTP timings, HikariCP, JVM, Resilience4j (circuit state, retries, bulkhead and rate-limiter rejections), and custom booking metrics (bookings created, conflicts by code, holds expired, outbox backlog).
- **Exposure.** Only the gateway's `health` is public; everything else is admin/internal (NFR-SEC-08).
- **Tracing.** Request IDs are propagated in P1; full distributed tracing (Micrometer Tracing + Zipkin or OpenTelemetry) is valuable with microservices but is a new dependency, pending approval (D-24).

## 4. Profiles

| Profile | Used by | Configuration source | Notes |
|---|---|---|---|
| `dev` | Local Docker Compose and IDE runs | Config Server (`*-dev.yml`) | Verbose logs, relaxed CORS for `localhost:5173`, seed demo data, actuator endpoints reachable locally |
| `prod` | Deployed host | Config Server (`*-prod.yml`) + env vars for secrets | Fail fast, secure cookies, strict CORS, only gateway health public |
| `test` | Automated tests | Test properties only | Config client and Eureka disabled; Testcontainers datasource |

## 5. Versions (to pin at scaffold time)

| Component | Target | Check at scaffold |
|---|---|---|
| Java | 21 LTS (Temurin) | — |
| Spring Boot | Latest 4.x patch | Spring Boot release notes |
| Spring Cloud | Release train compatible with that Boot version (2025.1.x line when written) | Official Spring Cloud compatibility table |
| Resilience4j | Version supported on Boot 4 via Spring Cloud CircuitBreaker | M1.1 compatibility spike |
| PostgreSQL | 16 (same tag in Compose, Testcontainers, prod) | — |
| Redis | 7 (P4) | — |
| Node.js | Current LTS | — |
| React 19.3, React Router 8.5, TanStack Query 5.107, Tailwind CSS 4.3, Vite 8.3, TypeScript 6.0, Vitest 5.1, Formik 2.4, Yup 1.7 | Pinned exactly in `frontend/package.json` (M1.6); Node 24 LTS (`frontend/.nvmrc`) | TypeScript stays on 6.0 until `typescript-eslint` supports 7 |
| Playwright | Latest stable when added (M1.13) | — |
| Nimbus JOSE, Flyway, Testcontainers | Latest stable, managed by BOMs where possible | Artifact names may differ on Boot 4 |

## 6. Note on CLAUDE.md

CLAUDE.md v2 (2026-11-04) lists this stack as the fixed stack. Any further addition still needs the owner's approval.
