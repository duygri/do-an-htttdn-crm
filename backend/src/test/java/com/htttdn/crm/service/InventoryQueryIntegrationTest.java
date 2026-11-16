package com.htttdn.crm.service;

import com.htttdn.crm.entity.Category;
import com.htttdn.crm.entity.Product;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventoryquery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc @Transactional
class InventoryQueryIntegrationTest {
    @Autowired InventoryQueryService inventory;
    @Autowired StockService stock;
    @Autowired SettingsService settings;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;

    User user(String role) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@inventory.test");
        user.setFullName(role);
        user.setPhone("0900000000");
        user.setRole(role);
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    Product product(int quantity, Integer threshold) {
        var category = new Category();
        category.setName("Áo");
        em.persist(category);
        var product = new Product();
        product.setCategoryEntity(category);
        product.setName("Áo kho");
        product.setPrice(BigDecimal.TEN);
        product.setStock(quantity);
        product.setLowStockThreshold(threshold);
        em.persist(product);
        em.flush();
        return product;
    }

    @Test void ledgerIsFilteredAndNewestFirst() {
        var product = product(10, null);
        stock.adjust(product, 7, "Giảm tồn");
        stock.adjust(product, 4, "Giảm tiếp");
        var page = inventory.movements(product.getId(), "ADJUST", null, null, 0, 20);
        assertEquals(2, page.getTotalElements());
        assertEquals(-3, page.getContent().getFirst().quantity());
        assertEquals(7, page.getContent().getFirst().stockBefore());
        assertEquals(4, page.getContent().getFirst().stockAfter());
        assertEquals(-3, page.getContent().get(1).quantity());
        assertEquals(10, page.getContent().get(1).stockBefore());
        assertEquals(7, page.getContent().get(1).stockAfter());
        assertEquals("Áo kho", page.getContent().getFirst().productName());
        assertThrows(com.htttdn.crm.exception.ApiException.class,
            () -> inventory.movements(null, "UNKNOWN", null, null, 0, 20));
    }

    @Test void lowStockUsesProductThresholdAndGlobalDefault() {
        var product = product(5, null);
        var page = inventory.lowStock(null, null, 0, 20);
        assertEquals(1, page.getTotalElements());
        assertEquals(5, page.getContent().getFirst().threshold());

        settings.update(Map.of("inventory.lowStockDefault", "2"), new AuditTrail.Actor(1L, "Admin", "ADMIN"));
        assertEquals(0, inventory.lowStock(null, null, 0, 20).getTotalElements());

        inventory.updateThreshold(product.getId(), 10, new AuditTrail.Actor(1L, "Admin", "ADMIN"));
        assertEquals(1, inventory.lowStock(null, null, 0, 20).getTotalElements());
        assertEquals(10, inventory.lowStock(null, null, 0, 20).getContent().getFirst().threshold());
    }

    @Test void managerUpdatesThresholdOverHttpWithValidation() throws Exception {
        var product = product(5, null);
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");
        mvc.perform(patch("/api/manager/products/" + product.getId() + "/stock-threshold")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"threshold\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lowStockThreshold").value(2));
        mvc.perform(patch("/api/manager/products/" + product.getId() + "/stock-threshold")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"threshold\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_THRESHOLD"));
        mvc.perform(get("/api/manager/inventory/low-stock").header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/manager/inventory/movements").header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/manager/inventory/low-stock")
                .header("Authorization", "Bearer " + jwt.issue(user("CUSTOMER").getId(), "CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/inventory/low-stock")).andExpect(status().isUnauthorized());
    }
}
