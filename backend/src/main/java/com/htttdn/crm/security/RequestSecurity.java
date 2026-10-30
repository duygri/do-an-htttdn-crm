package com.htttdn.crm.security;

import jakarta.servlet.http.HttpServletRequest;

/** Request scheme helpers that also work behind a TLS-terminating proxy. */
public final class RequestSecurity {
    private RequestSecurity() {}

    public static boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) return true;
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto.trim());
    }
}
