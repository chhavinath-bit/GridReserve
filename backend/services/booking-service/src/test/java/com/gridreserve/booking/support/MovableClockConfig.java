package com.gridreserve.booking.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Replaces common-web's system clock (it backs off when a Clock bean exists). */
@TestConfiguration(proxyBeanMethods = false)
public class MovableClockConfig {

    @Bean
    MovableClock clock() {
        return new MovableClock();
    }
}
