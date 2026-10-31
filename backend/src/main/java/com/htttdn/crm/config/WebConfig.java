package com.htttdn.crm.config;

import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Configuration; import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final com.htttdn.crm.security.AdminAccessGuard accessGuard;
    public WebConfig(com.htttdn.crm.security.AdminAccessGuard accessGuard) { this.accessGuard = accessGuard; }
    // No URL matcher: authorize the mapping that Spring actually resolved.
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(accessGuard); }
    @Value("${CORS_ORIGINS:${app.cors.origins:http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:4173}}") private String origins;
    @Override public void addCorsMappings(CorsRegistry registry){registry.addMapping("/api/**").allowedOrigins(origins.split(",")).allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS").allowedHeaders("*").allowCredentials(true).maxAge(3600);}
}
