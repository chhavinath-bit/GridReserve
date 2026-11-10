# GridReserve

One booking platform for many kinds of venues: library rooms and seats, sports courts, parking bays, clinic appointments and gym sessions. A member picks a category, a venue and a bookable, holds a time slot for five minutes and confirms it. Double-booking is impossible, even under heavy concurrent load and with several service instances running.

> **Status: Phase 1, M1.9.** Config server, Eureka, gateway, identity and booking services run together. identity-service registers users (and logs them in at once), issues RS256 access tokens plus a refresh-token cookie (sliding 7 days, logout ends it) and answers `/api/v1/me`. The gateway refuses requests without a valid access token, and booking-service checks the token and the role again itself, without calling identity-service. booking-service owns `booking_db` (Flyway schema, demo catalog) and the database itself rejects double-booking and overlapping bookings per member. booking-service serves the catalog (categories, venues, bookables with their effective rules, countries and time zones) and slot availability for a date (M1.4). Members hold a slot for 5 minutes, confirm it and cancel it; admins can cancel any booking (M1.5). A React frontend runs the whole journey in the browser through the gateway (M1.6). Every booking rule (hours, grid, length, horizon, per-member limit, cancellation cutoff) is enforced and a background job expires unconfirmed holds (M1.7). Double-booking is proven impossible under parallel load, also through the gateway with two booking-service instances (M1.8). Calls between services are protected (timeouts, retry, circuit breaker, bulkhead, fallbacks) and configuration changes reach every instance without a restart (M1.9). Container images and the first deployment are next (M1.10). Progress and verified test results are logged in [docs/PROGRESS.md](docs/PROGRESS.md).

## What this project demonstrates

- **Correctness under concurrency** enforced by PostgreSQL itself: exclusion constraints on time ranges, atomic seat claims, row locks, conditional state transitions. Target: 50 parallel requests for one slot → exactly 1 success, 49 conflicts, also across two service instances.
- **Microservices with Spring Cloud** (planned): Config Server with a separate config repository and `dev`/`prod` profiles, Eureka discovery, API Gateway, OpenFeign between services, Resilience4j (retry, rate limiter, bulkhead, circuit breaker), transactional outbox.
- **Testing**: JUnit 5, Mockito, Testcontainers against real PostgreSQL, concurrency and resilience tests, Playwright end-to-end tests.
- **Frontend**: React + TypeScript, Tailwind, shadcn/ui on Radix, light and dark themes.

## Planned stack

Java 21 · Spring Boot 4 · Spring Cloud (Config, Eureka, Gateway, OpenFeign, Resilience4j) · Spring Security + JWT · Spring Data JPA · PostgreSQL 16 · Flyway · Redis (Phase 4) · React · TypeScript · Vite · Tailwind CSS · TanStack Query · Docker Compose · GitHub Actions

## Documentation

| Document | Contents |
|---|---|
| [PROJECT.md](PROJECT.md) | Product spec and phased roadmap |
| [REQUIREMENTS.md](REQUIREMENTS.md) | Testable requirements with IDs |
| [TECH_STACK.md](TECH_STACK.md) | Technologies, reasons, rejected alternatives |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Services, schemas, transactions, concurrency, resilience |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | UI tokens, components, accessibility |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | Ordered milestones with tests and done criteria |
| [CLAUDE.md](CLAUDE.md) | Working rules for AI-assisted development sessions |

## Repository layout

```
backend/     Java: platform/ (config-server, discovery-server, api-gateway), services/, libs/
frontend/    React app (from M1.6)
infra/       Docker Compose and PostgreSQL init
scripts/     Helper scripts (from M1.9)
docs/        Progress log
```

Runtime configuration lives in a separate repository: [gridreserve-config](https://github.com/chhavinath-bit/gridreserve-config).

## Getting started

Requirements: Docker (with about 4 GB of memory available), Java 21 (only to build and test outside Docker).

```bash
# The app repo and the config repo must sit side by side
git clone https://github.com/chhavinath-bit/GridReserve.git
git clone https://github.com/chhavinath-bit/gridreserve-config.git

# Local secrets: copy the template and replace every "change-me"
# (JWT_PRIVATE_KEY needs a real key pair, see "Access-token keys" below)
cd GridReserve/infra
cp .env.example .env

# Build and start PostgreSQL, config server, Eureka, gateway and services
docker compose up -d --build --wait

# Smoke test through the gateway (routing can take ~15 s after everything is healthy)
curl -i localhost:8080/api/v1/ping
```

- Eureka dashboard: http://localhost:8761
- Only the gateway (8080), Eureka (8761), the config server (8888) and PostgreSQL (5432) are published, on `127.0.0.1` only.
- Two booking-service instances: `docker compose up -d --scale booking-service=2 --wait`
- Stop: `docker compose down` (`-v` also deletes the database volume)

### Demo accounts and logging in

Demo-only accounts, seeded by `identity-service` (fictional; never use these passwords anywhere else):

| Email | Password | Role |
|---|---|---|
| `admin@gridreserve.test` | `demo-admin-pass` | ADMIN |
| `alice@gridreserve.test` | `demo-alice-pass` | MEMBER |
| `bob@gridreserve.test` | `demo-bob-pass` | MEMBER |

```bash
# Log in through the gateway and keep the access token (valid 15 minutes)
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@gridreserve.test","password":"demo-alice-pass"}' | jq -r .accessToken)

curl -s localhost:8080/api/v1/me -H "Authorization: Bearer $TOKEN"     # 200, Alice
curl -s localhost:8080/api/v1/me                                      # 401 UNAUTHENTICATED

# The gateway checks the token before routing; booking-service checks it, and the role, again
curl -s localhost:8080/api/v1/categories                                       # 401 from the gateway
curl -s localhost:8080/api/v1/categories -H "Authorization: Bearer $TOKEN"     # 200: categories with their bookable types
curl -s localhost:8080/api/v1/admin/bookings -H "Authorization: Bearer $TOKEN" # 403 FORBIDDEN: Alice is a MEMBER

# Register a new member (always MEMBER; the email is stored lower-cased). Registering also logs in:
# the answer is the same as for login (access token + refresh cookie).
curl -s localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"carol@example.test","password":"carol-pass-1","displayName":"Carol"}'
```

Staying logged in: login and register also set the `gr_refresh` cookie (HttpOnly, 7 days without use). Refresh and logout accept only calls that look like they come from our frontend (the `Origin` and `X-GridReserve-Client` headers), so curl must send both:

```bash
JAR=$(mktemp)   # curl's cookie store, standing in for the browser
APP=(-H 'Origin: http://localhost:5173' -H 'X-GridReserve-Client: web')

curl -s -c "$JAR" localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@gridreserve.test","password":"demo-alice-pass"}' > /dev/null
curl -s -b "$JAR" -c "$JAR" -X POST localhost:8080/api/v1/auth/refresh "${APP[@]}"   # 200, new access token
curl -s -b "$JAR" -X POST localhost:8080/api/v1/auth/refresh                          # 403 FORBIDDEN (no Origin/header)
curl -s -b "$JAR" -c "$JAR" -X POST localhost:8080/api/v1/auth/logout "${APP[@]}" -o /dev/null -w '%{http_code}\n'   # 204
curl -s -b "$JAR" -X POST localhost:8080/api/v1/auth/refresh "${APP[@]}"              # 401 REFRESH_TOKEN_INVALID
```

### Browsing the catalog and availability

All catalog calls need a signed-in user (any role). Lists of venues and bookables are paginated (`page`, `size`, default 20, max 100).

```bash
H="Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/v1/categories -H "$H"                          # 5 categories with their bookable types
curl -s "localhost:8080/api/v1/venues?categoryId=2" -H "$H"               # venues of one category, as a page
curl -s localhost:8080/api/v1/venues/2/bookables -H "$H"                  # Court A, Court B (label "Court")
curl -s localhost:8080/api/v1/bookables/5 -H "$H"                         # effective rules, zone, opening hours
curl -s "localhost:8080/api/v1/bookables/5/availability?date=$(date -v+1d +%F)" -H "$H"   # slots: FREE / TAKEN / PAST
curl -s localhost:8080/api/v1/countries/IN/time-zones -H "$H"             # ["Asia/Kolkata"]
```

IDs come from the seed and may differ on your machine; take them from the list responses. Availability serves today and the next 6 days in the venue's time zone (`422 BEYOND_HORIZON` after that); `date` is a local date (`YYYY-MM-DD`; on Linux use `date -d tomorrow +%F`).

### Book with curl

A booking starts as a 5-minute **hold** (`HELD`) and becomes `CONFIRMED` when you confirm it in time. Times are UTC instants; Court A is in `Asia/Kolkata` (UTC+05:30), so `04:30Z` is 10:00 at the court.

```bash
H="Authorization: Bearer $TOKEN"; J="Content-Type: application/json"
DAY=$(date -v+1d +%F)    # on Linux: date -d tomorrow +%F

# Hold Court A 10:00-11:00 local → 201, Location: /api/v1/bookings/{id}, status HELD, expiresAt = now + 5 min
curl -si localhost:8080/api/v1/bookings -H "$H" -H "$J" \
  -d "{\"bookableId\":5,\"start\":\"${DAY}T04:30:00Z\",\"end\":\"${DAY}T05:30:00Z\"}"

curl -s -X POST localhost:8080/api/v1/bookings/1/confirm -H "$H"            # 200 CONFIRMED (409 HOLD_EXPIRED after 5 min)
curl -s localhost:8080/api/v1/bookings/me -H "$H"                           # your bookings, newest start first
curl -s localhost:8080/api/v1/bookings/1 -H "$H"                            # one booking (someone else's → 404)
curl -s -X POST localhost:8080/api/v1/bookings/1/cancel -H "$H" -H "$J" -d '{"reason":"Plans changed"}'   # 200 CANCELLED
```

The same slot for another member answers `409 SLOT_TAKEN`; the same member at the same time on another bookable answers `409 MEMBER_OVERLAP`. Both come from PostgreSQL exclusion constraints, not from a Java check, so they hold even when two requests arrive together. Admins can cancel any booking (`cancelledBy: ADMIN`) but book only for themselves.

### Booking rules and defaults

Every hold is checked in this order; the first rule that fails is the answer (REQUIREMENTS BR-14): times in order (`400`) → bookable and venue active → booked by time slot → start in the future → within the horizon → inside that weekday's opening hours, on one local date → on the slot grid → allowed length → fewer than 3 active bookings in the category (all `422`, each with its own code) → no overlap (`409`, decided by PostgreSQL). Times are the venue's local time; the horizon is today plus the next 6 days there.

| Bookable type | Slot grid | Length | Buffer after |
|---|---|---|---|
| Study Room | 30 min | 30 min – 3 h | — |
| Study Seat | 60 min | 1 – 4 h | — |
| Sports Court | 60 min | 1 – 2 h | — |
| Parking Bay | 60 min | 1 – 12 h | — |
| Clinic Doctor | 30 min | exactly 30 min | — |
| Gym Trainer | 15 min | exactly 45 min | 15 min (one client per hour) |
| Study Hall, Gym Class | sessions (CAPACITY, Phase 2) | | |

Per category: cancellation cutoff 2 hours, at most 3 active bookings (HELD or CONFIRMED, not yet ended), horizon 7 days. A bookable may override the type's grid, length and buffer, and may have its own weekly hours.

**Cancelling:** a live hold any time; a confirmed booking until 2 hours before its start (after that `422 CUTOFF_PASSED`); admins until it ends; nothing after the end (`409 INVALID_STATE`). Every booking response has `cancellableUntil`, so the UI can disable Cancel in time.

**Hold expiry:** a hold that isn't confirmed within 5 minutes stops blocking the slot at once (every check compares `expires_at` with now). The `HoldExpiryJob` marks such holds `EXPIRED` every 30 seconds, safely on any number of booking-service instances; settings `gridreserve.holds.expiry.{enabled,interval,batch-size}` (defaults on, 30 s, 100).

### How double-booking is prevented (even with two instances)

The database decides, not Java and not a single server:

1. **PostgreSQL exclusion constraints.** `ex_booking_bookable_overlap` allows at most one active booking per bookable for any moment (buffer included); `ex_booking_member_overlap` stops a member from being in two places at once. Two requests that both pass the Java checks still can't both insert: the second gets `409 SLOT_TAKEN` (or `MEMBER_OVERLAP`).
2. **Row locks in one global order** (venue `FOR SHARE` → bookable `FOR UPDATE` → member `FOR UPDATE` → booking rows). Holds on the same bookable queue for a few milliseconds each; holds on different bookables run in parallel; the member lock makes the per-member limit exact.
3. **Nothing in memory.** Instances share only the database, so it doesn't matter which instance a request reaches; the gateway never retries a `POST`.

**Proof.** `ConcurrencyIT` (booking-service, real PostgreSQL) releases parallel requests through a start gate, 20 times each: 50 members on one slot → exactly 1 × 201 and 49 × 409 with one active row (under 10 s); overlapping ranges; one member on ten seats; the 3-booking limit under load; confirm racing expiry. Every run also asserts that no transaction was killed for a deadlock (`gridreserve.db.retries` stays 0). Then CONC-10 repeats the 50-member race **through the gateway with two booking-service instances**, 20 times, and checks with each instance's own counters that both took part:

```bash
cd infra && docker compose up -d --build --scale booking-service=2 --wait
cd ../backend && ./mvnw -B -pl system-tests -Psystem-tests verify   # waits until the gateway routes, then 20 rounds
```

CI runs the same on a throwaway stack (job "System tests").

**What the tests found (M1.8).** With the bookable locked `FOR SHARE`, parallel holds on one slot sometimes deadlocked inside PostgreSQL's exclusion check; locking it `FOR UPDATE` fixed it (0 deadlocks in 300 runs). And the gateway had an unintended 25-call bulkhead from Spring Cloud CircuitBreaker's defaults, which turned a burst of 50 holds into 503s and opened the circuit breaker; it is now off (ARCHITECTURE D-41).

### Resilience demo

Booking keeps working when other parts fail (REQUIREMENTS §13). Try it on the running stack (two booking instances as above):

```bash
cd infra
docker compose stop identity-service          # login and refresh now answer 503 SERVICE_UNAVAILABLE ...
#   ... but a member who already has an access token can still browse, hold, confirm and cancel:
#   tokens are checked locally with the public key, and booking keeps its own member rows (RES-07)
docker compose up -d --wait identity-service

docker compose stop discovery-server          # Eureka down: routes keep working from the cached registry (FS-38)
docker compose up -d --wait discovery-server
```

What protects each call:

| Call | Protection | When it fails |
|---|---|---|
| Client → gateway → service | Per route: circuit breaker, 5 s timeout; GETs retried (never POSTs) | `503 SERVICE_UNAVAILABLE`, or `504 UPSTREAM_TIMEOUT` when too slow, in the standard error format |
| booking-service → identity-service (`/internal/users`) | Service token (role `SERVICE`), 1 s / 2 s timeouts, retry ×3 on 5xx and network errors (never 4xx), circuit breaker, bulkhead of 10 | Fallback: member IDs only, `memberDetailsAvailable: false` |
| Rate-sensitive downstreams (Phase 5) | Rate limiter, 20 calls/s per instance | Fallback at once |

Circuit-breaker states are in `/actuator/health` (component `circuitBreakers`, admins only) and in the metrics (`resilience4j.circuitbreaker.state`, `resilience4j.retry.calls`, `resilience4j.bulkhead.*`). `ResilienceSystemIT` runs these scenarios automatically, plus killing one booking instance during a burst (FS-34): `cd backend && ./mvnw -B -pl system-tests -Psystem-tests verify`.

### Change config without restart

The resilience settings (circuit-breaker thresholds, retries, bulkhead and rate-limiter limits) change on every running instance without a restart (PLAT-05):

```bash
# 1. Edit the config repo, e.g. gridreserve-config/booking-service.yml (dev reads your local clone)
# 2. Apply it everywhere (finds every instance in Eureka, POST /actuator/refresh with an admin token):
GRIDRESERVE_ADMIN_EMAIL=admin@gridreserve.test GRIDRESERVE_ADMIN_PASSWORD=demo-admin-pass \
  scripts/refresh-config.sh booking-service
# booking-service 172.18.0.8:8082 → 200, changed keys: ["resilience4j.circuitbreaker…failure-rate-threshold"]
# booking-service 172.18.0.6:8082 → 200, …
```

Not refreshable (restart for those): datasources, ports, keys, Feign timeouts, which actuator endpoints are exposed, and the hold-expiry job's settings (read once at startup). Service-to-service calls need two values in `infra/.env`: `BOOKING_SERVICE_CLIENT_SECRET` and its SHA-256 `BOOKING_SERVICE_CLIENT_SECRET_SHA256` (`.env.example` shows how to generate them).

### Run the frontend

The React app (`frontend/`) talks only to the gateway. In development Vite serves it on `http://localhost:5173` and forwards `/api` to the gateway on `:8080`, so the browser sees one origin and the refresh cookie works without CORS. Needs Node 24 or newer (`frontend/.nvmrc`) and the Compose stack running.

```bash
cd frontend
npm ci            # exact versions from package-lock.json
npm run dev       # http://localhost:5173, log in with a demo account above
```

The slice in M1.6: log in or register → pick a category, venue and bookable → pick a date and a free time (times in the venue's zone) → **Hold slot** (5-minute countdown) → **Confirm booking** → **My bookings** (Upcoming, Past, Cancelled & expired; confirm, release or cancel from there). A reload keeps you logged in: the access token lives only in memory and is restored from the `HttpOnly` refresh cookie. Try the race: open a second browser profile as bob, select the same slot in both, hold in one, then the other answers "This slot was just taken" and refreshes the free times. The theme switch (System / Light / Dark) is in the top bar. In development `http://localhost:5173/dev/components` shows every component in every state, light and dark side by side (not part of production builds).

```bash
npm run typecheck   # TypeScript, strict
npm run lint        # ESLint with the React hooks rules
npm test            # Vitest + React Testing Library
npm run build       # production build into frontend/dist
```

### Access-token keys

identity-service signs access tokens with an RSA private key (`JWT_PRIVATE_KEY` in `infra/.env`, never committed); every service verifies them with the matching public key from the config repo (`gridreserve.jwt.public-keys` in `application.yml`, keyed by `kid`). identity-service refuses to start if the two do not match. To make your own dev pair, follow the commands in `infra/.env.example`, put the public key in your config repo clone, and set `gridreserve.jwt.signing.key-id` in `identity-service.yml` to its `kid`.

Build and run all backend tests (Docker must be running):

```bash
cd backend
./mvnw -B verify
```

`verify` runs unit tests (`*Test`, Surefire) and integration tests against a real PostgreSQL 16 in a Testcontainer (`*IT`, Failsafe). The schema guarantees are proven in `services/booking-service/src/test/java/com/gridreserve/booking/schema/BookingConstraintsIT.java`; registration, login and tokens in `services/identity-service/src/test/java/com/gridreserve/identity/auth/AuthFlowIT.java`; the refresh cookie, logout and the CSRF check in `RefreshFlowIT.java` next to it. The gateway's token check is in `platform/api-gateway/src/test/java/com/gridreserve/gateway/GatewayTokenCheckIT.java`, booking-service's in `services/booking-service/src/test/java/com/gridreserve/booking/config/BookingSecurityIT.java`. The catalog and availability are covered by `CatalogIT` and `AvailabilityIT` (integration) and `RuleResolverTest` and `LocalSlotGridTest` (unit, including daylight-saving days). Hold, confirm and cancel are covered by `booking/BookingLifecycleIT` (integration, with a movable clock so holds can run out without waiting), `CancellationIT` (the cutoff's edges) and `HoldExpiryJobIT` (two sweeps in parallel, 20 repetitions), and by `BookingRulesTest` (every rule per type, table-driven), `BookingRetryTest` and `ConstraintViolationsTest` (unit). Tests generate throwaway keys; no key is needed to run them.

All demo data is fictional.

## License

[MIT](LICENSE)
