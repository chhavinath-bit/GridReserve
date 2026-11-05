# GridReserve

One booking platform for many kinds of venues: library rooms and seats, sports courts, parking bays, clinic appointments and gym sessions. A member picks a category, a venue and a bookable, holds a time slot for five minutes and confirms it. Double-booking is impossible, even under heavy concurrent load and with several service instances running.

> **Status: planning complete, Phase 0 (repositories).** No application code yet. Progress and verified test results are logged in [docs/PROGRESS.md](docs/PROGRESS.md).

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

## Repository layout (planned)

```
backend/     Java: platform/ (config-server, discovery-server, api-gateway), services/, libs/
frontend/    React app
infra/       Docker Compose and PostgreSQL init
scripts/     Helper scripts
docs/        Progress log
```

Runtime configuration lives in a separate repository: [gridreserve-config](https://github.com/chhavinath-bit/gridreserve-config).

## Getting started

Setup instructions arrive with the first runnable milestone (M1.1). All demo data is fictional.

## License

[MIT](LICENSE)
