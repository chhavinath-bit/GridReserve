package com.gridreserve.identity.auth;

import com.gridreserve.common.web.security.AccessTokenVerification;
import com.gridreserve.identity.support.Http;
import com.gridreserve.identity.support.Http.Response;
import com.gridreserve.identity.support.ServiceIntegrationTest;
import com.gridreserve.identity.support.TestJwtKeys;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Register, log in and call {@code /me} over real HTTP against PostgreSQL. */
@ServiceIntegrationTest
class AuthFlowIT {

    @Value("${local.server.port}")
    int port;

    Http http;

    @BeforeEach
    void setUp() {
        http = new Http(port);
    }

    /** A fresh address per test: the database is shared by all test classes. */
    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    private static String registerBody(String email, String password) {
        return """
                {"email":"%s","password":"%s","displayName":"Test User"}""".formatted(email, password);
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    private String loginToken(String email, String password) {
        Response login = http.post("/api/v1/auth/login", loginBody(email, password));
        assertThat(login.status()).isEqualTo(200);
        return login.body().get("accessToken").asString();
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("FR-AUTH-01/04: register creates a MEMBER, stores the email lower-cased, never returns the hash")
        void registerCreatesMember_FRAUTH01() {
            String email = uniqueEmail();

            Response response = http.post("/api/v1/auth/register",
                    // A role in the body is ignored: self-registration never creates an ADMIN.
                    """
                    {"email":"%s","password":"secret-pass","displayName":"  Test User ","role":"ADMIN"}"""
                            .formatted(email.toUpperCase()));

            assertThat(response.status()).isEqualTo(201);
            assertThat(response.body().get("email").asString()).isEqualTo(email);
            assertThat(response.body().get("displayName").asString()).isEqualTo("Test User");
            assertThat(response.body().get("role").asString()).isEqualTo("MEMBER");
            assertThat(response.raw().body()).doesNotContain("secret-pass", "$2a$", "passwordHash");
        }

        @Test
        @DisplayName("FR-AUTH-02: the same email in another case → 409 EMAIL_TAKEN")
        void duplicateEmailInOtherCase_FRAUTH02() {
            String email = uniqueEmail();
            http.post("/api/v1/auth/register", registerBody(email, "secret-pass"));

            Response again = http.post("/api/v1/auth/register", registerBody(email.toUpperCase(), "other-pass"));

            assertThat(again.status()).isEqualTo(409);
            assertThat(again.code()).isEqualTo("EMAIL_TAKEN");
        }

        @Test
        @DisplayName("FR-AUTH-02: ten simultaneous registrations of one email → exactly one 201 and nine 409")
        void concurrentRegistrationsOneWinner_FRAUTH02() throws Exception {
            String email = uniqueEmail();
            int attempts = 10;
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
                for (int i = 0; i < attempts; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return http.post("/api/v1/auth/register", registerBody(email, "secret-pass")).status();
                    }));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get());
                }
                assertThat(statuses).containsOnly(201, 409);
                assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            }
        }

        @Test
        @DisplayName("FR-AUTH-03: a password shorter than 8 characters → 400 with a field error")
        void shortPasswordRejected_FRAUTH03() {
            Response response = http.post("/api/v1/auth/register", registerBody(uniqueEmail(), "short"));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
            assertThat(response.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("password");
        }

        @Test
        @DisplayName("FR-AUTH-03: 40 accented characters (80 bytes) exceed BCrypt's 72 bytes → 400, not a 500")
        void multiBytePasswordOverLimit_FRAUTH03() {
            Response response = http.post("/api/v1/auth/register", registerBody(uniqueEmail(), "é".repeat(40)));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
        }

        @Test
        @DisplayName("FR-AUTH-02: an invalid email → 400 with a field error")
        void invalidEmailRejected_FRAUTH02() {
            Response response = http.post("/api/v1/auth/register", registerBody("not-an-email", "secret-pass"));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("email");
        }
    }

    @Nested
    @DisplayName("Login and access tokens")
    class Login {

        @Test
        @DisplayName("FR-AUTH-05/06: login returns a 15-minute RS256 token with kid, sub and role, no personal data")
        void loginReturnsToken_FRAUTH05() throws Exception {
            String email = uniqueEmail();
            Response registered = http.post("/api/v1/auth/register", registerBody(email, "secret-pass"));

            Response login = http.post("/api/v1/auth/login", loginBody(email.toUpperCase(), "secret-pass"));

            assertThat(login.status()).isEqualTo(200);
            assertThat(login.body().get("tokenType").asString()).isEqualTo("Bearer");
            assertThat(login.body().get("expiresIn").asLong()).isEqualTo(900);
            SignedJWT jwt = SignedJWT.parse(login.body().get("accessToken").asString());
            assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
            assertThat(jwt.getHeader().getKeyID()).isEqualTo(TestJwtKeys.KEY_ID);
            assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(registered.body().get("id").asString());
            assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("MEMBER");
            assertThat(Duration.between(jwt.getJWTClaimsSet().getIssueTime().toInstant(),
                    jwt.getJWTClaimsSet().getExpirationTime().toInstant())).isEqualTo(Duration.ofMinutes(15));
            assertThat(jwt.getJWTClaimsSet().getClaims()).doesNotContainKeys("email", "name");
        }

        @Test
        @DisplayName("FR-AUTH-07: wrong password and unknown email give the identical 401 INVALID_CREDENTIALS")
        void sameAnswerForWrongPasswordAndUnknownEmail_FRAUTH07() {
            Response wrongPassword = http.post("/api/v1/auth/login",
                    loginBody("alice@gridreserve.test", "wrong-password"));
            Response unknownEmail = http.post("/api/v1/auth/login", loginBody(uniqueEmail(), "wrong-password"));

            assertThat(wrongPassword.status()).isEqualTo(401);
            assertThat(unknownEmail.status()).isEqualTo(401);
            assertThat(wrongPassword.code()).isEqualTo("INVALID_CREDENTIALS").isEqualTo(unknownEmail.code());
            assertThat(wrongPassword.body().get("message")).isEqualTo(unknownEmail.body().get("message"));
        }

        @Test
        @DisplayName("FR-SEED-02: the seeded demo accounts can log in; the admin's token says ADMIN")
        void seededAccountsLogIn_FRSEED02() throws Exception {
            String admin = loginToken("admin@gridreserve.test", "demo-admin-pass");
            loginToken("alice@gridreserve.test", "demo-alice-pass");
            loginToken("bob@gridreserve.test", "demo-bob-pass");

            assertThat(SignedJWT.parse(admin).getJWTClaimsSet().getStringClaim("role")).isEqualTo("ADMIN");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/me")
    class Me {

        @Test
        @DisplayName("FR-AUTH-08: with a valid token, /me returns the user's id, email, name and role")
        void meReturnsCurrentUser_FRAUTH08() {
            Response me = http.get("/api/v1/me", loginToken("alice@gridreserve.test", "demo-alice-pass"));

            assertThat(me.status()).isEqualTo(200);
            assertThat(me.body().get("email").asString()).isEqualTo("alice@gridreserve.test");
            assertThat(me.body().get("role").asString()).isEqualTo("MEMBER");
            assertThat(me.raw().body()).doesNotContain("$2a$");
        }

        @Test
        @DisplayName("FR-ROLE-02: no token → 401 UNAUTHENTICATED in the standard format")
        void noToken_FRROLE02() {
            Response me = http.get("/api/v1/me", null);

            assertThat(me.status()).isEqualTo(401);
            assertThat(me.code()).isEqualTo("UNAUTHENTICATED");
            assertThat(me.body().get("requestId").asString()).isNotBlank();
        }

        @Test
        @DisplayName("FS-14: an expired token → 401 TOKEN_EXPIRED; a token signed with another key → 401 UNAUTHENTICATED")
        void expiredOrForeignToken_FS14() {
            Instant issued = Instant.now().minus(Duration.ofHours(1));
            String expired = sign(TestJwtKeys.KEYS, issued);
            String foreign = sign(TestJwtKeys.newKeyPair(), Instant.now());

            assertThat(http.get("/api/v1/me", expired).code()).isEqualTo("TOKEN_EXPIRED");
            Response foreignResponse = http.get("/api/v1/me", foreign);
            assertThat(foreignResponse.status()).isEqualTo(401);
            assertThat(foreignResponse.code()).isEqualTo("UNAUTHENTICATED");
        }

        @Test
        @DisplayName("FR-AUTH-08: a validly signed token for a user that does not exist → 401")
        void tokenForMissingUser_FRAUTH08() {
            Response me = http.get("/api/v1/me", sign(TestJwtKeys.KEYS, Instant.now(), "999999"));

            assertThat(me.status()).isEqualTo(401);
        }

        private String sign(KeyPair keys, Instant issuedAt) {
            return sign(keys, issuedAt, "2");
        }

        private String sign(KeyPair keys, Instant issuedAt, String subject) {
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                    .privateKey((RSAPrivateKey) keys.getPrivate()).keyID(TestJwtKeys.KEY_ID).build();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(TestJwtKeys.ISSUER).audience(List.of(TestJwtKeys.AUDIENCE))
                    .subject(subject).claim(AccessTokenVerification.ROLE_CLAIM, "MEMBER")
                    .issuedAt(issuedAt).expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                    .build();
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(TestJwtKeys.KEY_ID).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)))
                    .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }
    }
}
