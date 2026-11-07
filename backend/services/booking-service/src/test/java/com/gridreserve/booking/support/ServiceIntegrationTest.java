package com.gridreserve.booking.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts booking-service on a random port with a PostgreSQL Testcontainer, throwaway JWT keys and a
 * {@link MovableClock}, without Config Server or Eureka. Every test class uses this same setup, so Spring caches one context and all classes
 * share one container (and one Flyway run).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CONFIG_IMPORT=optional:classpath:/no-config-server.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({PostgresTestcontainer.class, MovableClockConfig.class})
public @interface ServiceIntegrationTest {
}
