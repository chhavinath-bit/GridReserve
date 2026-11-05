package com.gridreserve.common.web.config;

import com.gridreserve.common.web.error.GlobalExceptionHandler;
import com.gridreserve.common.web.request.RequestIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Registers the shared web beans in every servlet service that depends on common-web.
 * Listed in META-INF/spring/...AutoConfiguration.imports, the same way Spring's own starters work,
 * so services don't need to component-scan this package.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    /** One UTC clock for the whole service; tests replace it to control time. */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler(Clock clock) {
        return new GlobalExceptionHandler(clock);
    }
}
