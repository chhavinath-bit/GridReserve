package com.gridreserve.booking.config;

import com.gridreserve.common.web.security.JsonSecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * booking-service's endpoint rules (FR-ROLE-01..03). The gateway already refused requests without a
 * valid token, but this service checks again: it can be reached inside the network without passing
 * the gateway, and only it knows which roles its endpoints need. Stateless, Bearer tokens only, so
 * Spring's CSRF tokens are off (no cookie authenticates anything here).
 */
@Configuration(proxyBeanMethods = false)
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
                        // Temporary M1.1 smoke test.
                        .requestMatchers("/api/v1/ping/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Other actuator endpoints (refresh, metrics): an admin or another service (D-19).
                        .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SERVICE")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Catalog changes (FR-CAT-07..16) live next to the reads they change; members only read.
                        .requestMatchers(HttpMethod.POST, "/api/v1/venues/**", "/api/v1/bookables/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/venues/**", "/api/v1/bookables/**").hasRole("ADMIN")
                        // Everything else, catalog browsing included, needs a signed-in person (PROJECT.md §6); a
                        // service token (role SERVICE) must not act as a member.
                        .anyRequest().hasAnyRole("MEMBER", "ADMIN"))
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors));
        return http.build();
    }
}
