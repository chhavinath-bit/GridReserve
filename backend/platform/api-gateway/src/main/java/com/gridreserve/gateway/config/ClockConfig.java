package com.gridreserve.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** UTC clock for error timestamps; tests can replace it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
