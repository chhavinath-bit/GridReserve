package com.gridreserve.identity.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Services allowed to ask for a service token (D-19), keyed by client ID ({@code booking-service}).
 * Only the SHA-256 of each client's secret is kept here, like a password hash: the secret itself is in
 * the calling service's environment, and this hash comes from identity's environment, never the config repo.
 * SHA-256 (not BCrypt) is enough because the secrets are long random values, not human passwords.
 */
@Validated
@ConfigurationProperties("gridreserve.service-clients")
public record ServiceClientProperties(
        @DefaultValue("5m") @NotNull Duration tokenTtl,
        @Valid Map<String, Client> clients) {

    public ServiceClientProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    public record Client(@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String secretSha256) {

        @Override
        public String toString() {
            return "Client[secretSha256=<hidden>]";
        }
    }
}
