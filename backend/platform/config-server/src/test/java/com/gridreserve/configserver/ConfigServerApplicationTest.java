package com.gridreserve.configserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Uses the config server's "native" backend with a folder from test resources,
 * so the test needs neither Git nor the network.
 */
@SpringBootTest(properties = "spring.cloud.config.server.native.search-locations=classpath:/test-config")
@ActiveProfiles("native")
class ConfigServerApplicationTest {

    @Test
    @DisplayName("PLAT-04: the config server starts")
    void contextLoads() {
    }
}
