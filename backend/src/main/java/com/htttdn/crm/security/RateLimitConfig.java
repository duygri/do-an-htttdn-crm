package com.htttdn.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

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
            @Value("${app.rate-limit.trust-forwarded-for:true}") boolean trustForwardedFor,
            @Value("${app.request.max-body-bytes:1048576}") int maxBodyBytes) {
        return new RateLimitFilter(store, mapper, enabled, trustForwardedFor, maxBodyBytes);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
