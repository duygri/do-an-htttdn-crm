package com.htttdn.crm.security;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.service.AuditTrail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class AdminAccessGuard implements AsyncHandlerInterceptor {
    private static final Set<String> PUBLIC_AUTH_ROUTES = Set.of(
        "/api/admin/auth/login", "/api/admin/auth/refresh", "/api/admin/auth/logout",
        "/api/manager/auth/login", "/api/manager/auth/refresh", "/api/manager/auth/logout");
    private final JwtTokenService jwt;
    private final UserRepository users;
    public AdminAccessGuard(JwtTokenService jwt, UserRepository users) { this.jwt = jwt; this.users = users; }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Use Spring's resolved mapping, never a second interpretation of the raw URI.
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String route = pattern.toString();
        String role = internalRoute(route, "/api/manager") ? "MANAGER"
            : internalRoute(route, "/api/admin") ? "ADMIN" : null;
        if (role == null || PUBLIC_AUTH_ROUTES.contains(route)) return true;
        JwtTokenService.Claims claims = jwt.parse(request.getHeader("Authorization"));
        User user = users.findById(claims.userId()).orElseThrow(() -> new ApiException(
            HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token đăng nhập không hợp lệ."));
        if (!role.equals(claims.role()) || !role.equals(user.getRole())
            || user.isLocked() || user.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, role + "_REQUIRED", "Không có quyền truy cập portal này.");
        }
        request.setAttribute(AuditTrail.ACTOR_ATTRIBUTE,
            new AuditTrail.Actor(user.getId(), user.getFullName(), role));
        return true;
    }
    private static boolean internalRoute(String route, String root) {
        return route.equals(root) || route.startsWith(root + "/");
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        request.removeAttribute(AuditTrail.ACTOR_ATTRIBUTE);
    }
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.removeAttribute(AuditTrail.ACTOR_ATTRIBUTE);
    }
}
