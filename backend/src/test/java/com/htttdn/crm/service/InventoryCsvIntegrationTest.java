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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventorycsv;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc @Transactional
class InventoryCsvIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired StockService stock;
    @Autowired EntityManager em;

    User user(String role) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@inventory-csv.test");
        user.setFullName(role);
        user.setPhone("0900000000");
        user.setRole(role);
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    Product product() {
        var category = new Category();
        category.setName("Áo");
        em.persist(category);
        var product = new Product();
        product.setCategoryEntity(category);
        product.setName("Áo CSV");
        product.setPrice(BigDecimal.TEN);
        product.setStock(4);
        em.persist(product);
        em.flush();
        return product;
    }

    @Test void inventoryCsvCarriesBomHeaderAndRows() throws Exception {
        product();
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");
        var result = mvc.perform(get("/api/manager/reports/inventory.csv")
                .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals("text/csv", result.getResponse().getContentType().split(";")[0]);
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("productId,name,category,stock,unitCost,value"));
        assertTrue(csv.contains("Áo CSV,Áo,4,0.00,0.00"));
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("ton-kho.csv"));
    }

    @Test void movementsCsvEscapesCommasAndQuotes() throws Exception {
        var product = product();
        stock.adjust(product, 2, "A, B \"ghi chú\"");
        var manager = jwt.issue(user("MANAGER").getId(), "MANAGER");
        var result = mvc.perform(get("/api/manager/inventory/movements.csv")
                .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andReturn();
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("id,createdAt,productId,productName,type,quantity,stockBefore,stockAfter,actor,note"));
        assertTrue(csv.contains("\"A, B \"\"ghi chú\"\"\""));
        assertTrue(csv.contains("ADJUST"));
    }

    @Test void csvEndpointsRejectOtherRoles() throws Exception {
        var customer = jwt.issue(user("CUSTOMER").getId(), "CUSTOMER");
        mvc.perform(get("/api/manager/reports/inventory.csv").header("Authorization", "Bearer " + customer))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/inventory/movements.csv").header("Authorization", "Bearer " + customer))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/reports/inventory.csv")).andExpect(status().isUnauthorized());
    }
}
