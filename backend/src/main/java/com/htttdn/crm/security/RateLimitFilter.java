package com.htttdn.crm.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Two-tier rate limiter: a per-IP token bucket on every API request, and a per-IP+account limit on failed auth attempts. */
final class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password",
            "/api/auth/reset-password", "/api/auth/change-password",
            "/api/admin/auth/login", "/api/manager/auth/login");
    private static final String WEBHOOK_PATH = "/api/payments/payos/webhook";

    private final RateLimitStore store;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final boolean trustForwardedFor;
    private final int maxBodyBytes;

    RateLimitFilter(RateLimitStore store, ObjectMapper mapper, boolean enabled, boolean trustForwardedFor, int maxBodyBytes) {
        this.store = store;
        this.mapper = mapper;
        this.enabled = enabled;
        this.trustForwardedFor = trustForwardedFor;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean webhook = "POST".equals(method) && WEBHOOK_PATH.equals(path);
        boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        CachedBodyRequest wrapped = hasBody ? new CachedBodyRequest(request, maxBodyBytes) : null;
        if (wrapped != null && wrapped.bodyTooLarge()) { writeJsonError(response, 413, "PAYLOAD_TOO_LARGE", "Payload quá lớn. Giới hạn " + maxBodyBytes / 1024 + " KB.", null); return; }
        HttpServletRequest effective = wrapped != null ? wrapped : request;
        if (!enabled || "OPTIONS".equalsIgnoreCase(method) || webhook) { chain.doFilter(effective, response); return; }

        String ipHash = AuthService.hash(clientIp(request));
        if (!store.tryConsumeGlobal(ipHash)) { write429(response, store.globalRetryAfterSeconds(ipHash)); return; }

        String authKey = null;
        if ("POST".equals(method) && AUTH_PATHS.contains(path)) {
            authKey = authKey(ipHash, wrapped);
            if (store.isAuthBlocked(authKey)) { write429(response, store.authRetryAfterSeconds(authKey)); return; }
        }

        chain.doFilter(effective, response);
        if (authKey != null && response.getStatus() >= 400) store.recordAuthFailure(authKey);
    }

    private String authKey(String ipHash, CachedBodyRequest request) {
        String email = null;
        try {
            byte[] body = request.cachedBody();
            if (body.length > 0) {
                JsonNode node = mapper.readTree(body);
                email = node.path("email").asText(null);
            }
        } catch (Exception ignored) { }
        if (email == null || email.isBlank()) return ipHash + ":noemail";
        return ipHash + ":" + AuthService.hash(email.trim().toLowerCase(Locale.ROOT));
    }

    /** Behind a proxy the client-supplied hops come first; the last hop is the address added by the trusted proxy. */
    private String clientIp(HttpServletRequest request) {
        if (!trustForwardedFor) return request.getRemoteAddr();
        String forwarded = forwardedFor(request);
        if (forwarded == null || forwarded.isBlank()) return request.getRemoteAddr();
        String last = null;
        for (String hop : forwarded.split(",")) if (!hop.isBlank()) last = hop.trim();
        return last == null ? request.getRemoteAddr() : last;
    }

    /** Spring's ForwardedHeaderFilter hides these headers from downstream filters, so fall back to the original request. */
    private static String forwardedFor(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (value != null) return value;
        jakarta.servlet.ServletRequest current = request;
        while (current instanceof jakarta.servlet.ServletRequestWrapper wrapper) {
            jakarta.servlet.ServletRequest next = wrapper.getRequest();
            if (next == null || next == current) break;
            if (next instanceof HttpServletRequest http) {
                value = http.getHeader("X-Forwarded-For");
                if (value != null) return value;
            }
            current = next;
        }
        return null;
    }

    private static void write429(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        writeJsonError(response, 429, "RATE_LIMITED", "Quá nhiều yêu cầu. Vui lòng thử lại sau.", String.valueOf(retryAfterSeconds));
    }

    private static void writeJsonError(HttpServletResponse response, int status, String code, String message, String retryAfter) throws IOException {
        response.setStatus(status);
        if (retryAfter != null) response.setHeader("Retry-After", retryAfter);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
