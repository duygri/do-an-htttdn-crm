package com.htttdn.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:rateFilterTests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","app.rate-limit.per-minute=50","app.rate-limit.auth-max-attempts=2","app.rate-limit.auth-window-minutes=15","app.rate-limit.trust-forwarded-for=true","app.request.max-body-bytes=512","spring.profiles.active=development","DEV_PASSWORD_RESET_ENABLED=true"})
@AutoConfigureMockMvc @Transactional
class RateLimitFilterIntegrationTest {
    @Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired PasswordEncoder encoder;

    private static final String EMAIL = "customer@rate.test";
    private static final String PASSWORD = "Password123";

    User customer() {
        var u = new User();
        u.setEmail(EMAIL); u.setFullName("Rate Test"); u.setRole("CUSTOMER");
        u.setPasswordHash(encoder.encode(PASSWORD));
        em.persist(u); em.flush(); return u;
    }

    static RequestPostProcessor from(String ip) {
        return request -> { request.setRemoteAddr(ip); return request; };
    }

    @Test void failedLoginsReturn429AfterMaxAttempts() throws Exception {
        customer();
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"WrongPass1\"}";
        String url = "/api/auth/login";
        mvc.perform(post(url).with(from("10.0.1.1")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.1.1")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.1.1")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test void successfulLoginsNeverCountTowardTheLimit() throws Exception {
        customer();
        String good = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
        String bad = "{\"email\":\"" + EMAIL + "\",\"password\":\"WrongPass1\"}";
        String url = "/api/auth/login";
        mvc.perform(post(url).with(from("10.0.2.1")).contentType(MediaType.APPLICATION_JSON).content(good)).andExpect(status().isOk());
        mvc.perform(post(url).with(from("10.0.2.1")).contentType(MediaType.APPLICATION_JSON).content(good)).andExpect(status().isOk());
        mvc.perform(post(url).with(from("10.0.2.1")).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.2.1")).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.2.1")).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isTooManyRequests());
    }

    @Test void differentEmailOnSameIpIsNotBlocked() throws Exception {
        String url = "/api/auth/login";
        String a = "{\"email\":\"a@rate.test\",\"password\":\"WrongPass1\"}";
        String b = "{\"email\":\"b@rate.test\",\"password\":\"WrongPass1\"}";
        mvc.perform(post(url).with(from("10.0.3.1")).contentType(MediaType.APPLICATION_JSON).content(a)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.3.1")).contentType(MediaType.APPLICATION_JSON).content(a)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.3.1")).contentType(MediaType.APPLICATION_JSON).content(a)).andExpect(status().isTooManyRequests());
        mvc.perform(post(url).with(from("10.0.3.1")).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isUnauthorized());
    }

    @Test void payosWebhookIsExemptFromRateLimiting() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/payments/payos/webhook").with(from("10.0.4.1")).contentType(MediaType.APPLICATION_JSON).content("{\"junk\":true}"))
                    .andExpect(result -> assertNotEquals(429, result.getResponse().getStatus()));
        }
        mvc.perform(get("/api/products").with(from("10.0.4.1"))).andExpect(status().isOk());
    }

    @Test void refreshCookieIsSecureOnlyBehindHttps() throws Exception {
        customer();
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post("/api/auth/login").with(from("10.0.10.1")).header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
        mvc.perform(post("/api/auth/login").with(from("10.0.10.2"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", not(containsString("Secure"))));
    }

    @Test void forwardedForUsesTheLastProxyHop() throws Exception {
        String url = "/api/auth/login";
        String bad = "{\"email\":\"forward@rate.test\",\"password\":\"WrongPass1\"}";
        mvc.perform(post(url).with(from("10.0.9.1")).header("X-Forwarded-For", "203.0.113.1, 10.0.8.1")
                .contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.9.1")).header("X-Forwarded-For", "203.0.113.2, 10.0.8.1")
                .contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.9.1")).header("X-Forwarded-For", "203.0.113.3, 10.0.8.1")
                .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post(url).with(from("10.0.9.1")).header("X-Forwarded-For", "203.0.113.4, 10.0.8.2")
                .contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
    }

    @Test void optionsRequestsAreNeverBlocked() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(options("/api/auth/login").with(from("10.0.5.1")))
                    .andExpect(result -> assertNotEquals(429, result.getResponse().getStatus()));
        }
    }

    @Test void encodedAuthPathsStillCountFailures() throws Exception {
        var filter = new RateLimitFilter(new RateLimitStore(java.time.Clock.systemUTC(), 100, 1000, 2, 15),
                new ObjectMapper(), true, false, 1024, java.util.Set.of());
        String body = "{\"email\":\"encoded@rate.test\",\"password\":\"WrongPass1\"}";
        for (int i = 0; i < 2; i++) {
            var response = requestThrough(filter, body);
            org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
        }
        var response = requestThrough(filter, body);
        org.junit.jupiter.api.Assertions.assertEquals(429, response.getStatus());
    }

    /** Tomcat hands the filter the raw URI; MVC routes on the decoded servlet path. */
    private static org.springframework.mock.web.MockHttpServletResponse requestThrough(RateLimitFilter filter, String body) throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/%61uth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.setRemoteAddr("10.0.20.1");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));
        return response;
    }

    @Test void oversizedPayloadsConsumeTheGlobalBucket() throws Exception {
        String huge = "{\"name\":\"" + "a".repeat(600) + "\"}";
        for (int i = 0; i < 50; i++) {
            mvc.perform(post("/api/auth/register").with(from("10.0.21.1")).contentType(MediaType.APPLICATION_JSON).content(huge))
                    .andExpect(status().isPayloadTooLarge());
        }
        mvc.perform(post("/api/auth/register").with(from("10.0.21.1")).contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isTooManyRequests());
    }

    @Test void registerValidationFailuresDoNotLockLogin() throws Exception {
        customer();
        String invalidRegister = "{\"email\":\"" + EMAIL + "\"}";
        mvc.perform(post("/api/auth/register").with(from("10.0.22.1")).contentType(MediaType.APPLICATION_JSON).content(invalidRegister)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(from("10.0.22.1")).contentType(MediaType.APPLICATION_JSON).content(invalidRegister)).andExpect(status().isBadRequest());
        String bad = "{\"email\":\"" + EMAIL + "\",\"password\":\"WrongPass1\"}";
        mvc.perform(post("/api/auth/login").with(from("10.0.22.1")).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(from("10.0.22.1")).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(from("10.0.22.1")).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isTooManyRequests());
    }

    @Test void changePasswordFailuresAreNotRateLimited() throws Exception {
        customer();
        String good = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
        String token = JsonPath.read(mvc.perform(post("/api/auth/login").with(from("10.0.23.1"))
                        .contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
        String change = "{\"currentPassword\":\"WrongPass1\",\"newPassword\":\"Password123\"}";
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/change-password").with(from("10.0.23.1")).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(change)).andExpect(status().isBadRequest());
        }
    }

    @Test void onlyInvalidResetCodesCountAsFailures() throws Exception {
        customer();
        String url = "/api/auth/reset-password";
        String invalidPassword = "{\"email\":\"" + EMAIL + "\",\"code\":\"" + "A".repeat(43) + "\",\"newPassword\":\"short\"}";
        mvc.perform(post(url).with(from("10.0.24.1")).contentType(MediaType.APPLICATION_JSON).content(invalidPassword)).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(from("10.0.24.1")).contentType(MediaType.APPLICATION_JSON).content(invalidPassword)).andExpect(status().isBadRequest());
        String invalidCode = "{\"email\":\"" + EMAIL + "\",\"code\":\"" + "A".repeat(43) + "\",\"newPassword\":\"Password123\"}";
        mvc.perform(post(url).with(from("10.0.24.1")).contentType(MediaType.APPLICATION_JSON).content(invalidCode)).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(from("10.0.24.1")).contentType(MediaType.APPLICATION_JSON).content(invalidCode)).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(from("10.0.24.1")).contentType(MediaType.APPLICATION_JSON).content(invalidCode)).andExpect(status().isTooManyRequests());
    }

    @Test void throttledResponsesCarryCorsHeadersOnlyForAllowedOrigins() throws Exception {
        customer();
        String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"WrongPass1\"}";
        String url = "/api/auth/login";
        String allowed = "http://localhost:5173";
        mvc.perform(post(url).with(from("10.0.25.1")).header("Origin", allowed).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.25.1")).header("Origin", allowed).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).with(from("10.0.25.1")).header("Origin", allowed).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", allowed))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(post(url).with(from("10.0.25.2")).header("Origin", "http://evil.test").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(from("10.0.25.2")).header("Origin", "http://evil.test").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(from("10.0.25.2")).header("Origin", "http://evil.test").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
