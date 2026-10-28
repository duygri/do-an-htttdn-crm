package com.htttdn.crm.service;

import com.htttdn.crm.controller.WebDeploymentController;
import com.htttdn.crm.config.DemoAdminBootstrap;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebDeploymentTest {
    @Test void pagesForwardButApiAndAssetsNeverDo() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new WebDeploymentController()).build();
        for (String path : new String[]{"/", "/san-pham", "/san-pham/1", "/manager/login", "/admin/accounts", "/don-hang/2", "/ho-tro/3", "/dat-lai-mat-khau"})
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/api/not-found")).andExpect(status().isNotFound());
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }
    @Test void bootstrapHashesPasswordAndNeverResetsExistingAdmin() {
        var users = mock(UserRepository.class);
        String password = "test-private-password-12345";
        var bootstrap = new DemoAdminBootstrap(users, "Owner@example.com", password);
        bootstrap.run(null);
        var saved = ArgumentCaptor.forClass(User.class); verify(users).save(saved.capture());
        assertEquals("ADMIN", saved.getValue().getRole());
        assertEquals("owner@example.com", saved.getValue().getEmail());
        assertTrue(new BCryptPasswordEncoder().matches(password, saved.getValue().getPasswordHash()));
        reset(users); when(users.countByRole("ADMIN")).thenReturn(1L);
        bootstrap.run(null); verify(users, never()).save(any());
    }
    @Test void bootstrapRejectsWeakOrExistingCredentials() {
        var users = mock(UserRepository.class);
        assertThrows(IllegalStateException.class, () -> new DemoAdminBootstrap(users,"a@example.com","short").run(null));
        when(users.existsByEmailIgnoreCase("a@example.com")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> new DemoAdminBootstrap(users,"a@example.com","test-private-password-12345").run(null));
        verify(users, never()).save(any());
    }
}
