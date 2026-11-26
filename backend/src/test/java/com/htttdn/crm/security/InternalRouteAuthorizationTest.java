package com.htttdn.crm.security;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiExceptionHandler;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.service.AuditTrail;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalRouteAuthorizationTest {
    private final JwtTokenService jwt = mock(JwtTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private MockMvc mvc;
    private User user;

    @BeforeEach void setup() {
        user = new User(); org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        user.setRole("ADMIN"); user.setFullName("Admin");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(jwt.parse(any())).thenAnswer(invocation -> {
            String header = invocation.getArgument(0);
            if (header == null) throw new com.htttdn.crm.exception.ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing token");
            return new JwtTokenService.Claims(1L, header.substring(7));
        });
        mvc = MockMvcBuilders.standaloneSetup(new InternalEndpoints())
            .setControllerAdvice(new ApiExceptionHandler())
            .addInterceptors(new AdminAccessGuard(jwt, users)).build();
    }

    @ParameterizedTest @ValueSource(strings = {
        "/api/admin/accounts", "/api/admin;x=1/accounts", "/api;v=1/admin/accounts",
        "/api/manager/products", "/api/manager;x=1/products", "/api;v=1/manager/products"
    })
    void anonymousCannotReachInternalHandlers(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/admin/accounts", "/api/admin;x=1/accounts"})
    void cannotCreateInternalAccountWithoutAuthentication(String path) throws Exception {
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
    }

    @Test void verifiesBothTokenAndCurrentDatabaseRole() throws Exception {
        mvc.perform(get("/api/admin;x=1/accounts").header("Authorization", "Bearer CUSTOMER"))
            .andExpect(status().isForbidden());
        user.setRole("MANAGER");
        mvc.perform(get("/api/admin/accounts").header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isForbidden());
        user.setLocked(true);
        mvc.perform(get("/api/manager;x=1/products").header("Authorization", "Bearer MANAGER"))
            .andExpect(status().isForbidden());
        user.setLocked(false); user.setDeletedAt(Instant.now());
        mvc.perform(get("/api/manager/products").header("Authorization", "Bearer MANAGER"))
            .andExpect(status().isForbidden());
    }

    @Test void validRoleHasAuditActorAndContextPathDoesNotBypassGuard() throws Exception {
        mvc.perform(get("/crm/api/admin;x=1/accounts").contextPath("/crm")
            .header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isOk()).andExpect(content().string("ADMIN"))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                result.getRequest().getAttribute(AuditTrail.ACTOR_ATTRIBUTE)));
        mvc.perform(get("/crm/api/admin/accounts").contextPath("/crm"))
            .andExpect(status().isUnauthorized());
    }

    @Test void authAndStorefrontStayPublic() throws Exception {
        mvc.perform(post("/api/admin/auth/login")).andExpect(status().isOk());
        mvc.perform(post("/api/manager/auth/refresh")).andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        verifyNoInteractions(jwt, users);
    }

    @Test void missingDatabaseAccountIsUnauthorizedAndAuditActorClearsOnControllerError() throws Exception {
        when(users.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/admin/accounts").header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isUnauthorized());
        when(users.findById(1L)).thenReturn(Optional.of(user));
        mvc.perform(get("/api/admin/accounts/fail").header("Authorization", "Bearer ADMIN"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                result.getRequest().getAttribute(AuditTrail.ACTOR_ATTRIBUTE)));
    }

    @RestController static class InternalEndpoints {
        @RequestMapping(value = "/api/admin/accounts", method = {RequestMethod.GET, RequestMethod.POST})
        String accounts() { return actor(); }
        @GetMapping("/api/manager/products") String products() { return actor(); }
        @GetMapping("/api/admin/accounts/fail") String failure() {
            throw new com.htttdn.crm.exception.ApiException(
                org.springframework.http.HttpStatus.CONFLICT, "TEST_CONFLICT", "Business failure");
        }
        @PostMapping({"/api/admin/auth/login", "/api/manager/auth/refresh"}) String auth() { return "public"; }
        @GetMapping("/api/products") String catalog() { return "public"; }
        private String actor() { return AuditTrail.actor() == null ? "UNGUARDED" : AuditTrail.actor().role(); }
    }
}
