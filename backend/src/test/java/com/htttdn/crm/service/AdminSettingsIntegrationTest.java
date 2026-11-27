package com.htttdn.crm.service;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:adminsettings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc @Transactional
class AdminSettingsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired EntityManager em;

    User user(String role) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@settings.test");
        user.setFullName(role);
        user.setPhone("0900000000");
        user.setRole(role);
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    @Test void adminReadsAndUpdatesSettings() throws Exception {
        var admin = jwt.issue(user("ADMIN").getId(), "ADMIN");
        mvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$['invoice.seller.name']").value("Anh Lon Shop"));
        mvc.perform(put("/api/admin/settings").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"inventory.lowStockDefault\":\"150\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['inventory.lowStockDefault']").value("150"));
        mvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$['inventory.lowStockDefault']").value("150"));
        mvc.perform(put("/api/admin/settings").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"unknown.key\":\"1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_SETTING"));
    }

    @Test void onlyAdminsCanManageSettings() throws Exception {
        for (String role : new String[]{"MANAGER", "CUSTOMER"})
            mvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + jwt.issue(user(role).getId(), role)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/settings")).andExpect(status().isUnauthorized());
    }
}
