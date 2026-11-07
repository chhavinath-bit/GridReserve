# GridReserve

One booking platform for many kinds of venues: library rooms and seats, sports courts, parking bays, clinic appointments and gym sessions. A member picks a category, a venue and a bookable, holds a time slot for five minutes and confirms it. Double-booking is impossible, even under heavy concurrent load and with several service instances running.

> **Status: Phase 1, M1.5.** Config server, Eureka, gateway, identity and booking services run together. identity-service registers users (and logs them in at once), issues RS256 access tokens plus a refresh-token cookie (sliding 7 days, logout ends it) and answers `/api/v1/me`. The gateway refuses requests without a valid access token, and booking-service checks the token and the role again itself, without calling identity-service. booking-service owns `booking_db` (Flyway schema, demo catalog) and the database itself rejects double-booking and overlapping bookings per member. booking-service serves the catalog (categories, venues, bookables with their effective rules, countries and time zones) and slot availability for a date (M1.4). Members hold a slot for 5 minutes, confirm it and cancel it; admins can cancel any booking (M1.5). The full set of booking rules and the hold-expiry job come next (M1.6 is the first frontend slice, M1.7 the rules). Progress and verified test results are logged in [docs/PROGRESS.md](docs/PROGRESS.md).

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

The same slot for another member answers `409 SLOT_TAKEN`; the same member at the same time on another bookable answers `409 MEMBER_OVERLAP`. Both come from PostgreSQL exclusion constraints, not from a Java check, so they hold even when two requests arrive together. Admins can cancel any booking (`cancelledBy: ADMIN`) but book only for themselves. In this milestone a hold checks only that the times are in order, the bookable and venue are active, the bookable is booked by time slot, and the start is in the future; opening hours, the slot grid, durations, the horizon and the per-member limit are checked from M1.7, and the cancellation cutoff too.

### Access-token keys

identity-service signs access tokens with an RSA private key (`JWT_PRIVATE_KEY` in `infra/.env`, never committed); every service verifies them with the matching public key from the config repo (`gridreserve.jwt.public-keys` in `application.yml`, keyed by `kid`). identity-service refuses to start if the two do not match. To make your own dev pair, follow the commands in `infra/.env.example`, put the public key in your config repo clone, and set `gridreserve.jwt.signing.key-id` in `identity-service.yml` to its `kid`.

Build and run all backend tests (Docker must be running):

```bash
cd backend
./mvnw -B verify
```

`verify` runs unit tests (`*Test`, Surefire) and integration tests against a real PostgreSQL 16 in a Testcontainer (`*IT`, Failsafe). The schema guarantees are proven in `services/booking-service/src/test/java/com/gridreserve/booking/schema/BookingConstraintsIT.java`; registration, login and tokens in `services/identity-service/src/test/java/com/gridreserve/identity/auth/AuthFlowIT.java`; the refresh cookie, logout and the CSRF check in `RefreshFlowIT.java` next to it. The gateway's token check is in `platform/api-gateway/src/test/java/com/gridreserve/gateway/GatewayTokenCheckIT.java`, booking-service's in `services/booking-service/src/test/java/com/gridreserve/booking/config/BookingSecurityIT.java`. The catalog and availability are covered by `CatalogIT` and `AvailabilityIT` (integration) and `RuleResolverTest` and `LocalSlotGridTest` (unit, including daylight-saving days). Hold, confirm and cancel are covered by `booking/BookingLifecycleIT` (integration, with a movable clock so holds can run out without waiting) and `BookingRulesTest`, `BookingRetryTest` and `ConstraintViolationsTest` (unit). Tests generate throwaway keys; no key is needed to run them.

All demo data is fictional.

## License

[MIT](LICENSE)
