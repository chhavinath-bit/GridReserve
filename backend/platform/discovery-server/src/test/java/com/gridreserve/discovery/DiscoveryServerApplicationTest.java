package com.gridreserve.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTest {

    @Test
    @DisplayName("PLAT-02: the Eureka registry starts")
    void contextLoads() {
    }
}
