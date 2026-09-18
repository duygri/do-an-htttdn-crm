package com.htttdn.crm.security;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.UserRepository;
import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class AdminAccessGuard extends OncePerRequestFilter {
    private final JwtTokenService jwt; private final UserRepository users;
    public AdminAccessGuard(JwtTokenService jwt, UserRepository users) { this.jwt = jwt; this.users = users; }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin") || "OPTIONS".equalsIgnoreCase(request.getMethod()) || path.startsWith("/api/admin/auth/")) { chain.doFilter(request, response); return; }
        try {
            JwtTokenService.Claims claims = jwt.parse(request.getHeader("Authorization"));
            User user = users.findById(claims.userId()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Admin token không hợp lệ."));
            if (!"ADMIN".equals(claims.role()) || !"ADMIN".equals(user.getRole()) || user.isLocked()) throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Admin role required.");
            chain.doFilter(request, response);
        } catch (ApiException e) { response.setStatus(e.status().value()); response.setContentType("application/json"); response.getWriter().print("{\"code\":\"" + e.code() + "\",\"message\":\"" + e.getMessage() + "\"}"); }
    }
}
