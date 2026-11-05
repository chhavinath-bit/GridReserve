# GridReserve

> Version 0.3 (draft). Product spec. No application code exists yet.
> Changes from v0.2 (owner decisions, 2026-11-04): libraries support individually bookable seats (EXCLUSIVE, Phase 1) and unassigned reading halls (CAPACITY, Phase 2); bookable types per category carry labels and default rules; venues store a country plus a time zone; microservices architecture from the start (Config Server, Eureka discovery, API Gateway, identity and booking services) on Spring Boot 4 and Spring Cloud; fault-tolerance patterns (retry, rate limiter, bulkhead, circuit breaker); `dev` and `prod` profiles; refresh-token login; section 10 open questions answered.
> Changes from v0.1: renamed SlotLock to GridReserve; replaced "resource" with Category / Venue / Bookable; merged five booking domains into one app; added a two-model booking engine (EXCLUSIVE and CAPACITY); replaced the "postponed" list with a phased roadmap in which nothing is dropped.
> Items marked **[ASSUMPTION]** need the owner's confirmation (section 10).

## 1. Project overview

GridReserve is one booking platform for many kinds of places. A user chooses a **Category** from a dropdown, then a **Venue**, then books a **Bookable** in a time slot.

| Category | Example Venue | Example Bookable (UI label) | Booking model |
|---|---|---|---|
| Study | Central Library | Group room 4 ("Room"); Seat 14 in the quiet zone ("Seat") | EXCLUSIVE |
| Study | Riverside Reading Hall | Reading hall, 40 unassigned seats per session ("Hall") | CAPACITY (Phase 2) |
| Sports | Sunrise Turf | Court B ("Court") | EXCLUSIVE |
| Parking | Palm Apartments | Visitor bay 7 ("Bay") | EXCLUSIVE |
| Clinic | CityCare Clinic | Dr. Rao, 30-minute slot ("Doctor") | EXCLUSIVE |
| Gym | IronWorks | Personal training with Asha ("Trainer", EXCLUSIVE); Morning HIIT class, 12 seats ("Class", CAPACITY) | both |

The product has one engine and many categories. Each category has one or more **bookable types** (for example Study has Room, Seat and Hall). Categories and bookable types are configuration and seed data (labels, booking model, default slot length, cancellation cutoff, quotas), not separate code paths.

**Two booking models**
- **EXCLUSIVE:** one booking per time range. Enforced by a PostgreSQL exclusion constraint on (bookable, time range) for active bookings. A specific library seat is an EXCLUSIVE bookable.
- **CAPACITY:** up to N seats per session (gym group classes, reading halls with unassigned seating). An exclusion constraint cannot count, so this uses an atomic seat-claim (`UPDATE ... WHERE booked < capacity` or a row lock). Added in Phase 2.

**Architecture:** microservices from the start. Spring Cloud Config Server (configuration from a separate Git repository, `dev` and `prod` profiles, `@RefreshScope`), Eureka service discovery with client-side load balancing, an API Gateway as the single entry point, an identity service, and a booking service that owns the whole booking core (catalog, availability, bookings, capacity, waitlist) in one database so that its guarantees stay transactional. Later phases add payment, membership and notification services. Services exchange events through a transactional outbox pushed over HTTP (no message broker). Fault tolerance uses retry, rate limiter, bulkhead and circuit breaker patterns.

**Stack (fixed):** React + TypeScript; Java 21 + Spring Boot 4, Spring Cloud (Config, Netflix Eureka, Gateway, LoadBalancer, CircuitBreaker with Resilience4j), Spring Security + JWT, Spring Data JPA; PostgreSQL + Flyway; Redis (Phase 4); JUnit 5, Mockito, Testcontainers; Docker + Docker Compose; GitHub Actions. Details in TECH_STACK.md.

**Naming rule:** the word "resource" is not used in the UI, API paths or code. Internally the entity is `Bookable`; the UI label comes from its bookable type.

## 2. Problem statement

Shared spaces and services (study rooms, library seats, courts, parking bays, clinic slots, gym classes) are usually booked through spreadsheets, chat messages or separate clumsy tools. This causes double-bookings, unclear rules (hours, duration, cancellation), no-shows and unfair access. One system that enforces the rules and makes double-booking impossible, across several kinds of venue, solves a concrete and familiar problem.

## 3. Target users

- **Members:** students, residents, patients, players and gym-goers who need to reserve quickly and see what is free.
- **Admins:** managers who maintain categories, venues, bookables and rules.
- **Venue managers (Phase 6):** staff scoped to their own venues only.
- **Secondary audience (portfolio):** interviewers evaluating backend design, database modelling, concurrency, distributed-system patterns, testing and deployment.

## 4. Main goals

**Product goals**
1. Double-booking is impossible, even under simultaneous requests and with several service instances running.
2. Limited-seat sessions never oversell, even for the last seat.
3. Rules (hours, slot grid, duration, buffer, cancellation cutoff, per-member limits) are enforced consistently per category and bookable type.
4. A member completes the main journey (pick category, venue, slot; hold; confirm) in a few clicks.
5. A failing or slow supporting service degrades features gracefully instead of breaking booking.

**Portfolio goals**
1. Demonstrate relational modelling, transactions, constraints, concurrency, auth, validation, microservice patterns (discovery, central config, gateway, resilience, outbox), testing and deployment in Java/Spring Boot.
2. Every phase ends deployed, tested and documented, so the project is always showable.
3. Every design decision must be explainable in an interview.

## 5. Core features (Phase 1, the foundation)

1. **Authentication:** register and log in with email and password (BCrypt); short-lived JWT access tokens plus a rotating refresh token in an httpOnly cookie; handled by the identity service.
2. **Catalog:** categories and bookable types (seeded), venues and bookables managed by admins. Each venue has a country and a time zone; each bookable type defines its UI label, booking model and default rules.
3. **Category dropdown** in the frontend, driving venue and bookable lists.
4. **Availability:** for a bookable and date, which slots are free or taken.
5. **Booking lifecycle (EXCLUSIVE):** create a HELD booking (expires after 5 minutes), CONFIRM it, or CANCEL it before the cutoff. Statuses: HELD, CONFIRMED, CANCELLED, EXPIRED. Covers rooms, seats, courts, bays, doctors and trainers.
6. **Conflict protection:** exclusion constraint; conflicts return `409 Conflict`.
7. **Rule validation:** operating hours, slot grid, maximum duration, buffer time, cancellation cutoff (default 2 hours before start), per-member active-booking limit (default 3). Rules are columns on category, bookable type and bookable, not a rules engine.
8. **Hold expiry:** scheduled job marks expired holds EXPIRED.
9. **Consistent error format** and input validation on every endpoint, across all services.
10. **Frontend:** login/register, category dropdown, venue and bookable browsing, slot picker, my bookings, admin screens; loading, error and empty states.
11. **Platform:** Config Server (separate Git config repo, `dev`/`prod` profiles, refreshable configuration), Eureka discovery, API Gateway with load balancing, fault-tolerance patterns (retry, rate limiter, bulkhead, circuit breaker) on calls between services.
12. **Quality and delivery:** unit tests, Testcontainers integration tests, the 50-parallel-request concurrency test (also with two booking-service instances), resilience tests, GitHub Actions CI, Docker Compose, live deployment, README with architecture diagram, seeded demo data for all five categories (fictional venues and people only).
13. **Last milestones of Phase 1:** idempotency key for booking creation (unique per user and key) and an audit log of booking changes. If Phase 1 runs long, these move to the start of Phase 2; they are not dropped.

## 6. User roles and permissions

| Action | Anonymous | MEMBER | ADMIN |
|---|---|---|---|
| Register / log in | Yes | Yes | Yes |
| Browse categories, venues, bookables, availability | No | Yes | Yes |
| Create a booking (hold) | No | Yes | Yes (for self) |
| Confirm / cancel own booking | No | Yes | Yes |
| Cancel any booking | No | No | Yes |
| View own bookings | No | Yes | Yes |
| View all bookings | No | No | Yes |
| Manage venues and bookables | No | No | Yes |
| Deactivate (close) a bookable or venue, even with future bookings | No | No | Yes: stops new bookings, existing ones unchanged |

Admins can cancel any booking but cannot book on behalf of members. VENUE_MANAGER (Phase 6) is an admin scoped to specific venues. Ownership is checked in the service layer; roles are enforced with Spring Security in every service (and checked early at the gateway).

## 7. Main user journeys

1. **Book a slot:** log in, choose a category, choose a venue, pick a bookable (a room, a seat, a court…) and a free slot, a hold is created, confirm within 5 minutes, see it in "My bookings".
2. **Lose a race:** two members pick the same slot at once; one succeeds, the other sees a clear "just taken" message and picks another.
3. **Cancel:** cancel from "My bookings" before the cutoff; the slot is released.
4. **Hold expires:** an unconfirmed hold is released after 5 minutes and the member is told.
5. **Admin setup:** admin creates a venue (country and time zone) and bookables with rules and edits or deactivates them later.
6. **Admin oversight:** admin views all bookings and cancels one when needed.
7. **Degraded mode:** if a supporting service is down (for example the identity service while a member already holds a valid token), booking keeps working and only the affected feature degrades.
8. **Later phases:** book a gym class or reading-hall seat (Phase 2); join a waitlist and accept an offer (Phase 3); pay at checkout and get a refund on cancellation (Phase 5); use membership credits (Phase 6); book a recurring slot (Phase 7); join a walk-in queue (Phase 8).

## 8. Scope of the MVP (Phase 1 only)

Included: section 5, roles MEMBER and ADMIN, EXCLUSIVE booking model only, all five categories seeded and selectable in the dropdown, platform services (config server, discovery server, API gateway), identity service, booking service, one PostgreSQL server with a separate database per service, one frontend.

Success criteria: the 50-parallel-request test passes (exactly 1 success, 49 conflicts for one slot), including through the gateway with two booking-service instances; resilience tests pass; CI is green; the app is deployed; and the README explains how to run and test it.

## 9. Phased roadmap (nothing is dropped)

Every phase ends with: all tests passing, CI green, deployed, README updated. No phase begins until the previous one is complete.

| Phase | Name | Adds | Key engineering |
|---|---|---|---|
| 1 | Core engine and platform | Section 5 | Exclusion constraint, holds, rules, concurrency test; config server, discovery, gateway, resilience patterns |
| 2 | Capacity model | Gym group classes and library reading halls with N seats | Atomic seat claim, last-seat contention test, concurrent cancellations |
| 3 | Waitlist and offers | Waitlist for both models; auto-offer to next person on cancellation; offers expire | Promotion under concurrency, expiring offers |
| 4 | Redis | Availability cache (cache-aside, short TTL, invalidated on change); rate limiting on login and booking at the gateway (build the cache first) | Cache invalidation, distributed rate limits, graceful fallback when Redis is down |
| 5 | Payments and refunds | Payment service; checkout holds; mock payment provider (no real money, no real card data); webhook with idempotency; refunds by cancellation policy; transactional outbox with HTTP push | Idempotent webhooks, payment state machine, reliable events between services |
| 6 | Memberships and venue managers | Membership service: plans, per-plan limits and class credits; VENUE_MANAGER role scoped to venues | Quota logic across services (saga), scoped authorization |
| 7 | Recurring and no-shows | Recurring availability and recurring bookings; no-show marking and penalties | Date/recurrence handling, time zones and DST |
| 8 | Queue, notifications, polish | Walk-in queue (clinic); notification service with in-app and simulated email notifications; admin analytics; OpenAPI docs | Queue ordering, event-driven notifications |

Hard rule across all phases: PostgreSQL is the source of truth. Redis never decides a booking, and the app must work correctly if Redis is down. A booking decision is always made inside one booking-service database transaction, never across services.

## 10. Assumptions and open questions

**Decisions already made**
- Capacity was "info only" in v0.1. Superseded: Phase 1 is EXCLUSIVE only; real capacity arrives in Phase 2.
- Cancellation cutoff default: 2 hours before start (configurable per category).
- Max 3 active future bookings per member (configurable per category).
- Admins can cancel any booking but not book on behalf of others.
- (2026-11-05, replaces "deactivation with future bookings is blocked with 409") Deactivating a bookable or venue is a soft close: new bookings stop immediately, existing bookings are not changed, waitlists for it are closed. Bookables and venues are never deleted.
- Redis does both cache and rate limiting (Phase 4; cache first).
- Seeded admin and member accounts are listed in the README as demo-only credentials.
- A Git repo and a public GitHub remote are used from the start (a monorepo for all services and the frontend, plus a separate config repo).
- Deploy host is chosen later, host-agnostic until then (Dockerfiles + environment variables); decide before the first Phase 1 deployment, and verify current free-tier limits at that time (several JVMs now need to run).
- (2026-11-04) Microservices from the start; the booking core stays in one service and one database; events via transactional outbox + HTTP push; config in a separate Git repo; `@RefreshScope` refreshed per instance; Spring Boot 4.
- (2026-11-04) Library study: specific seats are EXCLUSIVE bookables (Phase 1); unassigned reading halls are CAPACITY (Phase 2).
- (2026-11-04) Time zones: each venue stores a country code and an IANA time zone; the country is used to suggest valid zones, the venue's zone is used for all rules.

**Answers to the v0.2 open questions (2026-10-26)**
1. Slot length and duration defaults per category: see REQUIREMENTS.md §2.
2. A member can never hold two overlapping active bookings, in any category.
3. Payments stay fully mocked.
4. Waitlist offers last 10 minutes by default, configurable per category.
5. Recurring series skip conflicting occurrences and report them.

**Assumptions [ASSUMPTION]**
- Self-registration creates MEMBER accounts; ADMIN accounts are seeded.
- Hold duration is 5 minutes in Phase 1 (checkout holds in Phase 5 may differ).
- Times are stored in UTC and displayed in the venue's time zone.
- All demo data is fictional. No real patient, payment or personal data is used.
- Expected load is small; the microservice split is for architecture practice and independent deployability, not for scale.
