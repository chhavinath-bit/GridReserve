# GridReserve

One booking platform for many kinds of venues: library rooms and seats, sports courts, parking bays, clinic appointments and gym sessions. A member picks a category, a venue and a bookable, holds a time slot for five minutes and confirms it. Double-booking is impossible, even under heavy concurrent load and with several service instances running.

> **Status: Phase 1, platform skeleton (M1.1).** Config server, Eureka, gateway and two empty services run together; no booking features yet. Progress and verified test results are logged in [docs/PROGRESS.md](docs/PROGRESS.md).

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

Build and run all backend tests (Docker must be running):

```bash
cd backend
./mvnw -B verify
```

All demo data is fictional.

## License

[MIT](LICENSE)
