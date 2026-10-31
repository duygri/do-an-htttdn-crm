package com.htttdn.crm.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Two-tier rate limiter: a per-IP token bucket on every API request, and a per-IP+account limit on failed auth attempts. */
final class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/admin/auth/login", "/api/manager/auth/login");
    private static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";
    private static final String WEBHOOK_PATH = "/api/payments/payos/webhook";

    /** Path matching must use the same decoded path Spring MVC routes on, otherwise encoded URLs bypass the auth tier. */
    private static final UrlPathHelper PATH_HELPER = pathHelper();

    private final RateLimitStore store;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final boolean trustForwardedFor;
    private final int maxBodyBytes;
    private final Set<String> allowedOrigins;

    RateLimitFilter(RateLimitStore store, ObjectMapper mapper, boolean enabled, boolean trustForwardedFor,
            int maxBodyBytes, Set<String> allowedOrigins) {
        this.store = store;
        this.mapper = mapper;
        this.enabled = enabled;
        this.trustForwardedFor = trustForwardedFor;
        this.maxBodyBytes = maxBodyBytes;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = PATH_HELPER.getPathWithinApplication(request);
        String method = request.getMethod();
        boolean webhook = "POST".equals(method) && WEBHOOK_PATH.equals(path);
        boolean exempt = !enabled || "OPTIONS".equalsIgnoreCase(method) || webhook;
        String origin = corsOrigin(request);
        boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        CachedBodyRequest wrapped = hasBody ? new CachedBodyRequest(request, maxBodyBytes) : null;
        HttpServletRequest effective = wrapped != null ? wrapped : request;

        // Consume the global token before the body check so oversized payloads cannot bypass the limiter.
        String ipHash = exempt ? null : AuthService.hash(clientIp(request));
        if (ipHash != null && !store.tryConsumeGlobal(ipHash)) {
            writeJsonError(response, origin, 429, "RATE_LIMITED", "Quá nhiều yêu cầu. Vui lòng thử lại sau.",
                    String.valueOf(store.globalRetryAfterSeconds(ipHash)));
            return;
        }
        if (wrapped != null && wrapped.bodyTooLarge()) {
            writeJsonError(response, origin, 413, "PAYLOAD_TOO_LARGE",
                    "Payload quá lớn. Giới hạn " + maxBodyBytes / 1024 + " KB.", null);
            return;
        }
        if (exempt) { chain.doFilter(effective, response); return; }

        String authKey = null;
        if ("POST".equals(method) && AUTH_PATHS.contains(path)) {
            authKey = authKey(ipHash, wrapped);
            if (store.isAuthBlocked(authKey)) {
                writeJsonError(response, origin, 429, "RATE_LIMITED", "Quá nhiều yêu cầu. Vui lòng thử lại sau.",
                        String.valueOf(store.authRetryAfterSeconds(authKey)));
                return;
            }
        }
        if (authKey == null) { chain.doFilter(effective, response); return; }

        // The error code lives in the JSON body, so buffer auth responses to tell real credential failures from validation errors.
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(effective, captured);
            if (countsAsAuthFailure(path, captured)) store.recordAuthFailure(authKey);
        } finally {
            captured.copyBodyToResponse();
        }
    }

    private static boolean countsAsAuthFailure(String path, ContentCachingResponseWrapper response) {
        int status = response.getStatus();
        if (status == 401 || status == 403) return true;
        if (status != 400 || !RESET_PASSWORD_PATH.equals(path)) return false;
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8).contains("\"code\":\"RESET_CODE_INVALID\"");
    }

    private String authKey(String ipHash, CachedBodyRequest request) {
        String email = null;
        try {
            byte[] body = request == null ? new byte[0] : request.cachedBody();
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

    private String corsOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || !allowedOrigins.contains(origin)) return null;
        return origin;
    }

    private static void writeJsonError(HttpServletResponse response, String origin, int status, String code, String message, String retryAfter) throws IOException {
        response.setStatus(status);
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
            if (retryAfter != null) response.setHeader("Access-Control-Expose-Headers", "Retry-After");
        }
        if (retryAfter != null) response.setHeader("Retry-After", retryAfter);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private static UrlPathHelper pathHelper() {
        UrlPathHelper helper = new UrlPathHelper();
        helper.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return helper;
    }
}
