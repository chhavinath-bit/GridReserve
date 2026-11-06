package com.gridreserve.gateway.security;

import com.gridreserve.common.web.security.AccessTokenVerification;
import com.gridreserve.common.web.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * The gateway's token pre-check (FR-ROLE-01): every path except the public ones needs a valid access
 * token, checked locally with the same rules as every service (FR-AUTH-06). The gateway does not
 * check roles; each service does that for its own endpoints, and checks the token again itself,
 * because it can be reached inside the network without passing through here. The
 * {@code Authorization} header is forwarded unchanged.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
                                                  GatewaySecurityErrorHandler errors) {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // Stateless: nothing about the caller is remembered between requests.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        // Register, login, refresh and logout have no access token yet; identity-service
                        // checks them itself (refresh and logout: cookie + Origin check, FR-AUTH-12).
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        // Temporary M1.1 smoke test.
                        .pathMatchers("/api/v1/ping/**").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // The circuit-breaker fallbacks only ever answer 503.
                        .pathMatchers("/fallback/**").permitAll()
                        // Everything else, unknown paths included: a valid token first.
                        .anyExchange().authenticated())
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtDecoder(decoder))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors));
        return http.build();
    }

    /**
     * The shared servlet-style decoder, wrapped for the reactive gateway. Safe on the event loop:
     * checking a token is pure computation with keys already in memory, with no network or disk call.
     */
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties properties, Clock clock) {
        JwtDecoder decoder = AccessTokenVerification.decoder(properties, clock);
        return token -> Mono.fromCallable(() -> decoder.decode(token));
    }
}
