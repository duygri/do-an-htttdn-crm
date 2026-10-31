package com.htttdn.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class RateLimitConfig {
    @Bean
    RateLimitStore rateLimitStore(
            @Value("${app.rate-limit.per-minute:120}") int perMinute,
            @Value("${app.rate-limit.auth-max-attempts:5}") int maxAttempts,
            @Value("${app.rate-limit.auth-window-minutes:15}") int windowMinutes) {
        return new RateLimitStore(Clock.systemUTC(), 10_000, perMinute, maxAttempts, windowMinutes);
    }

    @Bean
    RateLimitFilter rateLimitFilter(RateLimitStore store, ObjectMapper mapper,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
            @Value("${app.request.max-body-bytes:1048576}") int maxBodyBytes,
            @Value("${CORS_ORIGINS:${app.cors.origins:http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:4173}}") String origins) {
        Set<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new RateLimitFilter(store, mapper, enabled, trustForwardedFor, maxBodyBytes, allowedOrigins);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
