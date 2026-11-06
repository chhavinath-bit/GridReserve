# GridReserve

One booking platform for many kinds of venues: library rooms and seats, sports courts, parking bays, clinic appointments and gym sessions. A member picks a category, a venue and a bookable, holds a time slot for five minutes and confirms it. Double-booking is impossible, even under heavy concurrent load and with several service instances running.

> **Status: Phase 1, M1.3a.** Config server, Eureka, gateway, identity and booking services run together. identity-service registers users, logs them in with RS256 access tokens and answers `/api/v1/me`; booking-service owns `booking_db` (Flyway schema, demo catalog) and the database itself rejects double-booking and overlapping bookings per member. Refresh tokens (M1.3b) and token checks in the gateway and booking-service (M1.3c) are next; no booking API yet. Progress and verified test results are logged in [docs/PROGRESS.md](docs/PROGRESS.md).

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

# Register a new member (always MEMBER; the email is stored lower-cased)
curl -s localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"carol@example.test","password":"carol-pass-1","displayName":"Carol"}'
```

### Access-token keys

identity-service signs access tokens with an RSA private key (`JWT_PRIVATE_KEY` in `infra/.env`, never committed); every service verifies them with the matching public key from the config repo (`gridreserve.jwt.public-keys` in `application.yml`, keyed by `kid`). identity-service refuses to start if the two do not match. To make your own dev pair, follow the commands in `infra/.env.example`, put the public key in your config repo clone, and set `gridreserve.jwt.signing.key-id` in `identity-service.yml` to its `kid`.

Build and run all backend tests (Docker must be running):

```bash
cd backend
./mvnw -B verify
```

`verify` runs unit tests (`*Test`, Surefire) and integration tests against a real PostgreSQL 16 in a Testcontainer (`*IT`, Failsafe). The schema guarantees are proven in `services/booking-service/src/test/java/com/gridreserve/booking/schema/BookingConstraintsIT.java`; registration, login and tokens in `services/identity-service/src/test/java/com/gridreserve/identity/auth/AuthFlowIT.java`. Tests generate throwaway keys; no key is needed to run them.

All demo data is fictional.

## License

[MIT](LICENSE)
