package com.htttdn.crm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/** Baseline headers for all responses; the JSON-only CSP must never block the hosted SPA. */
final class SecurityHeadersFilter extends OncePerRequestFilter {
    private static final String CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    private static final String PAGE_CSP = "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'";
    private static final String HSTS = "max-age=31536000; includeSubDomains";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        boolean api = path.equals("/api") || path.startsWith("/api/");
        response.setHeader("Content-Security-Policy", api ? CSP : PAGE_CSP);
        if (RequestSecurity.isHttps(request)) response.setHeader("Strict-Transport-Security", HSTS);
        chain.doFilter(request, response);
    }
}
