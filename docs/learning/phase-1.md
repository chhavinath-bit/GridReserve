# Phase 1: what to know to read every line

Everything the Phase 1 code uses, grouped by topic. **Since** names the milestone that introduced it, so you can study in the order the code was written. Each milestone adds its new rows in its own PR (CLAUDE.md §2), and each review guide's "Learn first" table repeats that milestone's rows.

Suggested order: Java → Spring core → Spring MVC → Data → Spring Cloud → Security → Testing. Sections 1–3 are the base for everything else.

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

