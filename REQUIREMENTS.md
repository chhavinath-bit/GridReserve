# GridReserve — Requirements

> Version 0.17 (draft). Derived from [PROJECT.md](PROJECT.md) v0.3. No application code exists yet.
> Changes from v0.16 (M1.12, 2026-11-11, D-44): IDEM-03 defines "same body" as the same bookable and times (normalized), and replays carry `Idempotent-Replayed: true`; IDEM-06 says a replayed refusal has the same status, code and message (new timestamp and request ID); IDEM-07 says an expired key counts as unused before the cleanup deletes it; FR-AUD-01 names the actor rules. CONC-08 proven.
> Changes from v0.15 (M1.11, 2026-11-11, D-43): FR-CAT-07/08 say a venue's category and a bookable's venue are fixed after creation, updates carry the `version` read (FR-CAT-15), and CAPACITY bookables can't be created before Phase 2 (`422 WRONG_BOOKING_MODEL`); FR-CAT-09 is checked on the effective rules; FR-ADM-01 gives the response shape (`memberDetailsAvailable`); CONC-09 proven.
> Changes from v0.14 (M1.10, 2026-11-10, D-15): PLAT-07 verified with the deploy setup (restart with backoff, fail fast without the Config Server); new NFR-OPS-05 (nightly encrypted off-site backups with a restore drill) and NFR-OPS-06 (every release is one commit's images, smoke-tested from outside; rollback by redeploying an older commit).
> Changes from v0.13 (M1.9, 2026-11-07, D-42): FR-ROLE-01 adds that user endpoints require role MEMBER or ADMIN (a service token, role SERVICE, is refused there) and actuator endpoints beyond health/info require ADMIN or SERVICE; NFR-TEST-03 names WireMock (D-28 approved).
> Changes from v0.12 (M1.8, owner decision 2026-11-07, D-41): CONC-09 says hold creation locks the bookable `FOR UPDATE` (not `FOR SHARE`); CONC-09 is proven in M1.11 and CONC-08 in M1.12, where the deactivate endpoint and the idempotency key arrive.
> Changes from v0.11 (M1.7, owner decisions 2026-11-07, D-40): BR-07 counts the horizon in **whole local days** like availability (a start on a local date before today + horizon), so the API and the picker always agree; FR-BK-07 adds `cancellableUntil`; BR-08 states that bookings on inactive bookables of the category count too and that a hold that has run out does not.
> Changes from v0.10 (M1.5, 2026-11-07, D-11): FR-BK-03 and FR-BK-04 state what happens to a booking that is not in the expected state: confirming an expired hold answers `409 HOLD_EXPIRED` (also when it was already marked EXPIRED), any other state `409 INVALID_STATE`; a hold that has run out cannot be cancelled (it is recorded `EXPIRED`, `409 INVALID_STATE`); `cancelledBy` is `ADMIN` only when an admin cancels another member's booking.
> Changes from v0.9 (M1.4, owner decisions 2026-11-06, D-39): availability works in whole local days (today to today + horizon − 1; later dates `422 BEYOND_HORIZON`, past dates all `PAST`); categories and countries are unpaginated reference lists (API-04); availability for a CAPACITY bookable returns `422 WRONG_BOOKING_MODEL` until Phase 2 (FR-AV-04, FR-AV-07).
> Changes from v0.8 (owner decision, 2026-11-06, D-35 withdrawn): **sign-in with Google and GitHub is removed from Phase 1** and kept as an optional extension (US-30, FR-AUTH-15..19, FS-42..44, A-29 marked; not built). Email and password stay the only sign-in.
> Changes from v0.7 (M1.3c, 2026-11-06, D-38): FR-ROLE-01 states which gateway paths are public and that the auth endpoints ignore the `Authorization` header (an expired access token sent along must not block a refresh).
> Changes from v0.6 (owner decision, 2026-11-06, D-37): the refresh token is **not rotated**. One opaque token per login, stored hashed, with a sliding 7-day expiry; logout deletes it (FR-AUTH-09..11); reuse detection and token families are dropped (FS-39 withdrawn). PROJECT.md §5 still says "rotating refresh token"; this owner decision replaces it, PROJECT.md itself is unchanged.
> Changes from v0.5 (owner decisions, 2026-11-06): registering starts a session like login (FR-AUTH-01, D-36, built in M1.3b); **sign-in with Google and GitHub** added to Phase 1 (US-30, FR-AUTH-15..19, FS-42..44, D-35, built in M1.3d). This extends PROJECT.md §5 (email and password) by owner decision; PROJECT.md itself is unchanged.
> Changes from v0.4 (M1.1 spike S6, 2026-11-05): PLAT-07 startup behavior is fail fast + container restart with backoff (no in-app retry).
> Changes from v0.3 (owner review M0.3, 2026-11-05): Phase 1 assumptions confirmed; **per-weekday opening hours** in Phase 1 (A-06); booking horizon **7 days** (A-07); Gym Trainer **45-minute sessions + 15-minute buffer on a 15-minute grid** (A-08); waitlist promotion style (D-03) decided.
> Changes from v0.2 (owner decision 2026-11-05): deactivating a bookable or venue is a **soft close** — it stops new bookings and never touches existing ones; the `409 BOOKABLE_HAS_FUTURE_BOOKINGS` rule is removed (FR-CAT-10/13/16, US-09, CONC-09, FS-13, WL-11).
> If this file and PROJECT.md disagree, PROJECT.md wins until the owner decides otherwise.
> Items marked **[ASSUMPTION]** are listed in section 17 for the owner's confirmation.
> Changes from v0.1: bookable types (library seats, reading halls); venue country + time zone; refresh-token login; microservice platform requirements (§12), resilience requirements (§13), inter-service events (§14); fixes from ARCHITECTURE.md v0.1 §17 (stale holds of the member, app clock, idempotency lease, `STALE_UPDATE`, in-transaction waitlist promotion). Existing requirement IDs are unchanged.

## 1. Purpose, scope and conventions

This document turns the product spec into **specific, testable requirements**. Every requirement has a stable ID so that tests, commits and pull requests can cite it (for example a test named `rejectsOverlappingHold_BR10`).

**Keywords.** "shall" / "must" = mandatory. "should" = recommended, deviation must be explained. "may" = optional.

**ID scheme**

| Prefix | Area |
|---|---|
| `FR-AUTH`, `FR-CAT`, `FR-AV`, `FR-BK`, `FR-EXP`, `FR-ADM`, `FR-AUD`, `FR-UI`, `FR-SEED`, `FR-ROLE` | Functional requirements (section 5) |
| `BR` | Booking rules, EXCLUSIVE model (section 6) |
| `CAP` | Capacity rules (section 7) |
| `CXL` | Cancellation policies (section 8) |
| `WL` | Waitlist and offers (section 9) |
| `IDEM` | Idempotency (section 10) |
| `CONC` | Concurrency guarantees (section 11) |
| `PLAT` | Microservice platform (section 12) |
| `RES` | Resilience and fault tolerance (section 13) |
| `EVT` | Events between services (section 14) |
| `API` | API-level expectations (section 15) |
| `NFR` | Non-functional requirements (section 16) |
| `FS` | Failure scenarios (section 18) |
| `US` | User stories (section 4) |
| `A` | Assumptions (section 17) |

**Phase tags.** `[P1]` … `[P8]` mark the phase in which a requirement must be met (see PROJECT.md §9). A requirement without a tag is `[P1]`. Phases are done strictly in order.

**Vocabulary.** Category, Venue, Bookable. The word for a generic "thing that can be booked" is always **Bookable** in code, API paths, UI and docs. Each bookable has a **bookable type** (Room, Seat, Hall, Court, Bay, Doctor, Trainer, Class) that belongs to a category and supplies the UI label, the booking model and default rules. A **slot** is one cell of a bookable's time grid. A **session** `[P2]` is a scheduled occurrence of a CAPACITY bookable with N seats. An **active** booking is one in status `HELD` or `CONFIRMED`.

**Services.** "Gateway" = API gateway; "identity service" = users and authentication; "booking service" = catalog, availability, bookings, capacity, waitlist, audit, idempotency. See ARCHITECTURE.md.

**Time.** All instants are stored and transferred in UTC. "Local time" always means the venue's IANA time zone (for example `Asia/Kolkata`).

## 2. Decisions log

### 2.1 Answers to PROJECT.md v0.2 open questions (owner, 2026-10-26)

| # | Question | Decision |
|---|---|---|
| Q1 | Default slot length and maximum duration | Per bookable type, table 2.3. A bookable may override them. |
| Q2 | May a member hold overlapping bookings? | **No, never, in any category.** |
| Q3 | Phase 5 payment provider | **Fully mocked** behind a `PaymentProvider` interface, signed mock webhooks. |
| Q4 | Waitlist offer duration | **10 minutes** by default, per-category column. |
| Q5 | Recurring series with a conflicting occurrence | **Skip and report.** |

### 2.2 Decisions of 2026-11-04

| # | Topic | Decision |
|---|---|---|
| D1 | Library seats | Specific seats are **EXCLUSIVE** bookables (type Seat, P1). Unassigned reading halls are **CAPACITY** (type Hall, P2). Group rooms stay EXCLUSIVE (type Room). |
| D2 | Architecture | **Microservices from the start**: config server, discovery server (Eureka), API gateway, identity service, booking service; payment (P5), membership (P6), notification (P8) services later. |
| D3 | Service split | The **booking core stays in one service and one database** (catalog, availability, booking, capacity, waitlist, audit, idempotency, walk-in queue). |
| D4 | Events between services | **Transactional outbox + HTTP push**, no message broker. |
| D5 | Configuration | Spring Cloud Config Server backed by a **separate Git repository**; profiles `dev` and `prod`; `@RefreshScope` beans refreshed **per instance** by an admin script. |
| D6 | Repository layout | **Monorepo**, Maven multi-module, plus the separate config repo. |
| D7 | Framework version | **Spring Boot 4.x** with the matching Spring Cloud release train (2025.1.x at the time of writing; verify at scaffold). |
| D8 | Time zones | Each venue stores an ISO country code **and** an IANA time zone; the zone must belong to the country; rules use the venue's zone. |
| D9 | Fault tolerance | Retry, rate limiter, bulkhead and circuit breaker (Resilience4j) on calls between services and at the gateway. |
| D10 | Login | In-memory access token + **refresh token in an httpOnly cookie** (decided 2026-10-26; not rotated since 2026-11-06, D-37). |

### 2.3 Bookable types and defaults (seed data)

| Category | Type | UI label | Model | Slot | Min | Max | Buffer after | Phase |
|---|---|---|---|---|---|---|---|---|
| Study | ROOM | Room | EXCLUSIVE | 30 min | 30 min | 3 h | 0 | P1 |
| Study | SEAT | Seat | EXCLUSIVE | 60 min | 60 min | 4 h | 0 | P1 |
| Study | HALL | Hall | CAPACITY | sessions | — | — | — | P2 |
| Sports | COURT | Court | EXCLUSIVE | 60 min | 60 min | 2 h | 0 | P1 |
| Parking | BAY | Bay | EXCLUSIVE | 60 min | 60 min | 12 h | 0 | P1 |
| Clinic | DOCTOR | Doctor | EXCLUSIVE | 30 min | 30 min | 30 min | 0 | P1 |
| Gym | TRAINER | Trainer | EXCLUSIVE | 15 min | 45 min | 45 min (exactly one session) | 15 min | P1 |
| Gym | CLASS | Class | CAPACITY | sessions | — | — | — | P2 |

**Category-level defaults** (all categories): cancellation cutoff 2 h, max 3 active future bookings per member per category, waitlist offer TTL 10 min, booking horizon 7 days.

### 2.4 Defaults already decided (CLAUDE.md, PROJECT.md)

- Hold duration: 5 minutes.
- Admins may cancel any booking but may not book on behalf of members.
- Deactivating a bookable or venue is a **soft close** (2026-11-05, replaces the earlier 409 rule): it stops new bookings immediately; existing HELD and CONFIRMED bookings are not changed; Phase 3 waitlists for it are closed. Bookables and venues are never deleted.
- Redis (Phase 4) does cache and rate limiting, cache first, and is never the source of truth.
- Demo data is fictional; seeded demo credentials are documented in the README as demo-only.
- (2026-11-06) Registering logs the user in (D-36, M1.3b). Sign-in with Google and GitHub was planned as M1.3d (D-35) and then dropped from Phase 1 the same day; it is an optional extension.

## 3. User roles and permissions

| Role | Description | Phase |
|---|---|---|
| Anonymous | Not logged in. | P1 |
| MEMBER | Self-registered user who books. | P1 |
| ADMIN | Seeded account that manages the catalog and oversees bookings. | P1 |
| VENUE_MANAGER | An ADMIN whose powers are scoped to assigned venues. | P6 |

### 3.1 Permission matrix

| Action | Anonymous | MEMBER | ADMIN | VENUE_MANAGER `[P6]` |
|---|---|---|---|---|
| Register, log in, refresh, log out | Yes | Yes | Yes | Yes |
| Browse categories, venues, bookables, availability | No (401) | Yes | Yes | Yes |
| Create a hold for self | No (401) | Yes | Yes | Yes |
| Create a booking for another user | No | No | **No** | No |
| Confirm / cancel own booking | No | Yes | Yes | Yes |
| View own bookings | No | Yes | Yes | Yes |
| View all bookings | No | No (403) | Yes | Own venues only |
| Cancel any booking (ignores cutoff) | No | No (403) | Yes | Own venues only |
| Create / edit / deactivate venues and bookables | No | No (403) | Yes | Own venues only (no venue creation) |
| Edit categories and bookable types | No | No | No in P1 (seeded, read-only) | No |
| View audit log of a booking | No | No | Yes | Own venues only |
| Trigger configuration refresh | No | No | Yes (admin script, §12) | No |

### 3.2 Authorization rules

- **FR-ROLE-01** Roles shall be enforced by Spring Security in **every service** at the endpoint level; the gateway shall also reject requests without a valid access token to protected routes. Ownership (is this my booking?) shall be checked in the owning service's service layer. Public at the gateway: `/api/v1/auth/**`, the gateway's health; every other path needs a valid token. The auth endpoints ignore any `Authorization` header (D-38). User endpoints require role MEMBER or ADMIN; a service token (role SERVICE, D-19) is accepted only on `/internal/**` and actuator endpoints, which beyond health/info require ADMIN or SERVICE (v0.14).
- **FR-ROLE-02** A request without a valid token to a protected endpoint shall return `401 UNAUTHENTICATED`.
- **FR-ROLE-03** A MEMBER calling an ADMIN endpoint shall receive `403 FORBIDDEN`.
- **FR-ROLE-04** A MEMBER reading, confirming or cancelling another member's booking shall receive `404 NOT_FOUND`. **[ASSUMPTION A-03]**
- **FR-ROLE-05** No booking-creation endpoint shall accept a target user ID; the booking owner is always the authenticated user (`sub` of the access token).
- **FR-ROLE-06** `[P6]` A VENUE_MANAGER acting on a venue that is not assigned to them shall receive `403 FORBIDDEN`.

## 4. User stories

Acceptance criteria use Given / When / Then. Each story links to the requirements it exercises.

### Phase 1

**US-01 Register.** As a visitor, I want to create an account with email and password so that I can book.
- Given a new email and a valid password, when I register, then I get `201`, a MEMBER account is created, and I am logged in (access token + refresh-token cookie) without logging in again.
- Given an email that already exists (case-insensitive), when I register, then I get `409 EMAIL_TAKEN`.
- Links: FR-AUTH-01..04.

**US-02 Log in and stay logged in.** As a member, I want to log in and not be logged out on every page refresh.
- Given correct credentials, then I get `200` with an access token that expires in 15 minutes and a refresh-token cookie.
- When my access token expires or I reload the page, the app obtains a new access token through `POST /auth/refresh` without asking for my password.
- Given a wrong password or unknown email, then I get `401 INVALID_CREDENTIALS` with the same message in both cases.
- Links: FR-AUTH-05..12.

**US-03 Browse by category.** As a member, I want to pick a category from a dropdown and then a venue so that I only see relevant bookables.
- Given the five seeded categories, when I open the dropdown, then I see Study, Sports, Parking, Clinic and Gym.
- When I choose "Study" and "Central Library", then I see group rooms labelled "Room" and seats labelled "Seat".
- Links: FR-CAT-01..06, FR-UI-02.

**US-04 See availability.** As a member, I want to see free and taken slots for a bookable on a date so that I can pick a time.
- Given a bookable with one confirmed booking 10:00–11:00 local, when I request that date, then the 10:00 slot is `TAKEN` and the other slots inside operating hours are `FREE` (or `PAST` if already started).
- Links: FR-AV-01..06.

**US-05 Hold and confirm.** As a member, I want to hold a slot and confirm it within 5 minutes.
- Given a free slot, when I create a booking, then I get `201` with status `HELD` and `expiresAt` = now + 5 min.
- When I confirm before `expiresAt`, then the status becomes `CONFIRMED` and the booking appears in "My bookings".
- Links: FR-BK-01..06, BR-*.

**US-06 Lose a race.** As a member, if someone else takes my slot at the same moment, I want a clear message so that I can pick another slot.
- Given two members requesting the same slot at the same time, then exactly one gets `201` and the other gets `409 SLOT_TAKEN`, and the UI shows "This slot was just taken" and refreshes availability.
- Links: CONC-01, CONC-10, FR-UI-05.

**US-07 Cancel.** As a member, I want to cancel a confirmed booking before the cutoff so that the slot is released.
- Given a confirmed booking that starts in 3 hours (cutoff 2 h), when I cancel, then I get `200` with status `CANCELLED`, and the slot shows `FREE`.
- Given it starts in 1 hour, when I cancel, then I get `422 CUTOFF_PASSED`.
- Links: CXL-01..06.

**US-08 Hold expires.** As a member, if I do not confirm within 5 minutes, my hold is released and I am told.
- Given a hold created at T, when I confirm at T + 5 min 1 s, then I get `409 HOLD_EXPIRED` and the booking's status is `EXPIRED`.
- Links: FR-EXP-01..04.

**US-09 Admin setup.** As an admin, I want to create a venue and bookables and edit their rules.
- Given country `IN`, when I create a venue, then the time zone `Asia/Kolkata` is suggested; given country `AU`, I must choose one of Australia's zones; a zone from another country is rejected with `400`.
- Given valid input, when I create a bookable of a type, then it inherits the type defaults unless I set overrides.
- Given a bookable with a confirmed future booking, when I deactivate it, then I get `200`, members can no longer find or book it (`422 BOOKABLE_INACTIVE`), and the existing booking stays CONFIRMED and visible to its owner and to admins.
- Links: FR-CAT-03, FR-CAT-07..16.

**US-10 Admin oversight.** As an admin, I want to see all bookings and cancel one when needed.
- When I list bookings filtered by venue and date, then I see bookings of all members with their email, paginated.
- If the identity service is down, the list still loads, shows member IDs instead of emails, and says so (RES-06).
- When I cancel a member's booking 30 minutes before start, then it succeeds (the cutoff does not apply to admins), and the audit log records me as the actor.
- Links: FR-ADM-01..03, FR-AUD-01..03, RES-06.

**US-11 Safe retry.** As a member on a flaky connection, I want retrying "Book" not to create two holds.
- Given I sent `POST /bookings` with `Idempotency-Key: k1` and got `201`, when I resend the same request with `k1`, then I get the same `201` body and no new booking is created.
- Links: IDEM-01..07.

**US-12 Keep booking when a supporting service fails.** As a member who is already logged in, I want to keep booking even if the identity service is temporarily down.
- Given a valid access token and the identity service stopped, when I hold and confirm a slot, then both succeed.
- Links: RES-07, FS-32.

**US-13 Configuration change without redeploy.** As the owner, I want to change a refreshable setting (for example a resilience threshold) in the config repository and apply it to all running instances without restarting them.
- Given a changed property in the config repo, when the refresh script runs, then every registered instance reports the new value at `/actuator/env` (admin only) and behaves accordingly.
- Links: PLAT-04..06.

### Later phases

| ID | Phase | Story | Key acceptance criterion |
|---|---|---|---|
| US-20 | P2 | As a gym member or student, I want to book a seat in a group class or reading-hall session. | Session with capacity 12 and 11 booked: one request succeeds, the next gets `409 SESSION_FULL`. |
| US-21 | P3 | As a member, I want to join a waitlist for a full slot or session and get an offer when it frees up. | After a cancellation, the first person on the waitlist gets an offer that lasts 10 min; accepting it creates a `CONFIRMED` booking. |
| US-22 | P4 | As a member, I want availability to load fast. | Repeated availability reads within the TTL are served from cache; a booking change invalidates it. With Redis stopped, results are still correct. |
| US-23 | P5 | As a member, I want to pay at checkout and be refunded when I cancel before the cutoff. | Mock webhook `payment.succeeded` confirms the booking; cancellation before cutoff produces exactly one full refund, even if the event is delivered twice. |
| US-24 | P6 | As a member with a plan, I want to use class credits. | Booking a class seat consumes one credit; with 0 credits I get `422 NO_CREDITS`; a failed booking returns the credit. |
| US-25 | P6 | As a venue manager, I want to manage only my venues. | Editing a bookable in another venue returns `403`. |
| US-26 | P7 | As a member, I want to book the same court every Tuesday for 8 weeks. | 2 Tuesdays are taken: 6 bookings are created and 2 are reported as skipped with reason `SLOT_TAKEN`. |
| US-27 | P7 | As an admin, I want to mark no-shows. | After 3 no-shows in 30 days, the member cannot book for 7 days. **[A-21]** |
| US-28 | P8 | As a clinic patient, I want to join a walk-in queue and see my position. | Positions are strictly FIFO; "call next" pops the earliest entry. |
| US-30 | Ext (not planned) | As a visitor, I want to sign in with my Google or GitHub account so that I need no new password. | A verified provider email creates a MEMBER account or links to the existing account with that email; an unverified email is refused; I land in the app logged in, with no token in any URL. Links: FR-AUTH-15..19. |
| US-29 | P8 | As a member, I want to be notified about expiring holds, offers and cancellations. | An in-app notification exists; a simulated email is recorded (not sent); each event produces exactly one notification even if delivered twice. |

## 5. Functional requirements

### 5.1 Authentication (FR-AUTH) — identity service

- **FR-AUTH-01** `POST /api/v1/auth/register` shall create a MEMBER account from `{email, password, displayName}` and start a session exactly like login: `201 {accessToken, tokenType: "Bearer", expiresIn}` plus the refresh-token cookie (FR-AUTH-05, FR-AUTH-09). The client reads the user with `GET /api/v1/me`. (Changed 2026-11-06, D-36; until M1.3b it returns the user DTO.)
- **FR-AUTH-02** Email shall be valid, at most 254 characters, stored lower-cased, and unique (case-insensitive). Duplicate → `409 EMAIL_TAKEN`.
- **FR-AUTH-03** Password shall be 8–72 characters (72 is the BCrypt input limit). Passwords shall be stored only as BCrypt hashes (cost ≥ 10).
- **FR-AUTH-04** Self-registration shall never create an ADMIN. ADMIN accounts are seeded by migration. **[A-01]**
- **FR-AUTH-05** `POST /api/v1/auth/login` shall return `200 {accessToken, tokenType: "Bearer", expiresIn}` and set the refresh-token cookie (FR-AUTH-09).
- **FR-AUTH-06** Access tokens shall be JWTs signed by the identity service with an asymmetric key (RS256), containing `sub` (user ID), `role`, `iat`, `exp` and `kid`, with a lifetime of 15 minutes. Every other service verifies them locally with the public key, without calling the identity service. **[A-02, A-27]**
- **FR-AUTH-07** Invalid credentials shall return `401 INVALID_CREDENTIALS` with an identical message for unknown email and wrong password.
- **FR-AUTH-08** `GET /api/v1/me` shall return the current user's ID, email, display name and role.
- **FR-AUTH-09** Each login (and each registration) shall create one refresh token: an opaque random value (256 bits), stored only as a SHA-256 hash in one row per login, sent as a cookie with `HttpOnly`, `Secure` (prod), `SameSite=Strict`, `Path=/api/v1/auth`. It expires after **7 days without use** (sliding expiry). Expired rows are deleted when anyone logs in. **[A-24, D-37]**
- **FR-AUTH-10** `POST /api/v1/auth/refresh` shall return `200 {accessToken, tokenType, expiresIn}`, move the token's expiry to now + 7 days and send the same cookie again with a fresh `Max-Age`. The token value does not change, so parallel refreshes (several tabs) all succeed. A missing, unknown, logged-out or expired token → `401 REFRESH_TOKEN_INVALID`.
- **FR-AUTH-11** `POST /api/v1/auth/logout` shall delete the current refresh token's row (other devices stay logged in), clear the cookie and return `204`, also when there is no cookie or the token is already gone.
- **FR-AUTH-12** Refresh and logout shall be protected against CSRF: the request must carry an `Origin` header equal to the configured frontend origin **and** the header `X-GridReserve-Client: web`; otherwise `403 FORBIDDEN`. **[A-24]**
- **FR-AUTH-13** A disabled user shall not get a new access token: refresh checks `enabled` every time and answers `401 REFRESH_TOKEN_INVALID`. Their existing access token stops working at the latest when it expires (≤ 15 min). **[A-02]**
- **FR-AUTH-14** `[P4]` Login and booking creation shall be rate limited at the gateway (NFR-SEC-06).
- **FR-AUTH-15** `[optional extension, not planned (owner, 2026-11-06)]` Members shall be able to sign in with **Google** (OpenID Connect) and **GitHub** (OAuth2): `GET /api/v1/auth/oauth2/authorization/{provider}` starts the flow, the provider returns to `GET /api/v1/auth/oauth2/callback/{provider}`. A provider whose client ID and secret are not configured is off (`404`). Client secrets come only from environment variables. (D-35)
- **FR-AUTH-16** `[optional extension, not planned (owner, 2026-11-06)]` After a successful callback the identity service shall pick the account in this order: (1) an existing link for (provider, provider user ID); (2) if the provider reports the email as **verified** and an account with that email exists, link the provider to it (the role is unchanged); (3) if the email is verified and new, create a MEMBER with the provider's display name and no password. Without a verified email nothing is created or linked (FS-42). Google: `email_verified` claim; GitHub: the primary address marked verified in `/user/emails`.
- **FR-AUTH-17** `[optional extension, not planned (owner, 2026-11-06)]` On success the identity service shall start a session exactly like login (refresh-token cookie, FR-AUTH-09) and redirect to `{frontendOrigin}/auth/callback`; the frontend then gets the access token through `POST /api/v1/auth/refresh`. No token ever appears in a URL. On any failure (cancelled at the provider, unverified email, disabled account, state mismatch, provider error) it redirects to `{frontendOrigin}/auth/callback?error=<code>` and sets no session cookie.
- **FR-AUTH-18** `[optional extension, not planned (owner, 2026-11-06)]` The OAuth2 authorization request (state, PKCE verifier, OIDC nonce) shall be kept in a short-lived (≤ 10 min) `HttpOnly` cookie limited to `/api/v1/auth/oauth2`, with `SameSite=Lax` (the provider's redirect back is a cross-site navigation), not in a server session, so any identity instance can finish the flow. It is deleted when the callback is handled.
- **FR-AUTH-19** `[optional extension, not planned (owner, 2026-11-06)]` Accounts created through a provider have no password (`password_hash` is null); a disabled account cannot sign in through a provider. Setting a password later is out of scope (A-29).

### 5.2 Catalog (FR-CAT) — booking service

- **FR-CAT-01** The system shall provide exactly five seeded categories in P1 (Study, Sports, Parking, Clinic, Gym) and the bookable types in table 2.3 (CAPACITY types are seeded but not bookable until P2). Categories and types are read-only through the API in P1.
- **FR-CAT-02** Each category shall have: `code`, `name`, `cancellationCutoffMinutes`, `maxActiveBookingsPerMember`, `offerTtlMinutes` `[P3]`, `bookingHorizonDays`. Each bookable type shall have: `code`, `category`, `label` (UI), `bookingModel`, `slotMinutes`, `minDurationMinutes`, `maxDurationMinutes`, `bufferMinutes`.
- **FR-CAT-03** Each venue shall have: name, category, address line (fictional), `countryCode` (ISO 3166-1 alpha-2), `timeZone` (IANA), **weekly opening hours** (per ISO weekday Monday–Sunday: one opening interval `opensAt`–`closesAt` in local time, or closed; a weekday with no interval is closed), and an `active` flag. The time zone **must be one of the zones of that country** (FR-CAT-14); otherwise `400 VALIDATION_FAILED`.
- **FR-CAT-04** Each bookable shall have: venue, bookable type, name, `active` flag, and **nullable overrides** for `slotMinutes`, `minDurationMinutes`, `maxDurationMinutes`, `bufferMinutes`, and optional **own weekly opening hours**. A null override means "inherit"; if a bookable defines its own weekly hours, they replace the venue's hours entirely (no per-day mixing).
- **FR-CAT-05** The effective rule value shall be resolved as: bookable override → venue weekly hours (hours only) → bookable type default (slot, durations, buffer) → category default (cutoff, limit, offer TTL, horizon). One resolver shall be used everywhere (no per-category or per-type code paths).
- **FR-CAT-06** `GET /api/v1/categories` (with their bookable types), `GET /api/v1/venues?categoryId=`, `GET /api/v1/venues/{id}`, `GET /api/v1/venues/{venueId}/bookables` and `GET /api/v1/bookables/{id}` shall return only active venues/bookables to MEMBERs; ADMINs may pass `includeInactive=true`. Each bookable includes its type's `label` and `bookingModel`.
- **FR-CAT-07** ADMIN shall create and update venues (`POST /api/v1/venues`, `PUT /api/v1/venues/{id}`). An update replaces name, address, country, zone and weekly hours, and carries the `version` the admin read (FR-CAT-15); the category is fixed after creation, because the venue's bookables have types of that category.
- **FR-CAT-08** ADMIN shall create and update bookables (`POST /api/v1/venues/{venueId}/bookables`, `PUT /api/v1/bookables/{id}`). The bookable type must belong to the venue's category (else `400` on `bookableTypeId`); the venue is fixed after creation; an update carries the `version` read. Until Phase 2 only EXCLUSIVE types can be added (`422 WRONG_BOOKING_MODEL`, BR-09). Rule fields are optional overrides; own weekly hours are optional (none = the venue's).
- **FR-CAT-09** Validation: per weekday at most one interval, with opening time < closing time (closing may be 24:00); weekdays are ISO 1–7 without duplicates; `slotMinutes` ∈ {15, 30, 60}; `minDuration` and `maxDuration` must be multiples of the slot length, with `slotMinutes ≤ minDuration ≤ maxDuration ≤ 24 h`; `bufferMinutes` 0–120. These hold for the **effective** values (an override, else the type's default), so an override that makes a default invalid is refused too. Invalid → `400 VALIDATION_FAILED` with every field error at once.
- **FR-CAT-10** `POST /api/v1/bookables/{id}/deactivate` shall set `active=false` and return `200` (also when already inactive), **whether or not future bookings exist** (soft close). From that commit on, new holds and seat claims on it are rejected with `422 BOOKABLE_INACTIVE` (BR-06), and it is hidden from members in catalog and availability (FR-CAT-06; availability for an inactive bookable → `404` for members). Existing HELD and CONFIRMED bookings are **not** cancelled, expired or changed: owners can still confirm open holds within their expiry, see their bookings, and cancel under the normal policy. The response includes `activeFutureBookings` (count) so the admin knows what is still scheduled.
- **FR-CAT-11** `POST /api/v1/bookables/{id}/activate` shall set `active=true`.
- **FR-CAT-12** Changing a bookable's rules shall not alter existing bookings; new rules apply to new bookings only.
- **FR-CAT-13** Venue deactivation (`POST /api/v1/venues/{id}/deactivate`) shall follow the same soft-close rule: no new bookings on any of its bookables (BR-06 checks venue and bookable), existing bookings unchanged. Bookables keep their own `active` flag, so reactivating the venue restores exactly the bookables that were active before.
- **FR-CAT-14** The country-to-zone list shall be seed data derived from the IANA time zone database (`zone1970.tab` / `zone.tab`), not hard-coded logic. `GET /api/v1/countries` and `GET /api/v1/countries/{code}/time-zones` shall serve it. If a country has exactly one zone, the admin UI pre-fills it.
- **FR-CAT-15** Concurrent admin edits of the same venue or bookable shall not overwrite each other silently: a stale `version` → `409 STALE_UPDATE`.
- **FR-CAT-16** Bookables and venues shall never be deleted through the API (past bookings and the audit log reference them); deactivation is the way to retire them, and `POST /…/activate` reverses it.

### 5.3 Availability (FR-AV) — booking service

- **FR-AV-01** `GET /api/v1/bookables/{id}/availability?date=YYYY-MM-DD` shall return all slots of that **local** date within that weekday's opening hours. On a closed weekday it returns `200` with `closed: true` and no slots.
- **FR-AV-02** Each slot shall contain `start` and `end` (UTC ISO-8601), `localStart` and `localEnd` (venue time), and `status` ∈ {`FREE`, `TAKEN`, `PAST`}.
- **FR-AV-03** A slot is `TAKEN` if it overlaps the occupied range (`[start, end + buffer)`) of any active booking on that bookable. A `HELD` booking whose `expiresAt` has passed shall be treated as free even if the expiry job has not run yet.
- **FR-AV-04** A slot whose start is ≤ now is `PAST`. Dates beyond the booking horizon (BR-07) return `422 BEYOND_HORIZON`: availability works in whole local days in the venue's zone, so today through today + `bookingHorizonDays` − 1 are served (every slot on them starts before now + horizon) and later dates are refused. A past date returns `200` with every slot `PAST` (D-39).
- **FR-AV-05** Availability shall never reveal who holds a slot.
- **FR-AV-07** Availability for a CAPACITY bookable shall return `422 WRONG_BOOKING_MODEL` until Phase 2 (BR-09); CAPACITY bookables are still listed in the catalog with their `bookingModel` (D-39).
- **FR-AV-06** The response also includes the venue time zone, the bookable type label and the effective rules (slot length, min/max duration) so the UI can build the picker without hard-coded values.
- **FR-AV-07** `[P2]` For CAPACITY bookables, `GET /api/v1/bookables/{id}/sessions?date=` shall return sessions with `capacity`, `booked` and `seatsLeft`.

### 5.4 Booking lifecycle, EXCLUSIVE (FR-BK) — booking service

- **FR-BK-01** `POST /api/v1/bookings` with `{bookableId, start, end}` shall create a booking in status `HELD` with `expiresAt = now + 5 min`, returning `201` and a `Location` header.
- **FR-BK-02** All rules in section 6 shall be validated before insert; the database constraints (BR-10, BR-11) are the final guard.
- **FR-BK-03** `POST /api/v1/bookings/{id}/confirm` shall move `HELD → CONFIRMED` if `now < expiresAt`; otherwise mark it `EXPIRED` and return `409 HOLD_EXPIRED`. Only the owner may confirm (anyone else, admins included, gets `404`). Confirming a booking already `EXPIRED` also answers `409 HOLD_EXPIRED`; confirming one in any other state answers `409 INVALID_STATE`.
- **FR-BK-04** `POST /api/v1/bookings/{id}/cancel` shall move `HELD` or `CONFIRMED` → `CANCELLED` according to section 8, with an optional `reason` (max 200 chars). A `HELD` booking whose hold has run out is not cancelled: it is recorded as `EXPIRED` and the answer is `409 INVALID_STATE` (CXL-01). `cancelledBy` is `MEMBER` when the owner cancels (also an admin cancelling their own booking) and `ADMIN` when an admin cancels another member's booking.
- **FR-BK-05** `GET /api/v1/bookings/me?status=&from=&to=&page=&size=` shall list the caller's bookings, newest start first by default.
- **FR-BK-06** `GET /api/v1/bookings/{id}` shall return a booking the caller owns (or any booking, for ADMIN).
- **FR-BK-07** Every booking response shall include: `id`, `bookableId`, `bookableName`, `bookableLabel`, `venueName`, `venueTimeZone`, `start`, `end`, `status`, `expiresAt` (if HELD), `createdAt`, `cancelledAt`, `cancelledBy` (`MEMBER`/`ADMIN`/`SYSTEM`), `cancellableUntil` (the last moment the owner can cancel: `expiresAt` while HELD, `start − cancellationCutoff` when CONFIRMED, null otherwise; added in v0.12).
- **FR-BK-08** Allowed status transitions are only those in BR-13. Any other transition returns `409 INVALID_STATE`.
- **FR-BK-09** Booking operations shall not call any other service synchronously inside the booking transaction (RES-07).

### 5.5 Hold expiry (FR-EXP)

- **FR-EXP-01** A scheduled job in the booking service shall run every 30 seconds **[A-05]** and set `status = EXPIRED` for `HELD` bookings with `expiresAt ≤ now`, using conditional updates (`… WHERE status = 'HELD' AND expires_at <= :now`), where `:now` comes from the application clock. It shall be safe with several booking-service instances running (`FOR UPDATE SKIP LOCKED`).
- **FR-EXP-02** Before inserting a new hold, the booking service shall expire stale holds that would block it: those that overlap the requested range **on the bookable**, and those of the **requesting member** that overlap the range (BR-11).
- **FR-EXP-03** Expiry shall write an audit entry with actor `SYSTEM`.
- **FR-EXP-04** Correctness shall not depend on the job: confirm checks `expiresAt` (FR-BK-03), availability ignores stale holds (FR-AV-03), and FR-EXP-02 frees stale holds at insert.

### 5.6 Admin oversight (FR-ADM)

- **FR-ADM-01** `GET /api/v1/admin/bookings?venueId=&bookableId=&status=&from=&to=&page=&size=` shall list all bookings (ADMIN only), enriched with member email and display name from the identity service (batched: one call per page, never inside a transaction; with fallback per RES-06). Newest start first; `from`/`to` filter on the start time. Each item is `{booking, memberId, memberEmail, memberDisplayName}`; the page adds `memberDetailsAvailable` (false = identity unreachable, names null, FS-33).
- **FR-ADM-02** ADMIN shall cancel any booking through `POST /api/v1/bookings/{id}/cancel`; the cutoff does not apply; `cancelledBy = ADMIN`.
- **FR-ADM-03** An ADMIN booking for themselves follows exactly the member rules.

### 5.7 Audit log (FR-AUD) — last milestone of P1

- **FR-AUD-01** Every booking state change (create, confirm, cancel, expire; later offer/accept/no-show) shall append one audit row: `bookingId`, `fromStatus`, `toStatus`, `actorUserId` (null for SYSTEM), `actorType`, `reason`, `occurredAt` (UTC). Actor: MEMBER for a change to one's own booking (an admin booking for themself too, FR-ADM-03), ADMIN for an admin cancelling someone else's, SYSTEM for every expiry (job, sweep at hold creation, late confirm or cancel). Bookings created before M1.12 have no history (no backfill).
- **FR-AUD-02** The audit row shall be written in the same transaction as the change.
- **FR-AUD-03** Audit rows are append-only. `GET /api/v1/admin/bookings/{id}/audit` returns them in time order.

### 5.8 Seed data (FR-SEED)

- **FR-SEED-01** Seed data shall include all five categories and the bookable types in table 2.3; at least one venue per category matching PROJECT.md §1 (Central Library with ≥ 2 rooms and ≥ 10 seats, Sunrise Turf, Palm Apartments, CityCare Clinic, IronWorks); at least two bookables per other venue; Riverside Reading Hall and IronWorks classes in P2. All names are fictional. Venues carry a country code, a valid zone and weekly opening hours; at least one venue is closed on one weekday (e.g. CityCare Clinic on Sunday) and at least one bookable has its own weekly hours.
- **FR-SEED-02** The identity service seed shall include one ADMIN and at least two MEMBER demo accounts, documented in the README as demo-only, with BCrypt hashes (not plaintext) in the migration.
- **FR-SEED-03** No real personal, patient or payment data shall exist anywhere in the repositories (application and config).

### 5.9 Frontend (FR-UI)

- **FR-UI-01** Pages: Login, Register, Browse (category dropdown → venue list → bookable list grouped by type), Slot picker, My bookings, Admin: venues, bookables, all bookings. Routing with React Router.
- **FR-UI-02** The bookable label shown everywhere comes from the bookable type (no hard-coded "Room", "Seat", …).
- **FR-UI-03** Every data view shall have a loading state, an error state (showing the API `message`) and an empty state.
- **FR-UI-04** The slot picker shall display times in the venue's time zone and show the zone name.
- **FR-UI-05** On `409 SLOT_TAKEN` or `MEMBER_OVERLAP` the UI shall show a specific message and refresh availability.
- **FR-UI-06** A HELD booking shall show a countdown to `expiresAt`; at zero the UI shows "Hold expired" and offers to pick again.
- **FR-UI-07** The access token is kept in memory only. On app start and on `401 TOKEN_EXPIRED` the client calls `POST /auth/refresh` once; if that fails, it returns to Login and, after login, back to the page the user was on.
- **FR-UI-08** Admin screens shall be hidden from MEMBERs; the backend remains the actual guard (FR-ROLE-03).
- **FR-UI-09** The admin venue form shall offer a country select and, for that country, its time zones (pre-filled when there is one).
- **FR-UI-10** On `503 SERVICE_UNAVAILABLE` or `504 UPSTREAM_TIMEOUT` the UI shall show a "temporarily unavailable, please retry" message without losing the user's selection.

## 6. Booking rules, EXCLUSIVE model (BR)

All rule values come from the resolver in FR-CAT-05. "Local" = venue time zone.

| ID | Rule | Violation |
|---|---|---|
| **BR-01** | `start < end`, both valid UTC instants. | `400 VALIDATION_FAILED` |
| **BR-02** | `start` and `end` fall within the opening hours of **that local weekday** (bookable's own weekly hours if set, else the venue's), on **one local date**; a closed weekday rejects every booking. A booking may not cross local midnight in P1. **[A-06]** | `422 OUTSIDE_OPERATING_HOURS` |
| **BR-03** | Grid alignment: local start minus **that day's** opening time is a whole multiple of `slotMinutes`; duration is a whole multiple of `slotMinutes`. | `422 OFF_GRID` |
| **BR-04** | `minDuration ≤ end − start ≤ maxDuration`. | `422 DURATION_INVALID` |
| **BR-05** | `start > now`. | `422 START_IN_PAST` |
| **BR-06** | Bookable and its venue are active. | `422 BOOKABLE_INACTIVE` |
| **BR-07** | The start's **local date** is before today + `bookingHorizonDays` at the venue (default 7: today and the next 6 days), the same whole-day horizon availability uses (D-39, D-40). **[A-07]** | `422 BEYOND_HORIZON` |
| **BR-08** | Per-member limit: the member's active bookings with `end > now` **in the same category** must be < `maxActiveBookingsPerMember` (default 3) before the new one. HELD counts while its hold is live; a hold that has run out does not. Bookings on inactive bookables of the category count too. Checked while holding a row lock on the member (CONC-05). | `422 MEMBER_LIMIT_REACHED` |
| **BR-09** | Booking model check: `POST /bookings` with `start/end` only works for EXCLUSIVE bookables. | `422 WRONG_BOOKING_MODEL` |
| **BR-10** | **No double-booking (DB).** Exclusion constraint on `(bookable_id WITH =, tstzrange(start_time, occupied_until, '[)') WITH &&)` for active rows; `occupied_until = end + buffer`, stored at insert. | `409 SLOT_TAKEN` |
| **BR-11** | **No member overlap (DB).** Exclusion constraint on `(member_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&)` for active rows, across all categories and both models. | `409 MEMBER_OVERLAP` |
| **BR-12** | Ranges are half-open `[start, end)`: back-to-back bookings do not overlap. | (test) |
| **BR-13** | Transitions: `HELD → CONFIRMED`, `HELD → CANCELLED`, `HELD → EXPIRED`, `CONFIRMED → CANCELLED`; `[P7]` `CONFIRMED → NO_SHOW` after end. `CANCELLED`, `EXPIRED`, `NO_SHOW` are terminal. | `409 INVALID_STATE` |
| **BR-14** | Rule order (first failure is returned): BR-01, BR-06, BR-09, BR-05, BR-07, BR-02, BR-03, BR-04, BR-08, then insert (BR-10, BR-11). | — |

**Buffer note (Gym Trainer).** 15-minute grid, 45-minute sessions, 15-minute buffer: a session 10:00–10:45 occupies 10:00–11:00, so the next session can start at 11:00 (one client per hour); starts at 10:15–10:45 are rejected by the overlap constraint. See **[A-08]**.

## 7. Capacity rules `[P2]` (CAP)

Applies to Gym classes (type CLASS) and library reading halls (type HALL).

- **CAP-01** A CAPACITY bookable has **sessions**: `bookableId`, `start`, `end`, `capacity` (1–500), `booked` (0..capacity). Sessions are created by ADMIN. Reading-hall sessions default to 2-hour blocks within opening hours **[A-26]**.
- **CAP-02** `POST /api/v1/bookings` with `{sessionId}` shall claim a seat with one atomic statement: `UPDATE session SET booked = booked + 1 WHERE id = :id AND booked < capacity`. Zero rows → `409 SESSION_FULL`. Read-then-write in Java is forbidden.
- **CAP-03** A DB check constraint shall enforce `0 ≤ booked ≤ capacity`.
- **CAP-04** One active seat per member per session (partial unique index) → `409 ALREADY_BOOKED`.
- **CAP-05** Seat bookings use the same lifecycle (HELD 5 min → CONFIRMED / CANCELLED / EXPIRED), cutoff and per-member limit rules.
- **CAP-06** Cancelling or expiring a seat shall release it in the same transaction as the status change: `UPDATE session SET booked = booked - 1 WHERE id = :id AND booked > 0`.
- **CAP-07** BR-11 also applies to seat bookings (same booking table with `session_id`; BR-10 applies only to rows without a session).
- **CAP-08** Reducing capacity below `booked` → `409 CAPACITY_BELOW_BOOKED`. Cancelling a session cancels its seat bookings (`cancelledBy = ADMIN`).
- **CAP-09** The P1 data model shall already make CAP-07 possible (nullable `session_id`, model on bookable type).

## 8. Cancellation policies (CXL)

- **CXL-01** A member may cancel their own `HELD` booking at any time before it expires.
- **CXL-02** A member may cancel their own `CONFIRMED` booking while `now < start − cancellationCutoff` (default 120 min, per category). Otherwise → `422 CUTOFF_PASSED`.
- **CXL-03** An ADMIN may cancel any `HELD` or `CONFIRMED` booking at any time before its end.
- **CXL-04** Cancelling a booking whose end has passed → `409 INVALID_STATE`.
- **CXL-05** Cancellation frees the slot (or seat) immediately in the same transaction.
- **CXL-06** Cancelling an already cancelled booking → `409 INVALID_STATE`.
- **CXL-07** `[P3]` A cancellation (or expiry) triggers waitlist promotion **inside the same booking-service transaction** (WL-04), so a promotion can never be lost. (ARCHITECTURE.md D-03, confirmed by the owner 2026-11-05.)
- **CXL-08** `[P5]` Refund policy: cancellation by the member before the cutoff = full refund; by ADMIN = full refund; no partial refunds **[A-15]**. Refunds are triggered through an outbox event (EVT).
- **CXL-09** `[P7]` A no-show cannot be cancelled afterwards.

## 9. Waitlist and offers `[P3]` (WL)

- **WL-01** A member may join a waitlist only when the target is full: an EXCLUSIVE slot (exact `bookableId`, `start`, `end`) that is TAKEN, or a session with `seatsLeft = 0`. Otherwise → `422 NOT_FULL`.
- **WL-02** One waiting entry per member per target → `409 ALREADY_ON_WAITLIST`. Maximum 5 waiting entries per member **[A-10]**.
- **WL-03** Order is FIFO by `joinedAt` (ties by entry ID).
- **WL-04** When a slot or seat is freed, the system shall offer it to the first eligible waiting member: a booking in status `HELD` with `expiresAt = min(now + offerTtl, start)` linked to the entry; the entry becomes `OFFERED`.
- **WL-05** "Eligible" = still passes BR-08 and BR-11 at offer time. Ineligible entries are marked `SKIPPED` with a reason; the next entry is tried.
- **WL-06** `POST /api/v1/offers/{id}/accept` → booking `CONFIRMED`, entry `ACCEPTED`. `POST /api/v1/offers/{id}/decline` → booking cancelled, next person offered.
- **WL-07** If an offer expires, the entry becomes `EXPIRED` and the next eligible entry gets an offer (cascade).
- **WL-08** No offers are made when `start − now < 5 min`; remaining entries become `EXPIRED` **[A-11]**.
- **WL-09** A member may leave a waitlist (`DELETE /api/v1/waitlist/{id}`) while `WAITING`.
- **WL-10** Exactly one offer per freed slot/seat, even under concurrent cancellations and with several instances.
- **WL-11** Deactivating a bookable (or its venue) shall mark all its `WAITING` entries `EXPIRED` with reason `BOOKABLE_INACTIVE`, in the same transaction; joining a waitlist for an inactive bookable → `422 BOOKABLE_INACTIVE`; a slot freed on an inactive bookable is never offered. Already `OFFERED` entries keep their offer (it is an existing HELD booking, FR-CAT-10).

## 10. Idempotency (IDEM)

- **IDEM-01** `POST /api/v1/bookings` shall accept an optional `Idempotency-Key` header (1–64 characters, `[A-Za-z0-9_-]`). The frontend shall always send one (UUID v4) per user intent.
- **IDEM-02** Keys are unique per `(userId, key)` (DB unique constraint).
- **IDEM-03** Same key + **same request** (SHA-256 of the normalized `bookableId`, `start`, `end`, so field order, spacing or `…:00.000Z` vs `…:00Z` don't matter) → the stored status code and body of the first response, with the first `Location` and the header `Idempotent-Replayed: true`; no new booking.
- **IDEM-04** Same key + **different body** → `422 IDEMPOTENCY_KEY_REUSED`.
- **IDEM-05** First request still in progress → `409 IDEMPOTENCY_IN_PROGRESS`. An in-progress record carries a **lease** (30 s); if the lease has expired (the first request crashed), a retry takes over the key and proceeds.
- **IDEM-06** Only completed responses are stored (2xx and 4xx business errors). A replayed 4xx has the first status, `code` and `message`; its `timestamp` and `requestId` are the replay's. On a 5xx the record is removed so the client may retry with the same key. Requests refused before the key is claimed (`400` for times or the key's format) are not stored.
- **IDEM-07** Keys expire after 24 hours **[A-12]**: an expired key counts as unused at once; an hourly job deletes them.
- **IDEM-08** `[P5]` Payment webhooks are deduplicated by provider `eventId`; a duplicate returns `200` with no effect.
- **IDEM-09** `[P7]` Creating a recurring series accepts an `Idempotency-Key` with the same semantics.
- **IDEM-10** Confirm and cancel are idempotent in effect but not in response: a second confirm → `409 INVALID_STATE`.
- **IDEM-11** `[P5]` Every event delivered between services carries an event ID; receivers process each ID at most once (EVT-04).
- **IDEM-12** `[P6]` Service-to-service commands that change state (for example "reserve a credit") carry an idempotency key so they can be retried safely (RES-03).

## 11. Concurrency guarantees (CONC)

Each guarantee has an integration test against real PostgreSQL (Testcontainers).

| ID | Guarantee | Test |
|---|---|---|
| **CONC-01** | 50 parallel `POST /bookings` for the same bookable and slot by 50 members → exactly **1 × 201** and **49 × 409 SLOT_TAKEN**; exactly one active row. | 50 threads with a `CountDownLatch` start gate. |
| **CONC-02** | Overlapping but different ranges in parallel → at most one succeeds. | Parallel test. |
| **CONC-03** | One member, 10 parallel holds on 10 bookables at the same time → at most one succeeds; others `409 MEMBER_OVERLAP`. | Parallel test. |
| **CONC-04** | Confirm racing with expiry → exactly one wins (conditional UPDATE). | Repeated race test. |
| **CONC-05** | One member, 10 parallel holds in one category at different times, limit 3 → exactly 3 succeed (member row lock). | Parallel test. |
| **CONC-06** `[P2]` | Last seat: capacity 12 with 11 booked, 20 parallel claims → exactly 1 success, `booked = 12`; 50 claims on an empty session of 12 → exactly 12. | Parallel test. |
| **CONC-07** `[P2/P3]` | 12 parallel cancellations of 12 seats → `booked = 0`, never negative; one waitlist offer per freed seat. | Parallel test. |
| **CONC-08** | Two parallel requests with the same `Idempotency-Key` → exactly one booking. Proven in M1.12 (`IdempotencyIT`: 20 parallel requests, one key, 20 repetitions: one created, the rest replayed or `409 IDEMPOTENCY_IN_PROGRESS`). | Parallel test. |
| **CONC-09** | Deactivation racing with new holds → every hold either committed **before** the deactivation (and stays valid) or is rejected with `422 BOOKABLE_INACTIVE`; no hold is created after the deactivation commits. Hold creation locks the venue `FOR SHARE` and the bookable `FOR UPDATE` and re-checks `active`; deactivation locks it `FOR UPDATE` (D-41). Proven in M1.11 (`DeactivationRaceIT`: 20 parallel holds + 1 deactivation of the bookable or of its venue, 20 repetitions each, commit order checked with PostgreSQL commit timestamps). | Race test |
| **CONC-10** | CONC-01 repeated **through the gateway with two booking-service instances** registered in Eureka and load-balanced → still exactly 1 × 201 and 49 × 409. | System test with Docker Compose (or Testcontainers with two app containers). |

General rule: correctness comes from database constraints, conditional updates and row locks — never from Java `synchronized`, in-memory locks, Redis, or which instance handles a request.

## 12. Microservice platform (PLAT)

- **PLAT-01** All client traffic shall enter through the **API gateway** under `/api/v1/**`. In `prod`, services other than the gateway shall not be reachable from the internet.
- **PLAT-02** Every service shall register with the **Eureka** discovery server under a stable service ID (`identity-service`, `booking-service`, …). The gateway and service-to-service clients shall address services by ID (`lb://booking-service`) with client-side load balancing (round robin).
- **PLAT-03** Any service may run several instances; adding an instance shall need no configuration change, and all CONC guarantees shall still hold (CONC-10).
- **PLAT-04** Configuration shall come from the **Config Server**, backed by a separate Git repository, using profiles `dev` and `prod` (`{service}.yml`, `{service}-dev.yml`, `{service}-prod.yml`, shared `application*.yml`). Automated tests use a `test` profile with the config client and Eureka disabled.
- **PLAT-05** Properties marked refreshable (resilience thresholds, rate-limit settings, feature toggles, CORS origins, job intervals) shall be bound with `@RefreshScope` or `@ConfigurationProperties` rebinding. A refresh script shall find every instance through Eureka and call `POST /actuator/refresh` on each; the endpoint requires authentication and is not routed through the gateway. Booking **rules** are data in the database, not configuration (NFR-MAINT-01).
- **PLAT-06** Secrets shall never be stored in the config repository; they come from environment variables (or encrypted `{cipher}` values, if approved later).
- **PLAT-07** A service shall fail fast at startup if the Config Server is unreachable (no in-app retry, so misconfiguration shows up immediately). Recovery comes from the container platform restarting it with backoff: Compose uses `restart: unless-stopped`, and so does the deploy host (`infra/compose.prod.yaml`, M1.10). Verified in M1.1 (spike S6) and again in M1.10: a service restarted while the Config Server was down failed fast, was restarted with backoff, and became healthy once the Config Server was back; a JVM killed with SIGKILL was restarted by Docker.
- **PLAT-08** Each service shall own its data: a **separate database per service** (on one PostgreSQL server). No service reads or writes another service's database.
- **PLAT-09** The gateway shall generate an `X-Request-Id` if missing; every service shall log it and forward it on outbound calls.
- **PLAT-10** Each service shall expose `/actuator/health` with liveness and readiness groups; Eureka and the gateway shall route only to instances that are UP.
- **PLAT-11** Synchronous calls between services shall be made only through load-balanced HTTP clients wrapped in the resilience policies of section 13.
- **PLAT-12** Errors from every service (and from the gateway itself) shall use the single error format (API-05).

## 13. Resilience and fault tolerance (RES)

Implemented with Resilience4j (through Spring Cloud CircuitBreaker or the Resilience4j Spring Boot integration, verified for Spring Boot 4 at scaffold). Default values are **[A-25]** and refreshable (PLAT-05).

- **RES-01 Timeouts.** Every outbound HTTP call shall have a connect timeout (1 s) and a response timeout (2 s). Gateway routes shall have a response timeout (5 s) → `504 UPSTREAM_TIMEOUT`.
- **RES-02 Circuit breaker.** One circuit breaker per downstream service (and per route at the gateway): count-based window of 20 calls, opens at ≥ 50 % failures or ≥ 50 % slow calls (> 2 s), stays open 30 s, then allows 3 trial calls. While open, calls fail fast to the fallback.
- **RES-03 Retry.** Only **idempotent** operations may be retried: GETs, event deliveries (idempotent by event ID), and commands carrying an idempotency key. Max 3 attempts, exponential backoff starting at 200 ms (× 2) with jitter. Never retry on `4xx`. The gateway retries only GET routes. `POST /bookings` is never retried by the server; the client retries with the same `Idempotency-Key`.
- **RES-04 Bulkhead.** Concurrent calls from one service to one downstream shall be limited (semaphore bulkhead, default 10, wait 0 ms). Excess calls fail fast to the fallback instead of tying up request threads.
- **RES-05 Rate limiter (per instance).** Calls to rate-sensitive downstreams (for example the mock payment provider `[P5]`) shall be limited per instance (default 20 per second). This protects downstreams; it is separate from the distributed Redis rate limits for clients (NFR-SEC-06), which remain.
- **RES-06 Fallbacks.** Each protected call shall have an explicit fallback: admin booking list without identity → member ID only plus `"memberDetailsAvailable": false`; gateway route with open circuit → `503 SERVICE_UNAVAILABLE` in the standard error format; event delivery failure → stays in the outbox for retry.
- **RES-07 Booking independence.** Hold, confirm, cancel, availability and catalog reads shall work while the identity service is down (tokens are verified locally with the public key; the booking service keeps its own `member` rows for locking).
- **RES-08 Observability.** Circuit-breaker states, retry counts, bulkhead rejections and rate-limiter rejections shall be exposed as Micrometer metrics and in `/actuator/health` details (admin only).
- **RES-09 Order.** When several patterns wrap one call, the order shall be Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → call (Resilience4j default), so a retry never hammers an open circuit.
- **RES-10 No remote call in a DB transaction.** No resilience-wrapped remote call shall run inside an open database transaction.

## 14. Events between services (EVT) `[P5]`

- **EVT-01** A service that must notify others shall write the event to its own **outbox table in the same transaction** as the change.
- **EVT-02** An outbox relay in that service shall push pending events over HTTP (`POST /internal/events`) to each subscribed service, addressed through Eureka (`lb://…`), wrapped in Retry + CircuitBreaker + Bulkhead (RES).
- **EVT-03** Delivery is at-least-once. Failed deliveries are retried with exponential backoff; after 10 attempts the event is marked `FAILED` (dead letter), counted in metrics and visible to admins.
- **EVT-04** Receivers shall record processed event IDs (`processed_event` table, unique per event ID) and apply each event at most once, in the same transaction as its effect.
- **EVT-05** Internal endpoints (`/internal/**`) shall not be routed by the gateway and shall require service-to-service authentication (mechanism: ARCHITECTURE.md D-19).
- **EVT-06** Subscriptions (event type → target services) are configuration served by the Config Server.
- **EVT-07** Handlers shall not assume ordering; they read current state from their own database.

## 15. API-level expectations (API)

- **API-01** Public base path `/api/v1` on the gateway. JSON only (`application/json`, UTF-8).
- **API-02** Requests and responses use DTOs, never JPA entities. Unknown JSON fields → `400`.
- **API-03** Instants are ISO-8601 UTC with `Z`. Dates are `YYYY-MM-DD` in venue local time. Responses with times include `venueTimeZone`.
- **API-04** Lists are paginated: `page` (0-based, default 0), `size` (default 20, max 100); response `{items, page, size, totalItems, totalPages}`; `size` or `page` out of range → `400 VALIDATION_FAILED`. Exception: the small reference lists `GET /categories` and `GET /countries` return a plain array (D-39).
- **API-05** Every error (from any service or the gateway) uses one JSON format:

```json
{
  "timestamp": "2026-11-02T04:30:00Z",
  "status": 409,
  "code": "SLOT_TAKEN",
  "message": "This slot was just taken. Please choose another time.",
  "path": "/api/v1/bookings",
  "requestId": "6f1c…",
  "fieldErrors": [ { "field": "end", "message": "must be after start" } ]
}
```

`fieldErrors` only for `400 VALIDATION_FAILED`. No stack traces, SQL or class names.

- **API-06** Status codes: `400` invalid input; `401` not authenticated; `403` wrong role/scope or failed CSRF check; `404` not found or not yours; `409` conflict with current state; `422` business rule; `429` rate limited `[P4]`; `503` service or database unavailable / circuit open; `504` upstream timeout; `500` unexpected.

### 15.1 Endpoints, Phase 1 (through the gateway)

| Method | Path | Service | Role | Success | Main errors |
|---|---|---|---|---|---|
| POST | `/auth/register` | identity | Anonymous | 201 + cookie | 400, 409 EMAIL_TAKEN |
| POST | `/auth/login` | identity | Anonymous | 200 + cookie | 400, 401 INVALID_CREDENTIALS |
| POST | `/auth/refresh` | identity | Cookie | 200 + cookie | 401 REFRESH_TOKEN_INVALID, 403 |
| POST | `/auth/logout` | identity | Cookie | 204 | 403 |
| GET | `/me` | identity | Authenticated | 200 | 401 |
| GET | `/countries`, `/countries/{code}/time-zones` | booking | Authenticated | 200 | 404 |
| GET | `/categories` | booking | Authenticated | 200 | 401 |
| GET | `/venues?categoryId=`, `/venues/{id}` | booking | Authenticated | 200 | 404 |
| POST / PUT | `/venues`, `/venues/{id}` | booking | ADMIN | 201 / 200 | 400, 403, 404, 409 STALE_UPDATE |
| POST | `/venues/{id}/deactivate`, `/venues/{id}/activate` | booking | ADMIN | 200 | 403, 404 |
| GET | `/venues/{venueId}/bookables`, `/bookables/{id}` | booking | Authenticated | 200 | 404 |
| POST / PUT | `/venues/{venueId}/bookables`, `/bookables/{id}` | booking | ADMIN | 201 / 200 | 400, 403, 404, 409 STALE_UPDATE |
| POST | `/bookables/{id}/deactivate`, `/bookables/{id}/activate` | booking | ADMIN | 200 | 403, 404 |
| GET | `/bookables/{id}/availability?date=` | booking | Authenticated | 200 | 400, 404, 422 |
| POST | `/bookings` (`Idempotency-Key`) | booking | MEMBER, ADMIN (self) | 201 | 400, 404, 409, 422 |
| GET | `/bookings/me`, `/bookings/{id}` | booking | Authenticated / owner or ADMIN | 200 | 404 |
| POST | `/bookings/{id}/confirm` | booking | Owner | 200 | 404, 409 |
| POST | `/bookings/{id}/cancel` | booking | Owner or ADMIN | 200 | 404, 409, 422 |
| GET | `/admin/bookings`, `/admin/bookings/{id}/audit` | booking | ADMIN | 200 | 403, 404 |

Not routed by the gateway: `/actuator/**` (except the gateway's own health), `/internal/**`.

### 15.2 Endpoints, later phases (indicative)

| Phase | Service | Endpoints |
|---|---|---|
| P2 | booking | `GET /bookables/{id}/sessions?date=`, `POST /bookables/{id}/sessions` (ADMIN), `PUT /sessions/{id}`, `POST /sessions/{id}/cancel`, `POST /bookings` with `{sessionId}` |
| P3 | booking | `POST /waitlist`, `GET /waitlist/me`, `DELETE /waitlist/{id}`, `POST /offers/{id}/accept`, `POST /offers/{id}/decline` |
| P4 | gateway | `429 RATE_LIMITED` with `Retry-After` on login and booking creation |
| P5 | payment | `POST /bookings/{id}/checkout`, `POST /webhooks/payments`, `GET /payments/{id}`, `GET /bookings/{id}/refunds` |
| P6 | membership / identity | `GET /plans`, `POST /memberships`, `GET /memberships/me`; `POST /admin/venues/{id}/managers` |
| P7 | booking | `POST /booking-series`, `GET /booking-series/{id}`, `POST /bookings/{id}/no-show` |
| P8 | booking / notification | `POST /venues/{id}/queue`, `GET /queue-entries/{id}`, `POST /venues/{id}/queue/next`; `GET /notifications`; `GET /admin/analytics`; OpenAPI per service, aggregated at the gateway |

### 15.3 Error code catalog

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `TOKEN_EXPIRED`, `REFRESH_TOKEN_INVALID` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` |
| 409 | `EMAIL_TAKEN`, `SLOT_TAKEN`, `MEMBER_OVERLAP`, `HOLD_EXPIRED`, `INVALID_STATE`, `IDEMPOTENCY_IN_PROGRESS`, `STALE_UPDATE`; `[P2]` `SESSION_FULL`, `ALREADY_BOOKED`, `CAPACITY_BELOW_BOOKED`; `[P3]` `ALREADY_ON_WAITLIST` |
| 422 | `OUTSIDE_OPERATING_HOURS`, `OFF_GRID`, `DURATION_INVALID`, `START_IN_PAST`, `BEYOND_HORIZON`, `BOOKABLE_INACTIVE`, `WRONG_BOOKING_MODEL`, `MEMBER_LIMIT_REACHED`, `CUTOFF_PASSED`, `IDEMPOTENCY_KEY_REUSED`; `[P3]` `NOT_FULL`; `[P6]` `NO_CREDITS`; `[P7]` `BOOKING_BLOCKED` |
| 429 | `RATE_LIMITED` `[P4]` |
| 500 | `INTERNAL_ERROR` |
| 503 | `SERVICE_UNAVAILABLE` |
| 504 | `UPSTREAM_TIMEOUT` |

## 16. Non-functional requirements (NFR)

### Performance
- **NFR-PERF-01** Availability for one bookable and date through the gateway: p95 < 300 ms on demo data, without Redis **[A-13]**.
- **NFR-PERF-02** Booking creation through the gateway: p95 < 500 ms at 10 concurrent users **[A-13]**.
- **NFR-PERF-03** CONC-01 shall complete in < 10 s in CI.
- **NFR-PERF-04** Queries used by availability and booking checks shall be index-backed (checked with `EXPLAIN`).
- **NFR-PERF-05** Each service shall run within a configured memory budget (default heap ≤ 256 MB per service **[A-28]**) so the whole system fits the chosen host.

### Security
- **NFR-SEC-01** Passwords hashed with BCrypt; never logged or returned.
- **NFR-SEC-02** The JWT **private** key exists only in the identity service (environment variable). The public key is distributed as configuration (it is not secret). DB credentials and all other secrets come from environment variables; repositories contain only `.env.example` placeholders.
- **NFR-SEC-03** APIs are stateless (no HTTP session). The only cookies are the refresh-token cookie, limited to `/api/v1/auth` and protected by FR-AUTH-12. CORS allows only the configured frontend origin (at the gateway). The frontend and gateway should be served from the same site in `prod` so `SameSite=Strict` works **[A-24]**.
- **NFR-SEC-04** All input validated; SQL parameterized only.
- **NFR-SEC-05** Error responses never contain stack traces, SQL or internal class names.
- **NFR-SEC-06** `[P4]` Distributed rate limits at the gateway, backed by Redis: login 5 per minute per (IP, email), booking creation 20 per minute per user **[A-14]**.
- **NFR-SEC-07** `[P5]` Mock webhooks verified with HMAC-SHA256; invalid → `401`.
- **NFR-SEC-08** Actuator: only `health` is public on the gateway; other actuator endpoints are admin-only and not routed publicly.
- **NFR-SEC-09** Service-to-service calls and `/internal/**` endpoints are authenticated (D-19).

### Data and correctness
- **NFR-DATA-01** PostgreSQL is the only source of truth. Redis never decides a booking.
- **NFR-DATA-02** Each service's schema is created and changed only by its own Flyway migrations; Hibernate validates and never alters.
- **NFR-DATA-03** All timestamps are `timestamptz` and handled as UTC `Instant`.
- **NFR-DATA-04** Double-booking and member overlap are prevented by DB constraints in the booking database, not only by Java checks.
- **NFR-DATA-05** A booking decision never spans more than one service's database transaction.

### Reliability
- **NFR-REL-01** `[P4]` If Redis is down, every endpoint still returns correct results (cache bypassed; gateway rate limiting fails open with a logged warning). Verified by a test with Redis stopped.
- **NFR-REL-02** If a service's database is unreachable, that service returns `503` and reports readiness `DOWN`.
- **NFR-REL-03** Correctness does not depend on the hold-expiry scheduler running on time.
- **NFR-REL-04** The failure of a non-core service (identity for already-logged-in users, notification, payment for non-payment operations) shall not stop booking (RES-07).
- **NFR-REL-05** If the discovery server is down, running services keep calling each other using their cached registry; new instances cannot join until it returns.
- **NFR-REL-06** If the Config Server is down, running services keep their current configuration; only startup and refresh are affected.

### Testability and quality
- **NFR-TEST-01** Unit tests (JUnit 5, Mockito) cover every rule in sections 6–10 and the resilience fallbacks.
- **NFR-TEST-02** Integration tests run against real PostgreSQL via Testcontainers (no H2) per service.
- **NFR-TEST-03** Outbound Feign clients are tested against a stub HTTP server (WireMock, ARCHITECTURE.md D-28, approved 2026-11-07), including timeouts, 5xx and open-circuit behavior.
- **NFR-TEST-04** System tests run the full stack with Docker Compose: the main journey (Playwright), CONC-10, and resilience scenarios (FS-32..38).
- **NFR-TEST-05** CI runs all module builds and tests and the frontend checks on every push and pull request; `main` must be green.
- **NFR-TEST-06** Every phase ends with tests passing, CI green, deployed, README updated.

### Deployability and operations
- **NFR-OPS-01** `docker compose up -d` (profile `dev`) starts PostgreSQL (with one database per service), the config server, discovery server, gateway and all services (and Redis from P4).
- **NFR-OPS-02** Every service has its own Dockerfile and is configured only through environment variables plus the Config Server.
- **NFR-OPS-03** Logs include the request ID; booking state changes are logged at INFO without personal data beyond user ID.
- **NFR-OPS-04** Startup order is handled by health-based dependencies and client retries, not by fixed sleeps.
- **NFR-OPS-05** The deployed databases are backed up every night (`pg_dump` per service database), kept 7 days on the host and copied **encrypted** off the host (30 days). A failed or missed backup shows as an unhealthy container. A restore drill restores the newest off-site backup into a throwaway database and compares row counts (M1.10).
- **NFR-OPS-06** A release deploys the images of exactly one commit (tagged with its SHA) after CI passed, and is smoke-tested from outside the host (journey, private paths, closed ports). Rolling back is deploying an older commit's images; data lives in volumes and is never touched by a release (M1.10).

### Usability and maintainability
- **NFR-UX-01** The main journey needs at most 6 clicks after login.
- **NFR-UX-02** All interactive elements are keyboard accessible and labelled.
- **NFR-MAINT-01** Category and type differences are data only: adding a category or bookable type requires only a migration, no Java or TypeScript changes. Verified by a test that seeds an extra type and books it.
- **NFR-MAINT-02** No Kafka or other message broker, Kubernetes, rules engines, or frameworks outside TECH_STACK.md without the owner's approval.
- **NFR-MAINT-03** Shared code between services is limited to a small library (error format, request ID, JWT verification, event envelope); no shared entities or shared database access.

### Privacy
- **NFR-PRIV-01** All demo data is fictional. No real personal, patient or payment data, ever.

## 17. Assumptions

Items carried over from PROJECT.md §10 are marked (PROJECT).

**Owner review M0.3 (2026-11-05):** confirmed A-02, A-03, A-05, A-09, A-12, A-23, A-24, A-25, A-27, A-28 as written; **changed** A-06 (per-weekday hours now), A-07 (7 days), A-08 (45 + 15 on a 15-minute grid). Items not listed here are confirmed when their phase starts.

| ID | Assumption |
|---|---|
| A-01 | (PROJECT) Self-registration creates MEMBER accounts; ADMIN accounts are seeded. |
| A-02 | Access tokens live 15 minutes; refresh tokens handle staying logged in (FR-AUTH-09..13). A disabled user's access token remains valid until it expires (≤ 15 min). |
| A-03 | Accessing another member's booking returns 404, not 403. |
| A-04 | (PROJECT) Hold duration is 5 minutes in P1; P5 checkout holds may differ (decided in P5). |
| A-05 | The hold-expiry job runs every 30 seconds. |
| A-06 | Opening hours are defined **per weekday** from P1 (one interval per day; a weekday without hours is closed; a bookable may replace the venue's weekly hours with its own). A booking may not cross local midnight (no overnight parking in P1). |
| A-07 | Booking horizon is 7 days (category column). |
| A-08 | Gym Trainer: 15-minute grid, 45-minute sessions, 15-minute buffer, so one client per hour per trainer. |
| A-09 | The per-member limit counts HELD and CONFIRMED bookings that have not ended, per category. |
| A-10 | A member can be on at most 5 waitlists at once. |
| A-11 | Offers are capped at the slot start and are not made within 5 minutes of the start. |
| A-12 | Idempotency keys are retained for 24 hours. |
| A-13 | Performance targets are measured on demo data on a developer machine; they are sanity limits, not SLAs. |
| A-14 | Rate limits: 5 logins/min per (IP, email), 20 booking creations/min per user; if Redis is down, rate limiting fails open. |
| A-15 | Refunds: full refund for member cancellation before cutoff and for any admin cancellation; no partial refunds. |
| A-16 | (PROJECT) Times are stored in UTC and displayed in the venue's time zone. |
| A-17 | (PROJECT) All demo data is fictional. |
| A-18 | (PROJECT, updated) Expected load is small; the microservice split is for architecture practice and independent deployment, not scale. |
| A-19 | Recurring series in P7 are weekly, up to 12 occurrences; each occurrence counts toward the per-member limit. |
| A-20 | Recurring occurrences keep the same local wall-clock time across DST changes. |
| A-21 | No-show penalty: 3 no-shows within 30 days blocks new bookings for 7 days. |
| A-22 | Email notifications in P8 are simulated (stored and shown), never sent. |
| A-23 | Library seats: 60-minute grid, up to 4 hours per booking (confirmed). |
| A-24 | Refresh tokens expire after 7 days without use (sliding, D-37), cookie `SameSite=Strict`, `Path=/api/v1/auth`; CSRF defence = Origin check + `X-GridReserve-Client` header; frontend and gateway on the same site in prod. |
| A-25 | Resilience defaults in RES-01..05 (timeouts 1 s / 2 s / 5 s, breaker window 20 and 50 %, open 30 s, 3 retries from 200 ms, bulkhead 10, rate limiter 20/s). Tuned after measuring. |
| A-26 | Reading-hall sessions default to 2-hour blocks; capacity is set per session by the admin. |
| A-27 | JWT signing uses RS256 with a key ID (`kid`); key rotation is manual in P1 (new key pair, both public keys served during overlap). |
| A-29 | `[optional extension, not planned (owner, 2026-11-06)]` Accounts created through Google or GitHub have no password and cannot set one (possible extension). Linking an account to a provider happens only by verified email; there is no manual "link my account" screen. |
| A-28 | Default JVM heap ≤ 256 MB per service (confirmed); the deploy host must fit about 5 JVMs plus PostgreSQL in Phase 1. |

## 18. Failure scenarios (FS)

| ID | Scenario | Expected behavior | HTTP | Test |
|---|---|---|---|---|
| FS-01 | 50 members request the same slot at once | 1 hold, 49 rejected | 201 / 409 SLOT_TAKEN | Integration (CONC-01) |
| FS-02 | Member requests a slot overlapping another of their active bookings (any category) | Rejected by constraint | 409 MEMBER_OVERLAP | Integration |
| FS-03 | Member already has 3 active future bookings in the category | Rejected | 422 MEMBER_LIMIT_REACHED | Unit + integration |
| FS-04 | Outside hours, or crosses local midnight | Rejected | 422 OUTSIDE_OPERATING_HOURS | Unit |
| FS-05 | Start 10:15 on a 30-minute grid opening at 08:00 | Rejected | 422 OFF_GRID | Unit |
| FS-06 | Clinic booking of 60 minutes | Rejected | 422 DURATION_INVALID | Unit |
| FS-07 | Start in the past | Rejected | 422 START_IN_PAST | Unit |
| FS-08 | Confirm after hold expired (job not yet run) | Marked EXPIRED | 409 HOLD_EXPIRED | Integration (clock) |
| FS-09 | Confirm a cancelled booking | No change | 409 INVALID_STATE | Unit |
| FS-10 | Member cancels 1 h before start | Rejected | 422 CUTOFF_PASSED | Unit |
| FS-11 | Admin cancels 1 h before start | Cancelled, actor = admin | 200 | Integration |
| FS-12 | Booking an inactive bookable | Rejected | 422 BOOKABLE_INACTIVE | Unit |
| FS-13 | Deactivate a bookable with future bookings | Deactivated; existing HELD/CONFIRMED unchanged (open hold can still be confirmed); new holds rejected; hidden from members | 200, then 422 BOOKABLE_INACTIVE | Integration |
| FS-14 | Missing, malformed or expired JWT | Rejected at gateway and service | 401 | Integration |
| FS-15 | Member opens another member's booking | Not revealed | 404 | Integration |
| FS-16 | Member calls an admin endpoint | Rejected | 403 | Integration |
| FS-17 | Same `Idempotency-Key`, same body, retried | Original response, no duplicate | 201 (replay) | Integration |
| FS-18 | Same key, different body | Rejected | 422 IDEMPOTENCY_KEY_REUSED | Integration |
| FS-19 | Booking database down | Clear error; readiness DOWN | 503 | Integration |
| FS-20 | `[P4]` Redis down | Reads, bookings correct; rate limits fail open | 200 / 201 | System test |
| FS-21 | Hold-expiry job did not run for 10 min | Stale holds don't block | 201 | Integration |
| FS-22 | Invalid JSON or unknown field | Rejected | 400 MALFORMED_REQUEST | Integration |
| FS-23 | Unexpected exception | Generic message, logged with request ID | 500 | Unit |
| FS-24 | `[P2]` 20 members race for the last seat | 1 succeeds, `booked == capacity` | 201 / 409 SESSION_FULL | Integration |
| FS-25 | `[P2]` Concurrent seat cancellations | `booked` never < 0 | 200 | Integration |
| FS-26 | `[P3]` Offer not accepted in 10 min | Expires; next person offered | — | Integration (clock) |
| FS-27 | `[P3]` First waiting member now ineligible | SKIPPED, next offered | — | Integration |
| FS-28 | `[P5]` Same webhook twice | Processed once | 200 / 200 | Integration |
| FS-29 | `[P5]` Webhook with bad signature | Rejected | 401 | Integration |
| FS-30 | `[P7]` Series, 2 of 8 dates taken | 6 booked, 2 skipped with reasons | 201 | Integration |
| FS-31 | `[P7]` Occurrence in a DST gap | Skipped, reason `DST_GAP` | 201 | Unit |
| FS-32 | Identity service down, member has a valid access token | Hold/confirm/cancel/availability work; login and refresh fail | 2xx / 503 | System test |
| FS-33 | Identity service down, admin lists bookings | List loads with member IDs only, `memberDetailsAvailable: false` | 200 | Integration (stubbed) |
| FS-34 | One of two booking-service instances killed during load | Gateway routes to the healthy instance; GETs retried; POSTs not retried (client retries with same idempotency key); no duplicate bookings | 2xx / 503 | System test |
| FS-35 | Downstream slow (> 2 s) repeatedly | Circuit opens after the window; calls fail fast to fallback; half-open after 30 s | 503 / fallback | Integration |
| FS-36 | Bulkhead full (11th concurrent call to a downstream) | Fails fast to fallback, no thread exhaustion | fallback | Integration |
| FS-37 | Config Server down | Running services unaffected; a starting service retries then fails fast | — | System test |
| FS-38 | Discovery server down | Existing routes keep working from cached registry | 2xx | System test |
| FS-39 | *Withdrawn 2026-11-06 (D-37): refresh tokens are no longer rotated, so there is no reuse detection. The ID is not reused.* | — | — | — |
| FS-40 | Refresh call from a foreign origin or without the client header | Rejected | 403 | Integration |
| FS-42 | `[optional extension, not planned (owner, 2026-11-06)]` Provider sign-in with an email the provider has not verified, matching an existing account | Not linked, no account created, no session | redirect `?error=email_not_verified` | Integration |
| FS-43 | `[optional extension, not planned (owner, 2026-11-06)]` Provider callback with a missing or wrong `state` (forged or replayed callback) | Rejected, no session | redirect `?error=login_failed` | Integration |
| FS-44 | `[optional extension, not planned (owner, 2026-11-06)]` Disabled account signs in through a provider | Refused, no session | redirect `?error=login_failed` | Integration |
| FS-41 | `[P5]` Notification/payment service down when an event is emitted | Event stays in outbox, delivered after recovery exactly once in effect | — | Integration |

## 19. Traceability

### PROJECT.md §5 (Phase 1 features) → requirements

| §5 item | Requirements |
|---|---|
| 1 Authentication | FR-AUTH-01..13, FR-ROLE-01..05, NFR-SEC-01..03 |
| 2 Catalog | FR-CAT-01..16, FR-SEED-01 |
| 3 Category dropdown | FR-UI-01, FR-UI-02, US-03 |
| 4 Availability | FR-AV-01..07, US-04 |
| 5 Booking lifecycle | FR-BK-01..09, BR-13, US-05 |
| 6 Conflict protection | BR-10, BR-11, CONC-01..03, CONC-10, FS-01, FS-02 |
| 7 Rule validation | BR-01..09, BR-14, CXL-01..06 |
| 8 Hold expiry | FR-EXP-01..04, CONC-04, FS-08, FS-21 |
| 9 Error format and validation | API-05, API-06, §15.3, PLAT-12, NFR-SEC-04/05 |
| 10 Frontend | FR-UI-01..10 |
| 11 Platform | PLAT-01..12, RES-01..10, US-12, US-13, FS-32..38 |
| 12 Quality and delivery | NFR-TEST-01..06, NFR-OPS-01..04, CONC-01, CONC-10, FR-SEED-01..03 |
| 13 Idempotency and audit | IDEM-01..07, CONC-08, FR-AUD-01..03 |

### PROJECT.md §8 success criteria → requirements

| Criterion | Requirements |
|---|---|
| 50-parallel-request test (also through the gateway with two instances) | CONC-01, CONC-10, FS-01, NFR-PERF-03 |
| Resilience tests pass | RES-*, FS-32..38 |
| CI is green | NFR-TEST-05 |
| App is deployed | NFR-OPS-02, NFR-OPS-05, NFR-OPS-06, NFR-TEST-06 |
| README explains how to run and test | NFR-TEST-06, FR-SEED-02 |
