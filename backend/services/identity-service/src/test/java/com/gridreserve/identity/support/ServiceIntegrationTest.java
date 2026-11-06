package com.gridreserve.identity.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts identity-service on a random port with a PostgreSQL Testcontainer and throwaway JWT keys,
 * without Config Server or Eureka. Every test class uses this same setup, so Spring caches one
 * context and all classes share one container (and one Flyway run).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CONFIG_IMPORT=optional:classpath:/no-config-server.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        // In a real run these come from the config repo (application.yml, identity-service.yml).
        "gridreserve.web.frontend-origin=" + ServiceIntegrationTest.FRONTEND_ORIGIN,
        "gridreserve.auth.refresh.ttl=7d",
        "gridreserve.auth.refresh.cookie-name=gr_refresh",
        "gridreserve.auth.refresh.cookie-secure=false"
})
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(PostgresTestcontainer.class)
public @interface ServiceIntegrationTest {

    String FRONTEND_ORIGIN = "http://localhost:5173";
}
