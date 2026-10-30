package com.htttdn.crm.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class SecurityHeadersConfig {
    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
