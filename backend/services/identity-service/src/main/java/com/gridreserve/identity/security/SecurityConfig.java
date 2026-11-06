package com.gridreserve.identity.security;

import com.gridreserve.common.web.security.JsonSecurityErrorHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * identity-service's endpoint rules. Stateless: no HTTP session, no login form; the caller proves
 * who it is with a Bearer access token on every request (NFR-SEC-03). Spring's CSRF tokens are off: only
 * refresh and logout are authenticated by a cookie, and they have their own Origin and header check
 * ({@code ClientCheck}, FR-AUTH-12).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RefreshTokenProperties.class, FrontendProperties.class})
class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                            JsonSecurityErrorHandler errors) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // Authenticated by the refresh cookie, not a Bearer token; checked in AuthController.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/ping").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Not routed by the gateway; service credentials for /internal/** come with D-19 (M1.9).
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server
                        .bearerTokenResolver(bearerTokenOutsideAuthPaths())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors));
        return http.build();
    }

    /**
     * Spring checks any Bearer token it finds, even on public paths, and refuses a bad one with 401.
     * On the auth endpoints that would break refresh: a client that sends its expired access token
     * along would get {@code TOKEN_EXPIRED} from the very call meant to fix it. So the header is not
     * read there; these endpoints never use it. The gateway does the same.
     */
    private static BearerTokenResolver bearerTokenOutsideAuthPaths() {
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith("/api/v1/auth/") ? null : bearer.resolve(request);
    }

    /** BCrypt with cost 10 (FR-AUTH-03). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
