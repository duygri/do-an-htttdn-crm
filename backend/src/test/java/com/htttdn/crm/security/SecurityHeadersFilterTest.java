package com.htttdn.crm.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class SecurityHeadersFilterTest {
    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/manager/login", "/admin/login", "/assets/app.js", "/assets/app.css"})
    void browserPagesAndAssetsAreNotGivenTheJsonOnlyPolicy(String path) throws Exception {
        var response = request(path, "", "text/html");
        String policy = response.getHeader("Content-Security-Policy");
        assertNotNull(policy);
        assertFalse(policy.contains("default-src 'none'"), "The API-only policy must not block the SPA's scripts, styles and fonts");
        assertTrue(policy.contains("frame-ancestors 'none'"));
        assertTrue(policy.contains("object-src 'none'"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("max-age=31536000; includeSubDomains", response.getHeader("Strict-Transport-Security"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "/api/products", "/api/manager/products", "/api;v=1/manager/products"})
    void apiResponsesKeepTheRestrictivePolicyIncludingContextAndMatrixPaths(String path) throws Exception {
        for (String context : new String[]{"", "/crm"}) {
            var response = request(context + path, context, "application/json");
            assertTrue(response.getHeader("Content-Security-Policy").contains("default-src 'none'"));
            assertEquals("DENY", response.getHeader("X-Frame-Options"));
            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        }
    }

    private MockHttpServletResponse request(String uri, String context, String contentType) throws Exception {
        var request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(context);
        request.setSecure(true);
        var response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(request, response, (req, res) -> res.setContentType(contentType));
        return response;
    }
}
