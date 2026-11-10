# Phase 1: what to know to read every line

Everything the Phase 1 code uses, grouped by topic. **Since** names the milestone that introduced it, so you can study in the order the code was written. Each milestone adds its new rows in its own PR (CLAUDE.md §2), and each review guide's "Learn first" table repeats that milestone's rows.

Suggested order: Java → Spring core → Spring MVC → Data → Spring Cloud → Security → Testing. Sections 1–3 are the base for everything else. Section 10 is the frontend (from M1.6).

## 1. Java language (Java 21)

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| `record` | A compact immutable class: fields, constructor, getters (`email()` not `getEmail()`), `equals`, `hashCode`, `toString` generated. We use it for DTOs and value objects. You can still add methods or override `toString`. | `ErrorResponse`, `RegisterRequest`, `CountryTimeZoneId`, `JwtProperties` | M1.1 |
| `enum` with fields | Fixed set of constants that can carry data and implement interfaces. | `CommonErrorCode` (each code knows its `HttpStatus`), `BookingStatus`, `Role` | M1.1 |
| Interfaces as contracts | A type with method signatures; classes `implements` it. Lets each service add its own error codes while the handler only knows `ErrorCode`. | `ErrorCode`, `IdentityClient` | M1.1 |
| Unchecked exceptions | `RuntimeException` subclasses need no `throws`; Spring rolls back transactions on them by default. | `DomainException` | M1.1 |
| Utility class pattern | `final` class + `private` constructor + `static` methods: can't be instantiated. | `PemKeys`, `Credentials`, `SqlErrors` | M1.2 |
| `java.time` | `Instant` = a moment in UTC; `Duration` = an amount of time; `ZoneId` = an IANA zone like `Asia/Kolkata`; `Clock` = "what time is it", injectable so tests can fix time. Never `LocalDateTime` for stored moments. | `Clock` bean in `CommonWebAutoConfiguration`, `Venue.getTimeZone()`, token expiry | M1.1 |
| `Optional` | A box that may be empty; forces you to handle "not found" (`map`, `orElse`, `orElseThrow`). | `AppUserRepository.findByEmail`, `AuthService.login` | M1.2 |
| Lambdas and method references | `x -> x + 1`, `AppUser::getEmail`: short functions passed as arguments. | Everywhere in Spring config (`auth -> auth.requestMatchers(...)`) and AssertJ | M1.1 |
| Streams | `list.stream().map(...).filter(...).toList()`: transform collections without loops. | `GlobalExceptionHandler` field errors, `AccessTokenVerification` key list | M1.1 |
| Text blocks | `"""` multi-line strings; used for SQL and JSON in tests. | `SeedDataIT`, `AuthFlowIT` | M1.2 |
| Pattern matching `instanceof` | `if (x instanceof Foo foo)` checks the type and casts in one step. | `ConstraintViolations`, `AccessTokenVerification.isExpired` | M1.3a |
| Annotations and meta-annotations | `@Something` attaches metadata; an annotation can carry other annotations, so `@ServiceIntegrationTest` bundles several. `@Retention(RUNTIME)` keeps it visible to frameworks. | `ServiceIntegrationTest` | M1.2 |
| UTF-8 bytes vs characters | `"é".length()` is 1 character but 2 bytes. Matters when a limit is in bytes (BCrypt: 72). | `Credentials.fitsBcrypt` | M1.3a |
| `BigInteger`, `KeyFactory`, key specs | Java's crypto API: turn encoded bytes (X.509 / PKCS#8) into `RSAPublicKey` / `RSAPrivateKey`; compare modulus to check a key pair. | `PemKeys`, `JwtSigningConfig.belongTogether` | M1.3a |
| `ExecutorService`, `Future`, `CountDownLatch` | Thread pool, a handle to an async result, and a gate that releases all threads at once (true simultaneous start). `try (ExecutorService ...)` closes the pool (Java 21). | `AuthFlowIT.concurrentRegistrationsOneWinner_FRAUTH02` | M1.3a |
| `SecureRandom`, URL-safe Base64 | A random generator meant for secrets (unpredictable, unlike `Random`), and Base64 with `-` and `_` instead of `+` and `/`, so the value fits in a cookie without escaping. | `RefreshTokens.newToken` | M1.3b |
| `MessageDigest` (SHA-256), `HexFormat` | A one-way hash: same input → same 32 bytes, impossible to reverse; `HexFormat` prints them as 64 hex characters. | `RefreshTokens.hash` | M1.3b |
| `Optional` chains (`flatMap`, `filter`, `map`, `orElseThrow`) | Each step runs only if there is still a value: token valid → find user → enabled → issue; any empty step falls through to the error. | `AuthService.refresh` | M1.3b |
| `LocalDateTime` vs `ZonedDateTime`, DST gaps and overlaps | `LocalDateTime` is a wall-clock reading with no zone; `ZonedDateTime` adds the zone and so names a real moment. On spring-forward night a local hour does not exist (`getValidOffsets` is empty); on fall-back night one happens twice (two offsets). | `LocalSlotGrid` | M1.4 |
| `Comparator.comparing(...).thenComparing(...)` | Builds a sort order from getters, first key then tie-breaker. | `CatalogService.categories` | M1.4 |
| `List.getFirst()` / `getLast()` | Java 21 sequenced collections: first and last element without index arithmetic. | `AvailabilityService` | M1.4 |
| `Instant.truncatedTo(ChronoUnit.MICROS)` | Cuts an instant to whole microseconds. PostgreSQL `timestamptz` keeps microseconds, Linux clocks give nanoseconds; without it a response and a later read of the same row differ. | `BookingTransactions.hold` | M1.5 |
| Wall-clock vs elapsed time (`ZonedDateTime` vs `Duration`) | Opening hours and the slot grid are wall-clock rules (`atZone(zone).getHour()`); a booking's length is real elapsed time (`Duration.between`). On a daylight-saving day they differ: 00:00–02:00 London on spring-forward night lasts 60 minutes. | `BookingRules`, `BookingRulesTest.daylightSavingDay` | M1.7 |
| Switch expression over an enum | `return switch (status) { case HELD -> …; case CONFIRMED -> …; case CANCELLED, EXPIRED -> null; };` must cover every constant, so a new status is a compile error until handled. | `BookingMapper.cancellableUntil` | M1.7 |

## 2. Build, containers and CI

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| Maven multi-module | One parent `pom.xml` lists modules; each module has its own `pom.xml`. `-pl X -am` builds module X and what it depends on. | `backend/pom.xml` | M1.1 |
| Parent POM and BOM | The Spring Boot parent and the Spring Cloud BOM (`<dependencyManagement>`, `import` scope) fix versions, so module POMs list dependencies without versions. | `backend/pom.xml` | M1.1 |
| Starters | One dependency that brings a feature's libraries plus auto-configuration (`spring-boot-starter-data-jpa`). Boot 4 split them into smaller modules (e.g. separate Flyway starter). | every module `pom.xml` | M1.1 |
| Dependency scopes | `compile` (default), `runtime` (not needed to compile, e.g. the JDBC driver), `test`, and `optional` (not passed on to modules that depend on you). | `postgresql` runtime; resource-server starter optional in `common-web` | M1.2 / M1.3a |
| Surefire vs Failsafe | Surefire runs `*Test` in `test`; Failsafe runs `*IT` in `integration-test`/`verify`. Without Failsafe, `*IT` classes are silently skipped. | `backend/pom.xml` build plugins | M1.2 |
| Docker multi-stage build | Stage 1 compiles with the JDK; stage 2 copies only the jar into a smaller JRE image and runs as a non-root user. | `backend/Dockerfile` | M1.1 |
| Docker Compose | Starts all containers on one network. `depends_on: condition: service_healthy` + `healthcheck` = start order by health, not sleeps. `${VAR}` comes from `infra/.env`. YAML anchors (`&java-env`, `<<: *java-env`) reuse blocks. | `infra/compose.yaml` | M1.1 |
| GitHub Actions | A workflow runs `./mvnw -B verify` on every push and PR; Docker is available, so Testcontainers works in CI. | `.github/workflows/ci.yml` | M1.1 |
| Optional dependency used to keep an app reactive | If a jar puts both the servlet (webmvc/Tomcat) and the reactive (WebFlux/Netty) stack on the classpath, Spring Boot starts a servlet app. Marking webmvc `optional` in `common-web` means the gateway gets only the token code it needs. Check with `./mvnw -pl platform/api-gateway -am dependency:tree`. | `libs/common-web/pom.xml`, `platform/api-gateway/pom.xml` | M1.3c |
| CI job with a throwaway Compose stack | GitHub Actions checks out both repos side by side, generates random passwords and a fresh JWT key pair, starts the stack with two booking instances, runs the system tests, then stops it. Nothing secret is committed or printed. | `.github/workflows/ci.yml`, `scripts/ci/prepare-system-test-env.sh` | M1.8 |
| Untrusted input in workflow scripts | Values like a branch name go into a step through `env:` and are used as `"$BRANCH"`, never pasted into the script with `${{ }}`, so they can't inject shell commands. | `.github/workflows/ci.yml` (Pick the config branch) | M1.8 |
| `while read` and stdin | A command inside `while read … done <<< "$list"` that reads standard input (like `docker exec`) swallows the rest of the list; give it `</dev/null`. | `scripts/refresh-config.sh` | M1.9 |
| Multi-platform images (`--platform=$BUILDPLATFORM`, Buildx, QEMU) | One image tag can hold variants for several CPUs (amd64 laptops, arm64 Oracle VM). Buildx builds them; QEMU emulates the other CPU. Our build stage runs on the builder's own CPU because a jar (or the frontend's files) is the same everywhere, so only the small runtime stage is emulated. | `backend/Dockerfile`, `frontend/Dockerfile`, CI images job | M1.10 |
| Dockerfile `HEALTHCHECK`, `ARG` vs `ENV`, `USER` | `HEALTHCHECK` lets Docker itself mark a container healthy or unhealthy. `ARG` exists only while building (`MODULE`, `PORT`); `ENV` stays in the image at run time. `USER` makes the process run as a non-root account. | `backend/Dockerfile`, `infra/backup/Dockerfile` | M1.10 |
| JVM memory beyond the heap | `-Xmx` limits only the heap. A JVM also uses metaspace (loaded classes), the code cache (compiled code), one stack per thread and GC bookkeeping. Capping them (`-XX:MaxMetaspaceSize`, `-XX:ReservedCodeCacheSize`, `-Xss`) and choosing the serial GC keeps a container's total predictable; `-XX:+ExitOnOutOfMemoryError` lets the platform restart a broken JVM. `JAVA_TOOL_OPTIONS` is read by every JVM at start. | `backend/Dockerfile` | M1.10 |
| Restart policies | `restart: unless-stopped`: Docker restarts a container that exits on its own (crash, fail fast), with growing delays, and after a host reboot, but not one an operator stopped (`docker stop`/`docker kill`). | `infra/compose.prod.yaml` | M1.10 |
| Compose profiles and several files | `profiles: [full]` keeps a service out of a normal `up` until asked for (`--profile full`). A second compose file can be a different deployment of the same images (`compose.prod.yaml`); `name:` fixes the project and network names. | `infra/compose.yaml`, `infra/compose.prod.yaml` | M1.10 |
| Reverse proxy and automatic HTTPS (Caddy) | One server in front of everything: it ends TLS, serves static files and forwards `/api` to the gateway. Caddy proves to Let's Encrypt that it controls the name (on port 80) and renews the certificate by itself. | `frontend/deploy/Caddyfile` | M1.10 |
| SPA fallback and cache headers | A React route like `/bookings` is no file on disk; the server answers `index.html` and the router takes over. File names with a content hash (`/assets/index-3f2a.js`) can be cached for a year; `index.html` must never be cached, or users keep an old app. | `frontend/deploy/Caddyfile` | M1.10 |
| Container registry and image tags | GHCR stores images like Docker Hub. Tagging each with the commit SHA makes a release exact ("this commit's code") and rollback a redeploy of an older tag. | CI images job, `scripts/prod/release.sh` | M1.10 |
| Continuous deployment over SSH | A workflow that starts when another one finishes (`workflow_run`), uses a deploy-only SSH key from GitHub secrets, pins the host's key (`known_hosts`, `StrictHostKeyChecking=yes`) so it can't be tricked into another machine, and smoke-tests from outside. | `.github/workflows/deploy.yml` | M1.10 |
| Backups: dump, checksums, encryption, off-site | `pg_dump --format=custom` writes a database to one compressed file that `pg_restore` can load into an empty database. Checksums (`sha256sum`) prove a copy is intact. rclone's `crypt` remote encrypts contents and names before upload; the key stays with us. A backup isn't proven until a restore has been tried (drill). | `infra/backup/*.sh`, `scripts/prod/restore-drill.sh` | M1.10 |

## 3. Spring core and Spring Boot

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| IoC container and beans | Spring creates objects ("beans") and hands them to each other. You declare *what* you need, not how to build it. | everywhere | M1.1 |
| Constructor injection | Dependencies arrive as constructor parameters (`final` fields). No `@Autowired` on fields in production code. | `UserService(AppUserRepository, PasswordEncoder, Clock)` | M1.1 |
| Stereotypes | `@Component`, `@Service`, `@RestController`, `@Configuration` mark classes Spring should create. | `AuthService`, `AccessTokenIssuer`, `SecurityConfig` | M1.1 |
| `@Configuration` + `@Bean` | A method whose return value becomes a bean; used for objects you build yourself. `proxyBeanMethods = false` skips a proxy we don't need. | `JwtSigningConfig.jwtEncoder`, `SecurityConfig` | M1.1 |
| Auto-configuration | Boot configures things based on what is on the classpath. A library registers its own config in `META-INF/spring/...AutoConfiguration.imports`; that is how `common-web` reaches every service without component scanning. | `CommonWebAutoConfiguration`, `CommonWebSecurityAutoConfiguration` | M1.1 / M1.3a |
| Conditions | `@ConditionalOnMissingBean` (a service can replace our bean), `@ConditionalOnClass` (only if a library is present), `@ConditionalOnWebApplication(SERVLET)`. | common-web auto-configurations | M1.1 / M1.3a |
| Externalized configuration | Properties from `application.yml`, the Config Server, env vars and test overrides merge into one `Environment`. `${NAME:default}` placeholders; env var `JWT_PRIVATE_KEY` fills `${JWT_PRIVATE_KEY}`. | `application.yml`, config repo | M1.1 |
| `@ConfigurationProperties` + `@Validated` | Binds a property group to a typed object (here records) and checks it at startup, so a missing key stops the app instead of failing later. | `JwtProperties`, `JwtSigningProperties` | M1.3a |
| Profiles | `dev` / `prod` select extra config files (`application-dev.yml`). | config repo, `SPRING_PROFILES_ACTIVE` | M1.1 |
| Servlet filters and order | Code that runs around every request before controllers; `OncePerRequestFilter`; `Ordered.HIGHEST_PRECEDENCE` runs first. | `RequestIdFilter` | M1.1 |
| SLF4J and MDC | Logging API; MDC is a per-thread map whose values (our `requestId`) appear in every log line. Clear it in `finally`. | `RequestIdFilter`, log pattern in config repo | M1.1 |
| `ApplicationContextInitializer` | Runs before beans are created; can add properties. Used to inject throwaway test keys. | `TestJwtKeys` (identity tests) | M1.3a |
| Transaction propagation and rollback | A `@Transactional` method called from another one joins its transaction (`REQUIRED`). A `RuntimeException` leaving the method rolls back everything in it, including earlier successful statements. | `AuthService.register` joins `UserService.register`; `AuthService.refresh`: a disabled user's 401 also undoes the expiry move | M1.3b |
| Profile-specific config files | `x-dev.yml` overrides `x.yml` when the `dev` profile is active. We keep the safe value as the default and relax it only for dev. | config repo `identity-service.yml` (`cookie-secure: true`) + `identity-service-dev.yml` (`false`) | M1.3b |
| Profile files merge, maps included | Spring layers `application.yml` → `application-prod.yml` → `{service}-prod.yml`; a later file overrides a value, but for a **map** it adds entries. A JWT public key listed in the shared file would also be trusted in prod, so each profile lists its own keys. | `gridreserve-config/application-dev.yml`, `application-prod.yml` | M1.10 |

## 4. Spring MVC (REST APIs)

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| `@RestController` and mappings | `@GetMapping`, `@PostMapping`, `@RequestMapping` bind HTTP method + path to a method; the return value becomes JSON. `@ResponseStatus(CREATED)` sets 201. | `AuthController`, `MeController`, `PingController` | M1.1 |
| `@RequestBody` + `@Valid` | Reads JSON into a DTO and runs Bean Validation; failures raise `MethodArgumentNotValidException` → our `400 VALIDATION_FAILED` with `fieldErrors`. | `AuthController.register` | M1.3a |
| Bean Validation | `@NotBlank`, `@Email`, `@Size`, `@NotEmpty`, `@NotNull` on fields; checked automatically. | `RegisterRequest`, `JwtProperties` | M1.1 |
| `@RestControllerAdvice` + `@ExceptionHandler` | One class turns exceptions from any controller into the standard error JSON. | `GlobalExceptionHandler` | M1.1 |
| Jackson 3 | Converts Java ↔ JSON. Boot 4 uses Jackson 3 (`tools.jackson.*`); annotations stay in `com.fasterxml.jackson.annotation`. `Instant` is written as ISO-8601. | `ErrorResponse`, `JsonSecurityErrorHandler` | M1.1 |
| Spring-specific exceptions | `HttpMessageNotReadableException` (bad JSON), `NoResourceFoundException` (no handler for the path), `HttpRequestMethodNotSupportedException`. | `GlobalExceptionHandler` | M1.1 |
| `ResponseEntity` with headers | Lets a controller choose the status and add headers (here `Set-Cookie`) next to the body. | `AuthController.withSession`, `refresh`, `logout` | M1.3b |
| `ResponseCookie`, reading cookies | Spring's builder for a `Set-Cookie` header with all attributes; `WebUtils.getCookie` reads one cookie from the request. | `RefreshCookie` | M1.3b |
| `@RequestParam` with defaults, `@DateTimeFormat` | Binds query parameters (`?page=0&size=20`, `?date=2026-11-07`) to Java types; a value that cannot be converted throws `MethodArgumentTypeMismatchException`, now a `400 VALIDATION_FAILED` naming the field. | `CatalogController`, `AvailabilityController`, `GlobalExceptionHandler` | M1.4 |
| `Authentication` as a controller parameter | Spring passes the current caller; its authorities show the role (`ROLE_ADMIN`). | `Viewer.isAdmin` | M1.4 |
| `ResponseEntity.created(uri)` | A `201 Created` with a `Location` header pointing at the new item. | `BookingController.hold` | M1.5 |
| `@RequestBody(required = false)` | The body may be missing (cancel without a reason); the parameter is then `null`. | `BookingController.cancel` | M1.5 |
| `authentication.getName()` = token `sub` | For a JWT the principal name is the `sub` claim, here the identity user ID; it is the booking owner, never a request field (FR-ROLE-05). | `Viewer.memberId` | M1.5 |
| `@PutMapping` and full replacement | PUT sends the whole editable state, not a patch; the server replaces the fields (and, here, the weekly hours) at once. Simpler to validate than partial updates. | `AdminCatalogController` | M1.11 |
| Validating list elements: `List<@Valid @NotNull X>` | Annotations on the type argument check each element, and `@Valid` cascades into it; a field error names its index (`openingHours[1].opensAt`). | `CreateVenueRequest` | M1.11 |
| HTTP method in security rules | `requestMatchers(HttpMethod.POST, "/api/v1/venues/**")` lets members GET a path while only admins POST or PUT to it. | booking `SecurityConfig` | M1.11 |

## 5. Data: PostgreSQL, Flyway, JPA

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| Database per service | Each service has its own database and user; no cross-database joins or foreign keys. | `infra/postgres/init-databases.sh` | M1.1 |
| Flyway | Versioned SQL files `V<n>__name.sql` applied once, in order, tracked in `flyway_schema_history`; never edit an applied one. A lock serializes concurrent starts. | `db/migration` in both services | M1.2 |
| Named constraints | `pk_`, `fk_`, `uq_`, `ck_`, `ex_`: the API maps errors by constraint name. A `CHECK` passes when the expression is NULL. | `V2__core_schema.sql`, `V1__user_schema.sql` | M1.2 |
| Composite foreign key | An FK on two columns; here "this zone belongs to this country". | `fk_venue_country_zone` | M1.2 |
| Exclusion constraint | "No two rows with equal X and overlapping Y": `EXCLUDE USING gist (bookable_id WITH =, tstzrange(...) WITH &&) WHERE (...)`. Needs `btree_gist` for `=` on bigint. | `ex_booking_bookable_overlap`, `ex_booking_member_overlap` | M1.2 |
| Ranges and `'[)'` | `tstzrange(start, end, '[)')`: start included, end excluded, so back-to-back bookings don't overlap. | V2 | M1.2 |
| `timestamptz`, identity columns, partial indexes | Stored in UTC; `GENERATED ALWAYS AS IDENTITY` is the modern auto-increment; an index `WHERE status IN (...)` covers only active rows. | V2 | M1.2 |
| SQLSTATE codes | `23505` unique, `23503` FK, `23514` check, `23P01` exclusion violation. | `SqlErrors`, tests | M1.2 |
| JPA / Hibernate mapping | `@Entity`, `@Table`, `@Id`, `@GeneratedValue(IDENTITY)`, `@Column`, `@Enumerated(STRING)`, `@ManyToOne(LAZY)` + `@JoinColumn`, `@Version` (optimistic locking), `@EmbeddedId` + `@Embeddable` (composite keys), `@JdbcTypeCode(CHAR)`. Protected no-arg constructor for Hibernate. | `catalog/entity/*`, `AppUser` | M1.2 |
| `ddl-auto: validate`, `open-in-view: false` | Hibernate only checks entities against the schema (Flyway changes it); no lazy loading during view rendering. | both services' `application.yml` | M1.2 |
| Spring Data JPA repositories | Interface extending `JpaRepository<Entity, Id>`; methods like `findByEmail` are generated from their names. `saveAndFlush` sends the INSERT now. | `AppUserRepository`, `CategoryRepository` | M1.2 |
| `@Transactional` | Starts and commits (or rolls back on a runtime exception) a DB transaction around a service method; `readOnly = true` for reads. | `UserService`, `AuthService` | M1.3a |
| Exception translation | Spring wraps driver errors in `DataIntegrityViolationException`; Hibernate's `ConstraintViolationException` carries the constraint name. | `ConstraintViolations` | M1.3a |
| Check-then-act race | "Is it free? then insert" fails under concurrency; let a constraint decide. The same idea protects slots (exclusion) and emails (unique). | `UserService.register`, booking constraints | M1.2 / M1.3a |
| HikariCP | The JDBC connection pool; named and bounded per service. | config repo datasource `hikari` | M1.2 |
| `@Modifying` JPQL `UPDATE` / `DELETE` | A repository method that runs one bulk statement and returns how many rows it changed, without loading entities. The count is the answer (1 = done, 0 = refused). | `RefreshTokenRepository.extend`, `deleteByTokenHash`, `deleteExpired` | M1.3b |
| Conditional `UPDATE` as check-and-act | `UPDATE … WHERE <still valid>`: checking and changing happen in one statement, so no other request can slip in between. | `RefreshTokenRepository.extend` (`expires_at > :now`) | M1.3b |
| `char(n)` vs `varchar`, `@JdbcTypeCode` | `char(64)` is fixed length. Hibernate assumes `varchar` for a `String`; `@JdbcTypeCode(SqlTypes.CHAR)` tells it otherwise, or `ddl-auto: validate` refuses to start. | `RefreshToken.tokenHash` | M1.3b |
| `INSERT … ON CONFLICT DO NOTHING` | Insert a row unless one with the same key exists, in one statement. Under parallel calls PostgreSQL lets exactly one insert win; the others do nothing instead of failing. Returns 1 or 0 rows. | `MemberRepository.insertIfAbsent` | M1.3c |
| Native `@Query` | `nativeQuery = true` sends plain PostgreSQL SQL instead of JPQL, needed for PostgreSQL-only syntax such as `ON CONFLICT`. | `MemberRepository` | M1.3c |
| `Page`, `Pageable`, `Sort` | Spring Data's page of results with totals; `PageRequest.of(page, size, sort)` asks for one. Mapped to API-04's `{items, page, size, totalItems, totalPages}`. | `PageResponse`, `PageParams`, `VenueRepository` | M1.4 |
| `@EntityGraph` and the N+1 problem | N+1 = one query for a list plus one per item for a lazy relation. `@EntityGraph(attributePaths = ...)` joins the relation into the first query. | `BookableRepository` | M1.4 |
| JPQL constructor expression | `select new pkg.Dto(b.a, b.b) from Booking b ...` returns DTOs with only those columns. | `BookingRepository.findOccupied` | M1.4 |
| Optional filter in JPQL | `(:categoryId is null or v.category.id = :categoryId)`: one query for "all" and "filtered". | `VenueRepository.search` | M1.4 |
| `@Transactional(readOnly = true)` | A read-only transaction: Hibernate skips dirty checking; lazy relations can be loaded inside it (open-in-view is off). | `CatalogService`, `AvailabilityService` | M1.4 |
| Row locks: `FOR SHARE` and `FOR UPDATE` | `SELECT … FOR SHARE` lets many transactions read-lock a row together but blocks writers; `FOR UPDATE` is exclusive. Both last until the transaction ends. | `VenueRepository.lockForShareByBookable`, `BookableRepository.lockForShare`, `MemberRepository.lockForUpdate` | M1.5 |
| Lock order and deadlocks | Two transactions that lock the same rows in opposite orders can wait for each other forever; PostgreSQL kills one (`40P01`). Taking locks in one global order (venue → bookable → member → bookings by id) prevents it. | `BookingTransactions.hold`, `BookingRepository.lockStaleHolds` | M1.5 |
| `@Modifying` + conditional UPDATE | A JPQL/SQL `UPDATE` from a repository; returns the row count. `WHERE status = 'HELD' AND …` makes it a compare-and-set: 0 rows = someone changed it first. `clearAutomatically` drops stale entities afterwards. | `BookingRepository.confirm`, `cancel`, `expireHolds` | M1.5 |
| `saveAndFlush` to surface constraint errors | `save` may delay the INSERT to commit time, outside your `try`; `saveAndFlush` sends it now so the violation can be caught and translated. | `BookingTransactions.hold` | M1.5 |
| Exclusion violation `23P01` and its name | PostgreSQL's error for an exclusion constraint. Hibernate does not extract the constraint name for it, so it is read from the message with Hibernate's own template. | `ConstraintViolations` | M1.5 |
| `Propagation.MANDATORY` | The method must run inside an existing transaction, else it fails. Right for lock methods: a lock outside a transaction is released at once. | `CatalogService.lockForBooking`, `MemberLock.lock` | M1.5 |
| `noRollbackFor` | Normally a thrown runtime exception rolls the transaction back. `noRollbackFor = DomainException.class` keeps the writes, so a run-out hold is saved as `EXPIRED` even though the caller gets a 409. | `BookingTransactions.confirm`, `cancel` | M1.5 |
| `cast(:x as Instant) is null` in JPQL | PostgreSQL cannot infer the type of a bare `? is null` parameter (error `42P18`); the cast tells it. | `BookingRepository.findMine` | M1.5 |
| `FOR UPDATE SKIP LOCKED` | Locks a row, but skips rows another transaction already holds instead of waiting. Several workers can share a queue table without blocking each other. | `BookingRepository.lockRunOutHold`, `HoldExpiryService` | M1.7 |
| Policy inside the conditional UPDATE | The cancellation rules are part of the `WHERE` (`end_time > :now AND (… OR start_time > :confirmedStartAfter)`), so checking and changing are one atomic step; a refusal is explained afterwards from the current row. | `BookingRepository.cancel` | M1.7 |
| `count` with `IN (:ids)` | `select count(b) … where b.bookableId in :bookableIds`: Spring Data binds a collection to an IN list. Used to count per category without joining catalog tables. | `BookingRepository.countActive` | M1.7 |
| Shared vs exclusive row lock for writers | `FOR SHARE` lets many transactions in at once; if they then insert conflicting rows into an exclusion constraint, two can each wait for the other's uncommitted row (deadlock). `FOR UPDATE` lets one in at a time, so conflicting inserts never check each other mid-flight. | `BookableRepository.lockForBooking`, ARCHITECTURE §8.3 | M1.8 |
| Deadlock detection and `40P01` | PostgreSQL notices a wait cycle after `deadlock_timeout` (1 s) and kills one transaction with SQLSTATE `40P01`; `pg_stat_database.deadlocks` counts them but pooled connections report it late. | `BookingMetrics.lockFailure`, D-41 | M1.8 |
| Reproducing a race in plain SQL | Many `psql` sessions that wait for one instant (`SELECT pg_sleep_until(…)`) and then run the same transaction show whether the database itself deadlocks, independent of the app. | PROGRESS M1.8 | M1.8 |
| Optimistic locking with `@Version` | Hibernate adds `WHERE version = ?` to every UPDATE and bumps the number; if another transaction changed the row first, no row matches and Spring throws `ObjectOptimisticLockingFailureException`. Comparing the client's version first gives a clear 409 even when the edits weren't simultaneous. | `AdminCatalogService`, `Venue`/`Bookable` | M1.11 |
| Bulk `@Modifying` delete vs derived delete | A JPQL `delete from …` runs at once in one statement; a derived `deleteBy…` loads and queues each row, and Hibernate runs queued inserts **before** deletes at flush, so "delete Monday, insert Monday" would collide on the key. | `VenueOpeningHoursRepository.deleteAllFor` | M1.11 |
| Factory methods and mutators on entities | No public setters: `Venue.create(...)`, `update(...)`, `changeActive(...)` keep the rules of a change (timestamps, version bump) in one place. | `Venue`, `Bookable` | M1.11 |
| Commit timestamps (`track_commit_timestamp`, `pg_xact_commit_timestamp(xmin)`) | With the setting on, PostgreSQL remembers when each transaction committed; `xmin` is the transaction that wrote a row's current version. Together they show the real commit order of rows. | `DeactivationRaceIT`, `PostgresTestcontainer` | M1.11 |
| `INSERT … ON CONFLICT DO NOTHING` | Inserts only if the key is free; a second inserter waits for the first to commit, then inserts nothing (0 rows) instead of failing. A race-free "claim this key". | `IdempotencyRecordRepository.insertIfAbsent` | M1.12 |
| `jsonb` columns | PostgreSQL's binary JSON type; we store a response body there and read it back as text (`@JdbcTypeCode(SqlTypes.JSON)`, `CAST(:body AS jsonb)`). | `IdempotencyRecord` | M1.12 |
| `@Lock(PESSIMISTIC_WRITE)` | A Spring Data query that adds `FOR UPDATE`: the row stays locked until the transaction ends. Cancel uses it so the status it records as "from" can't change underneath. | `BookingRepository.lockById` | M1.12 |
| `Propagation.MANDATORY` | The method refuses to run without a caller's transaction instead of starting its own. Guarantees an audit row commits or rolls back with its change. | `AuditService.record` | M1.12 |
| Foreign key without cascade | Deleting a booking that has audit rows fails; history can't disappear by accident (tests delete in order: `TestBookings`). | `V5__audit_and_idempotency.sql` | M1.12 |

## 6. Spring Cloud and the platform

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| Config Server and client | One service serves config files from the config repo; apps import them at startup (`spring.config.import: configserver:`), fail fast if unreachable. `native` backend (local folder) in dev, Git in prod. | `config-server`, every `application.yml` | M1.1 |
| Eureka | Services register themselves; callers look up instances by service ID. | `discovery-server`, `eureka.client` in config repo | M1.1 |
| `lb://` and LoadBalancer | A URL by service ID; the load balancer picks an instance (round robin). | gateway routes | M1.1 |
| Spring Cloud Gateway (WebFlux) | The only public entry: route predicates (`Path=...`), first match wins; reactive (`Mono`, `ServerWebExchange`, `WebFilter`, `ErrorWebExceptionHandler`). | `api-gateway`, `api-gateway.yml` | M1.1 |
| OpenFeign | Declarative HTTP client: an interface with `@FeignClient(name=...)`, resolved through Eureka, with a fallback class. | `IdentityClient`, `IdentityClientFallback` | M1.1 |
| Resilience4j CircuitBreaker | Stops calling a failing downstream for a while and uses the fallback. | gateway route breaker, Feign spike | M1.1 |
| Actuator health probes | `/actuator/health/liveness` and `/readiness`; Compose waits on readiness. | config repo `management`, Compose healthchecks | M1.1 |
| `Mono.fromCallable` | Wraps a normal method call as a reactive result. Fine on the event loop only if the call does no waiting (network, disk); checking a token with in-memory keys is pure computation. | `GatewaySecurityConfig.reactiveJwtDecoder` | M1.3c |

## 7. Security (identity and tokens)

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| Hashing vs encryption | A hash can't be reversed; passwords are hashed, never encrypted or stored. | `app_user.password_hash` | M1.3a |
| BCrypt | Slow, salted password hash; cost 10 ≈ 2¹⁰ rounds. The salt is inside the hash string (`$2a$10$...`). Reads at most 72 bytes. | `SecurityConfig.passwordEncoder`, V2 seed | M1.3a |
| User enumeration and timing | Different answers, or different response times, reveal which emails have accounts. Same message plus a dummy BCrypt check prevents both. | `AuthService.login` | M1.3a |
| JWT | `header.payload.signature`, each part Base64URL. Anyone can read the payload; only the signature proves who issued it. Claims: `iss` issuer, `aud` audience, `sub` subject (user ID), `iat` issued at, `exp` expiry, plus our `role`. | `AccessTokenIssuer` | M1.3a |
| RS256 (asymmetric) vs HS256 | RS256: private key signs (identity only), public key verifies (everyone). HS256: one shared secret both signs and verifies, so every verifier could forge tokens. | `JwtSigningConfig`, `AccessTokenVerification` | M1.3a |
| PEM, X.509, PKCS#8 | Text format (`-----BEGIN ...-----` + Base64) for keys; public keys are X.509 "SubjectPublicKeyInfo", private keys PKCS#8. | `PemKeys`, `.env.example` | M1.3a |
| `kid` and key rotation | Key ID in the token header picks the verification key; publish new key, switch signer, retire old key after tokens expire. | config repo `gridreserve.jwt.public-keys` | M1.3a |
| Clock skew | Hosts' clocks differ a little; accept tokens up to 30 s past `exp`. | `AccessTokenVerification.CLOCK_SKEW` | M1.3a |
| Spring Security filter chain | A list of filters before your controllers; `SecurityFilterChain` bean says which paths are public (`permitAll`) and which need a user (`authenticated`). | `SecurityConfig` | M1.3a |
| Stateless sessions and CSRF | No HTTP session; every request carries a Bearer token. CSRF attacks need cookie authentication, so CSRF protection is off until the refresh cookie (M1.3b brings its own checks). | `SecurityConfig` | M1.3a |
| Resource server, `JwtDecoder` / `JwtEncoder` | Spring's support for "requests carry a Bearer JWT": a decoder verifies and parses tokens (Nimbus JOSE inside); an encoder signs them. Validators (`OAuth2TokenValidator`) add claim checks. | `AccessTokenVerification`, `JwtSigningConfig` | M1.3a |
| 401 vs 403 | 401 = we don't know who you are (no or bad token); 403 = we know you, but you may not do this. `AuthenticationEntryPoint` writes 401s, `AccessDeniedHandler` 403s. | `JsonSecurityErrorHandler` | M1.3a |
| Authorities and roles | Spring permissions are strings; roles are authorities with the `ROLE_` prefix (`ROLE_ADMIN`). Spring Security 7 also adds `FACTOR_BEARER` to record how the user signed in. | `AccessTokenVerification.authenticationConverter` | M1.3a |
| `@AuthenticationPrincipal Jwt` | Injects the verified token into a controller method; `getSubject()` is the user ID. | `MeController` | M1.3a |
| Secrets handling | Private keys and passwords only in env vars (`infra/.env`, git-ignored); never logged (`toString` overrides). | `JwtSigningProperties`, `RegisterRequest` | M1.3a |
| Refresh token and sliding expiry | A long-lived secret (in a cookie) that buys new short-lived access tokens. Each use moves its expiry to now + 7 days, so only 7 days *without use* end the session. Stored only as SHA-256; logout deletes the row (D-37). | `RefreshTokenService`, `V3__refresh_token.sql` | M1.3b |
| Cookie attributes | `HttpOnly`: page scripts cannot read it (XSS cannot steal it). `Secure`: HTTPS only. `SameSite=Strict`: not sent on requests started by other sites. `Path`: only sent to that path. `Max-Age`: when the browser deletes it (0 = now). | `RefreshCookie` | M1.3b |
| CSRF with cookies; Origin + custom header | A browser attaches cookies even to requests another site triggers. Other sites cannot set our `Origin`, and a custom header from another site needs CORS approval, so checking both proves the call came from our app. | `ClientCheck` | M1.3b |
| SHA-256 vs BCrypt for stored secrets | BCrypt is slow and salted, for guessable passwords. A random 256-bit token cannot be guessed, and we must find it by its hash, so a fast unsalted SHA-256 is right. | `RefreshTokens.hash` | M1.3b |
| Stateful sessions vs stateless tokens | Access tokens are checked without a database (fast, but cannot be revoked); the refresh token has a row (can be ended at logout). We use both: short stateless + long stateful. | `AccessTokenIssuer` vs `RefreshTokenService` | M1.3b |
| Reactive Spring Security (`SecurityWebFilterChain`, `ServerHttpSecurity`) | The WebFlux version of the filter chain: `authorizeExchange` + `pathMatchers` instead of `authorizeHttpRequests` + `requestMatchers`; `ServerAuthenticationEntryPoint` / `ServerAccessDeniedHandler` instead of the servlet handlers. | `GatewaySecurityConfig`, `GatewaySecurityErrorHandler` | M1.3c |
| Defence in depth | Check at the edge (gateway) **and** at the service. The gateway rejects early and cheaply; the service still verifies because it can be reached without the gateway, and only it knows which roles its endpoints need. | `GatewaySecurityConfig`, booking `SecurityConfig` | M1.3c |
| `hasRole("ADMIN")` and 403 vs 404 | A URL rule needing the `ROLE_ADMIN` authority. Security runs before Spring MVC looks for a controller, so a MEMBER gets 403 even where no endpoint exists yet, and an ADMIN gets 404. | booking `SecurityConfig`, `BookingSecurityIT` | M1.3c |
| Bearer token resolver / converter | The part of Spring Security that reads `Authorization: Bearer …`. Spring checks any token it finds, even on public paths, and answers 401 for a bad one, so we switch it off on `/api/v1/auth/**` (an expired token must not block `/refresh`). | `GatewaySecurityConfig.bearerTokenOutsideAuthPaths`, identity `SecurityConfig` | M1.3c |
| `NoOpServerSecurityContextRepository` | Tells reactive Spring Security not to store who the caller is between requests: stateless, every request brings its token. | `GatewaySecurityConfig` | M1.3c |
| Service tokens (client credentials) | A service proves itself with a client ID and secret (HTTP Basic) and gets a short-lived JWT with role `SERVICE`. The caller keeps the secret; identity keeps only its SHA-256, compared in constant time (`MessageDigest.isEqual`). | `ServiceTokenIssuer`, `ServiceTokens` | M1.9 |
| Roles per path family | `/internal/**` → SERVICE; user APIs → MEMBER or ADMIN; actuator beyond health/info → ADMIN or SERVICE. A valid token alone is not enough: it must be the right kind of caller. | `SecurityConfig` (identity, booking) | M1.9 |
| Caching a token with `AtomicReference` | Read the cached token; if it's close to expiry, fetch a new one and store it. Two threads may both fetch once: harmless, and no lock is needed. | `ServiceTokens` | M1.9 |
| Secrets created where they are used | The prod `.env` (database passwords, the signing key, the backup password) is generated on the host and never leaves it; only the public key is printed, for the config repo. A `Secure` cookie travels only over HTTPS, which is why prod needs a real certificate. | `scripts/prod/init-env.sh`, `infra/.env.prod.example` | M1.10 |
| Idempotency keys | A client-chosen ID for one intent; the server remembers its answer, so repeating the request (lost response, Retry) can't do the work twice. Keyed per member, so one member can't see another's answers. | `IdempotencyService`, `lib/idempotency.ts` | M1.12 |

## 8. Testing

| Concept | What it is | Where in our code | Since |
|---|---|---|---|
| JUnit 5 | `@Test`, `@DisplayName` (we cite requirement IDs), `@Nested` (group tests), `@BeforeEach`, `@AfterEach`. | all tests | M1.1 |
| AssertJ | Fluent assertions: `assertThat(x).isEqualTo(...)`, `extracting(...)`, `tuple(...)`, `filteredOn(...)`, `assertThatThrownBy(...)`, `satisfies(...)`. | all tests | M1.1 |
| `@SpringBootTest(RANDOM_PORT)` | Starts the whole app on a free port; tests call it over real HTTP. Spring caches the context, so classes with the same setup share one app. | `ServiceIntegrationTest` (both services) | M1.1 |
| MockMvc / mock servlet objects | Test controllers or filters without starting a server. | `GlobalExceptionHandlerTest`, `JsonSecurityErrorHandlerTest` | M1.1 |
| Testcontainers + `@ServiceConnection` | Starts a real PostgreSQL in Docker for tests and points the datasource at it automatically (never H2). | `PostgresTestcontainer` | M1.2 |
| `@TestConfiguration` + `@Import` | Extra beans only for tests. | `PostgresTestcontainer` | M1.2 |
| Concurrency tests | Many threads released at once by a `CountDownLatch`; assert exact outcomes (one 201, nine 409). | `AuthFlowIT` | M1.3a |
| Mutation checks | Break the code on purpose and confirm a test fails; proves the test bites. | PROGRESS "Checks that the tests bite" | M1.2 |
| `MockHttpServletRequest` | A fake request object from `spring-test`, to unit-test code that reads headers without starting a server. | `ClientCheckTest` | M1.3b |
| Changing the database directly in a test | Instead of waiting 7 days, the test sets `expires_at` with `JdbcTemplate`; time-based behavior is tested without sleeping. | `RefreshFlowIT.setExpiresAt` | M1.3b |
| Mutation check (by hand) | Break one rule on purpose and confirm a test fails; proves the test really covers that rule. | review guides, "Make it fail on purpose" | M1.3b |
| Stub upstream with the JDK `HttpServer` | `com.sun.net.httpserver.HttpServer` (built into the JDK) stands in for a service behind the gateway, so a gateway test needs no other service and no new library. | `GatewayTokenCheckIT` | M1.3c |
| `@DynamicPropertySource` | Adds properties whose value is known only at runtime (the stub's random port) before the Spring context starts. | `GatewayTokenCheckIT` | M1.3c |
| Hibernate `Statistics` | Counts SQL statements the app sent; a test asserts a fixed upper bound to catch N+1. | `CatalogIT.noNPlusOne` | M1.4 |
| `@ParameterizedTest` + `@CsvSource` | One test run once per input row. | `LocalSlotGridTest.gridSizes` | M1.4 |
| Mockito default answers | An unstubbed method returns a default: `0` for `Integer` too, not `null`; stub `null` explicitly when it matters. | `RuleResolverTest` | M1.4 |
| Dates relative to "today" in tests | Tests compute dates from `LocalDate.now(zone)` so they pass on any day without a fixed clock. | `AvailabilityIT` | M1.4 |
| A movable test clock | The service's `Clock` bean is replaced in tests by real time plus an offset the test can move, so a 5-minute hold runs out instantly. | `support/MovableClock`, `BookingLifecycleIT` | M1.5 |
| `@SpringJUnitConfig` + `@MockitoBean` | A small Spring context with only the listed beans (no Boot, no database); `@MockitoBean` puts a Mockito mock in it. Needed to test behavior that only exists on Spring proxies, like `@Retryable`. | `BookingRetryTest` | M1.5 |
| `@RepeatedTest(20)` | Runs one test 20 times in the normal build: the concurrency test's 20 repetitions are part of CI, not a manual loop. | `HoldExpiryJobIT` | M1.7 |
| Start gate with two latches | Threads count down `ready`, wait on `gate`; the test opens the gate when all are ready, so requests really arrive together. | `concurrency/Parallel` | M1.8 |
| System tests in their own Maven module | Tests against the running Compose stack, skipped in a normal build (`skipITs` unless `-Psystem-tests`); plain JUnit and the JDK HTTP client. | `backend/system-tests` | M1.8 |
| WireMock | A stand-in HTTP server for tests: stub answers (`okJson`, status 503, `withFixedDelay`), sequences (`inScenario`), and checks of what arrived (`verify`, `findAll`). | `IdentityGatewayIT` | M1.9 |
| Load balancer without Eureka in tests | `spring.cloud.discovery.client.simple.instances.identity-service[0].uri` points the `lb://identity-service` name at WireMock. | `IdentityGatewayIT` | M1.9 |
| Chaos-style system tests | Stop or kill a real container (`docker compose stop`, `docker kill`), check the promise, start it again and wait until the gateway routes. | `ResilienceSystemIT` | M1.9 |
| Table-driven tests with `@CsvSource` | One row per case (type, start, end, expected code); a new rule or type is a new row. | `BookingRulesTest` | M1.7 |
| Tokens issued at the moved clock | The service checks token expiry with its own clock; when a test moves that clock hours ahead, it signs tokens with `issuedAt` = the moved now. | `CancellationIT` | M1.7 |
| Making a test fail on purpose first | A new assertion is checked against the code without the fix; if it still passes, the test proves nothing (here: macOS clocks have no nanoseconds, so the test moves the clock by 123 ns). | `BookingLifecycleIT.holdCreated_FRBK01` | M1.5 |
| Test data that cleans up after itself | Tests that share a Spring context share one database; a test that creates venues removes them again (`TestCatalog.removeAll`), or other tests' counts change. | `support/TestCatalog` | M1.11 |
| Proving a race test races | Count how often the interesting interleaving happened (here: holds on both sides of the deactivation) and print it; a race that never interleaves proves little. | `DeactivationRaceIT` | M1.11 |

## 9. Architecture ideas behind the code

| Concept | What it is | Where | Since |
|---|---|---|---|
| Microservices with a booking core | Separate deployables; everything that must be one transaction stays in booking-service. | ARCHITECTURE.md §2–3 | M1.1 |
| One error format everywhere | `timestamp, status, code, message, path, requestId, fieldErrors` from every service and the gateway. | `ErrorResponse`, `GatewayErrorResponse` | M1.1 |
| Request ID correlation | One ID per request in headers, logs and errors, to trace a call across services. | `RequestIdFilter`, `RequestIdWebFilter` | M1.1 |
| Correctness in the database | Constraints, not Java checks, guarantee no double-booking and unique emails. | V2 constraints, `uq_app_user_email` | M1.2 |
| Local token verification | Services verify tokens with the public key, so they work while identity-service is down. | `common-web` security | M1.3a |
| One rules resolver | Every rule value comes from one place (override → type → category), so no code ever branches on a category. | `RuleResolver` | M1.4 |
| Read view vs decision | Availability is an unlocked read and can be stale a moment later; the database constraint decides the booking (M1.5). | `AvailabilityService` | M1.4 |
| Module API between modules | Availability reads bookings through `BookingOccupancy`, never the booking table; the DTO carries no member. | `BookingOccupancy`, `OccupiedRange` | M1.4 |
| Spring Framework 7 `@Retryable` | Built into `spring-context` (enable with `@EnableResilientMethods`): reruns a method on chosen exceptions, with delay and jitter. Placed on a different bean from `@Transactional`, so each attempt is a fresh transaction. Only for retry-safe failures (deadlock), never for business refusals. | `BookingService`, `config/RetryConfig` | M1.5 |
| Resilience4j bulkhead | Caps how many calls run at once; extra calls are rejected. Spring Cloud CircuitBreaker adds one (25 calls) around each gateway route by default; rejections count as failures and can open the circuit breaker. Turned off for gateway routes; bulkheads belong on outbound clients (M1.9). | config `api-gateway.yml`, D-41 | M1.8 |
| Composing Resilience4j decorators in code | `Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(cb, Bulkhead.decorateSupplier(bh, call)))`: the order is visible (Retry outermost), unlike annotations whose AOP order is implicit. Policies come from registries by name. | `IdentityGateway` | M1.9 |
| Retry only what a second try can fix | `retry-exceptions` lists 5xx and network errors; a 4xx would fail again, a full bulkhead or an open circuit shouldn't be hammered. | config `resilience4j.retry.instances.identity-service`, `IdentityErrors` | M1.9 |
| Circuit breaker states | CLOSED (calls go through, failures counted over the last 20), OPEN (fail fast for 30 s), HALF_OPEN (3 trial calls decide). `ignore-exceptions` keeps our own mistakes (4xx, full bulkhead) from opening it. | `IdentityGatewayIT` | M1.9 |
| Feign `ErrorDecoder` and `RequestInterceptor` | The decoder turns an HTTP error answer into a typed exception (4xx vs 5xx); the interceptor adds headers (service token, request ID) to every call of that client. Configuration classes named on `@FeignClient(configuration=…)` apply only to that client. | `IdentityErrors`, `ServiceAuthentication` | M1.9 |
| Feign client config keys are context IDs | `spring.cloud.openfeign.client.config.<key>` matches the client's `contextId` (default: its name); `default` applies to all. A wrong key silently leaves the timeout off (caught by a test). | config `openfeign.client.config.default` | M1.9 |
| Per-instance rate limiter | At most N calls per period from this instance to one downstream; extra calls get the fallback at once. Different from limiting clients (distributed, Redis, Phase 4). | `RateLimitedCalls` | M1.9 |
| Configuration refresh | `POST /actuator/refresh` re-reads the Config Server, rebinds `@ConfigurationProperties` and publishes events; beans that copied values at startup (like Resilience4j policies on Boot 4) must be rebuilt by a listener. | `ResilienceRefresher`, `scripts/refresh-config.sh` | M1.9 |
| Health that never fails for a dependency | An open circuit to identity is shown in health but keeps booking UP: booking works without identity, so it must stay in the load balancer. | `ResilienceObservabilityConfig` | M1.9 |
| Gateway fallback status from the cause | The circuit-breaker fallback reads the exception it caught: a timeout → 504, anything else → 503. | `FallbackController` | M1.9 |
| Client-side load balancing (round robin) | The gateway asks Eureka for booking-service's instances and sends requests to them in turn; CONC-10's counters showed an exact 500/500 split. | `lb://booking-service`, `DoubleBookingThroughGatewaySystemIT` | M1.8 |
| Healthy vs ready to route | A container can be healthy before the gateway has fetched it from Eureka (15-50 s here); system tests wait for real routing (instances listed, ping and login answered), not for a fixed time. | `Stack.awaitReady` | M1.8 |
| `@EnableScheduling` and `SchedulingConfigurer` | Scheduled jobs run on Spring's task scheduler. `SchedulingConfigurer.configureTasks` registers a job in code (`addFixedDelayTask(job::run, Duration)`), so the interval comes from typed properties and the job can be switched off. | `HoldExpiryJob`, `config/SchedulingConfig` | M1.7 |
| Fixed delay vs fixed rate | Fixed delay waits N seconds after a run ends before the next starts, so runs never overlap; fixed rate starts every N seconds regardless. | `HoldExpiryJob` | M1.7 |
| Bounded scheduler pool and graceful shutdown | `spring.task.scheduling.pool.size: 2`, thread names `gr-sched-*`; on shutdown running jobs get up to 20 s. Each item is its own transaction, so stopping half-way leaves no half-done work. | `application.yml` | M1.7 |
| `@ConfigurationProperties` record with `@DefaultValue` | Typed, validated settings bound from `gridreserve.holds.expiry.*`; defaults live on the record (`@DefaultValue("30s") Duration interval`), and Spring converts "30s" to a `Duration`. | `HoldExpiryProperties` | M1.7 |
| Micrometer `Counter` with a tag | `Counter.builder("gridreserve.holds.expired").tag("layer", "job").register(registry).increment()`: a metric per label value, visible on `/actuator/metrics`. | `HoldExpiryMetrics` | M1.7 |
| Three expiry layers | Correctness comes from comparing `expires_at` with now everywhere; the status `EXPIRED` is bookkeeping written by confirm, cancel, a blocked hold, or the job, whichever comes first. | ARCHITECTURE §9 | M1.7 |
| Checks first, database decides | Rules in Java give clear early answers, but two requests can pass them at the same moment; the exclusion constraints at INSERT are the guarantee. | `BookingRules`, `BookingTransactions.hold` | M1.5 |
| 404 instead of 403 for other people's data | Answering "not found" hides that the booking exists at all (FR-ROLE-04). | `BookingTransactions.owned`, `BookingService.get` | M1.5 |
| Lazy cleanup at the point of use | Run-out holds that block a new hold are expired inside that hold's transaction, so correctness never waits for the background job (FR-EXP-02/04). | `BookingRepository.lockStaleHolds` | M1.5 |
| SPI to avoid a module cycle | The catalog needs a number only the booking module can compute, but booking already depends on catalog. The catalog defines an interface (`ScheduledBookings`), the booking module implements it, and Spring injects it: no cycle. | `catalog/service/ScheduledBookings`, `booking/service/ScheduledBookingCounter` | M1.11 |
| Soft close | Closing stops new bookings but never touches existing ones; nothing is deleted, so history and audit stay valid. | `AdminCatalogService.setBookableActive` | M1.11 |
| Remote call after the transaction | Read the page in a short transaction, close it, then call identity once for the whole page; a slow identity never holds a database connection or locks. | `AdminBookingService` | M1.11 |
| Three-step idempotency (claim → work + store → release or store refusal) | The claim is its own short transaction so a parallel duplicate sees "in progress" at once; the answer is stored in the work's transaction so they can't come apart; a crash leaves a lease that runs out, so the key isn't stuck. | `BookingService.hold`, `IdempotencyService` | M1.12 |
| Retry only the part that can be retried | The deadlock retry repeats the hold's transaction; wrapping the claim too would make the retry meet its own "in progress" key. | `RetriedTransactions` | M1.12 |
| Audit log in the same transaction | Writing history in the change's transaction makes "changed but not recorded" (or the reverse) impossible; an outbox (Phase 5) applies the same idea to messages. | `BookingTransactions`, `AuditService` | M1.12 |

## 10. Frontend (React, TypeScript, Vite)

| Concept | What it is | Where | Since |
|---|---|---|---|
| Vite dev server and proxy | Serves the app with instant reloads; `server.proxy` forwards `/api` to the gateway, so the browser sees one origin (no CORS, the refresh cookie just works). `vite build` makes the static files for production. | `frontend/vite.config.ts` | M1.6 |
| `tsconfig` strict options | `strict`, `noUncheckedIndexedAccess` (an array lookup may be `undefined`), `verbatimModuleSyntax` (type-only imports are marked `import type`). Catch bugs at compile time. | `frontend/tsconfig.json` | M1.6 |
| Path alias `@/` | `@/components/ui/button` instead of `../../components/ui/button`; set in both `tsconfig.json` and `vite.config.ts`. | `frontend/vite.config.ts` | M1.6 |
| Tailwind 4 with CSS variables (`@theme inline`) | Design tokens are CSS variables on `:root` and `.dark`; `@theme inline` turns them into class names like `bg-primary`. Switching the `.dark` class on `<html>` switches every color at once. | `src/styles/globals.css` | M1.6 |
| shadcn/ui and Radix | shadcn is not a library: its components are copied into our repo (`components/ui`) and we own them. Radix provides the hard parts underneath (keyboard, focus, ARIA), e.g. the Select. | `components/ui/select.tsx`, `components.json` | M1.6 |
| `class-variance-authority` (`cva`) and `cn` | `cva` declares a component's variants (`primary`, `outline`, ...) in one place; `cn` joins classes and lets the later Tailwind class win (`tailwind-merge`). | `components/ui/button.tsx`, `lib/utils.ts` | M1.6 |
| TanStack Query `useQuery` / `useMutation` | Server state with caching, loading and error flags. A query key (`['availability', id, date]`) names the data; `invalidateQueries` after a hold or a 409 makes every view of that data refetch. | `features/availability/SlotPickerPage.tsx`, `api/queryKeys.ts` | M1.6 |
| React Router data router and guards | `createBrowserRouter` with nested routes; `RequireAuth` renders `<Outlet />` for signed-in users and `<Navigate>` to `/login?returnTo=…` otherwise. | `app/router.tsx`, `features/auth/RequireAuth.tsx` | M1.6 |
| URL as state (`useSearchParams`) | The chosen category and date live in the URL (`?category=2`, `?date=…`), so reload, back/forward and shared links keep them. | `BrowsePage`, `SlotPickerPage` | M1.6 |
| React context for the session | `AuthProvider` holds who is signed in and exposes `login`, `logout`; components read it with `useAuth()`. The token itself stays inside `api/client.ts`. | `features/auth/AuthContext.tsx` | M1.6 |
| Access token in memory, refresh cookie | The token is a module variable (gone on reload, unreadable to other tabs and to stored-data theft); the `HttpOnly` cookie, which JavaScript can't read, restores it via `/auth/refresh`. | `api/client.ts` | M1.6 |
| Single-flight refresh | When several requests get `401 TOKEN_EXPIRED` at once, they all await one shared refresh promise, then retry once. | `api/client.ts` `refreshSession` | M1.6 |
| `Intl.DateTimeFormat` with `timeZone` | Formats an instant in any IANA zone, independent of the browser's zone; used for every time shown (venue time, FR-UI-04). | `lib/time.ts` | M1.6 |
| Clock-skew-safe countdown | The hold's length (`expiresAt − createdAt`, both server times) is added to the moment the answer arrived; a wrong device clock can't shorten or lengthen it. | `features/bookings/hold.ts` | M1.6 |
| Pure render and `useNow` | React may render a component any number of times, so render code must not read changing values like `Date.now()`; time comes from state that a timer updates. Enforced by `eslint-plugin-react-hooks` 7. | `lib/useNow.ts`, `HoldTimer` | M1.6 |
| `useEffect` with `setInterval` and cleanup | Starts a ticking timer after render and stops it in the cleanup function, so no timer outlives the component. | `components/patterns/HoldTimer.tsx` | M1.6 |
| Roving tabindex | Only one cell of a grid is in the Tab order (`tabIndex=0`); arrow keys move focus between cells. Keeps keyboard users from tabbing through 17 slots. | `components/patterns/SlotGrid.tsx` | M1.6 |
| ARIA: `aria-pressed`, `aria-disabled`, live regions | Toggle buttons say whether they are on; disabled-but-focusable cells are announced; `aria-live` regions announce the hold's last minute (polite) and conflicts (assertive). | `SlotGrid`, `HoldTimer`, `ConflictNotice` | M1.6 |
| Theme before first paint | A tiny inline script in `index.html` sets the `.dark` class before React loads, so dark-mode users never see a white flash. Only the theme preference is stored in `localStorage`. | `index.html`, `lib/theme.tsx` | M1.6 |
| `import.meta.env.DEV` and dead-code removal | `true` only in the dev server; code behind `if (!import.meta.env.DEV) return []` is dropped from production builds (the component gallery). | `app/router.tsx` | M1.6 |
| Vitest + React Testing Library | Vitest runs tests in a simulated browser (jsdom); RTL renders components and finds elements the way users do (`getByRole('button', { name: '10:00, free' })`). | `*.test.ts(x)` | M1.6 |
| `vi.stubGlobal('fetch')` and fake timers | Replace `fetch` with a fake server, and the clock with one the test moves (`vi.advanceTimersByTime`), so network and time are deterministic. | `api/client.test.ts`, `HoldTimer.test.tsx` | M1.6 |
| `user-event` for keyboard tests | Simulates real typing and key presses (`{ArrowDown}`, `{Enter}`), including focus movement. | `SlotGrid.test.tsx` | M1.6 |
| ESLint flat config | `eslint.config.js` with `typescript-eslint` and the React hooks rules (rules of hooks, purity, dependencies). | `frontend/eslint.config.js` | M1.6 |
| Formik | Keeps a form's values, which fields were touched, errors and the submitting flag; `<Formik onSubmit>` runs only when validation passes. `useField` connects one input. | `features/auth/LoginPage.tsx`, `components/patterns/FormField.tsx` | M1.6 |
| Yup schemas | Rules as data: `yup.string().trim().required('…').email('…').max(254)`; Formik runs the schema and shows the first message per field. `InferType` gives the TypeScript type of the values. | `features/auth/schemas.ts` | M1.6 |
| Client validation vs server validation | The client checks give instant feedback; the server checks are the guard (anyone can send a request without our UI). Server field errors are put back into the form with `setErrors`. | `RegisterPage.tsx` | M1.6 |
| `npm ci` and exact versions | `npm ci` installs exactly what `package-lock.json` says (CI and fresh clones); every package is pinned without `^`. | `frontend/package.json`, CI | M1.6 |
| `crypto.randomUUID()` and `useRef` for the key | The browser makes the UUID; a ref keeps it between renders without causing one, so Retry sends the same key and a new selection gets a new one. | `SlotPickerPage`, `lib/idempotency.ts` | M1.12 |
