package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:archiveProducts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop", "crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ProductArchiveIntegrationTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;

    User account(String role) {
        var user=new User();user.setFullName("Archive test");user.setEmail(UUID.randomUUID()+"@example.test");
        user.setRole(role);user.setPasswordHash("test-only-hash");em.persist(user);em.flush();return user;
    }
    Product product() {
        var category=new Category();category.setName("Archive category");em.persist(category);
        var product=new Product();product.setName("Archive regression product");product.setCategoryEntity(category);
        product.setPrice(BigDecimal.valueOf(100000));product.setStock(8);em.persist(product);em.flush();return product;
    }
    @Test void archivesWithNoContentAndRemovesFromPublicCatalogWithoutDeletingOrChangingStock() throws Exception {
        var manager=account("MANAGER");var product=product();String bearer="Bearer "+jwt.issue(manager.getId(),"MANAGER");
        mvc.perform(get("/api/products/"+product.getId())).andExpect(status().isOk());
        for(int attempt=0;attempt<2;attempt++)mvc.perform(delete("/api/manager/products/"+product.getId()).header("Authorization",bearer))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        em.flush();em.clear();var hidden=em.find(Product.class,product.getId());assertNotNull(hidden);assertFalse(hidden.isActive());assertEquals(8,hidden.getStock());
        mvc.perform(get("/api/products/"+hidden.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("keyword","Archive regression product")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void rejectsUnauthenticatedAndWrongRoleAndReportsMissingProduct() throws Exception {
        var product=product();var customer=account("CUSTOMER");var manager=account("MANAGER");
        mvc.perform(delete("/api/manager/products/"+product.getId())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/manager/products/"+product.getId()).header("Authorization","Bearer "+jwt.issue(customer.getId(),"CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/manager/products/9223372036854775807").header("Authorization","Bearer "+jwt.issue(manager.getId(),"MANAGER"))).andExpect(status().isNotFound());
        assertTrue(em.find(Product.class,product.getId()).isActive());
    }
}
