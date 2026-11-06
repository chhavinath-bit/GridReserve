package com.gridreserve.common.web.config;

import com.gridreserve.common.web.security.AccessTokenVerification;
import com.gridreserve.common.web.security.JsonSecurityErrorHandler;
import com.gridreserve.common.web.security.JwtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Access-token verification beans, active only in services that declare the resource-server starter.
 * Each service still writes its own {@code SecurityFilterChain}, because only the service knows
 * which of its paths are public and which roles they need.
 */
@AutoConfiguration(after = CommonWebAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(JwtProperties.class)
public class CommonWebSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
        return AccessTokenVerification.decoder(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        return AccessTokenVerification.authenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    JsonSecurityErrorHandler jsonSecurityErrorHandler(JsonMapper jsonMapper, Clock clock) {
        return new JsonSecurityErrorHandler(jsonMapper, clock);
    }
}
