package com.gridreserve.booking.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL 16 (the same image as Compose) for tests; never H2, because exclusion
 * constraints and tstzrange exist only in PostgreSQL. {@code @ServiceConnection} points the
 * datasource at the container, so no URL or password is configured anywhere.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16");
    }
}
