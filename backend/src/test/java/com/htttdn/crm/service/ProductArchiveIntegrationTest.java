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

    @Test void restoresArchivedProductOnceWithoutChangingDetailsOrStock() throws Exception {
        var manager=account("MANAGER");var product=product();
        product.setDescription("Original description");product.setImageUrl("https://example.test/product.jpg");
        product.setSalePrice(BigDecimal.valueOf(90000));product.setSizes("M,L");product.setColors("Black,Blue");
        product.setUpdatedAt(java.time.Instant.parse("2020-01-01T00:00:00Z"));em.flush();
        String bearer="Bearer "+jwt.issue(manager.getId(),"MANAGER");
        mvc.perform(delete("/api/manager/products/"+product.getId()).header("Authorization",bearer)).andExpect(status().isNoContent());
        mvc.perform(patch("/api/manager/products/"+product.getId()+"/restore").header("Authorization",bearer))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        em.flush();em.clear();var restored=em.find(Product.class,product.getId());var restoredAt=restored.getUpdatedAt();
        assertTrue(restored.isActive());assertTrue(restoredAt.isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z")));
        assertEquals("Archive regression product",restored.getName());assertEquals("Archive category",restored.getCategory());
        assertEquals(0,BigDecimal.valueOf(100000).compareTo(restored.getPrice()));assertEquals(8,restored.getStock());
        assertEquals(0,BigDecimal.valueOf(90000).compareTo(restored.getSalePrice()));
        assertEquals("Original description",restored.getDescription());assertEquals("https://example.test/product.jpg",restored.getImageUrl());
        assertEquals("M,L",restored.getSizes());assertEquals("Black,Blue",restored.getColors());
        mvc.perform(patch("/api/manager/products/"+restored.getId()+"/restore").header("Authorization",bearer)).andExpect(status().isNoContent());
        em.flush();em.clear();assertEquals(restoredAt,em.find(Product.class,restored.getId()).getUpdatedAt());
        assertEquals(1L,em.createQuery("select count(a) from AuditLog a where a.action='RESTORE' and a.targetId=:id",Long.class).setParameter("id",restored.getId()).getSingleResult());
        mvc.perform(get("/api/products/"+restored.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/products").param("keyword","Archive regression product")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void permitsRestoringOutOfStockProductAndEnforcesPermissions() throws Exception {
        var hidden=product();hidden.setStock(0);hidden.setActive(false);em.flush();
        mvc.perform(patch("/api/manager/products/"+hidden.getId()+"/restore")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"CUSTOMER","ADMIN"}){
            var wrongRole=account(role);
            mvc.perform(patch("/api/manager/products/"+hidden.getId()+"/restore").header("Authorization","Bearer "+jwt.issue(wrongRole.getId(),role))).andExpect(status().isForbidden());
        }
        assertFalse(em.find(Product.class,hidden.getId()).isActive());var manager=account("MANAGER");String bearer="Bearer "+jwt.issue(manager.getId(),"MANAGER");
        mvc.perform(patch("/api/manager/products/9223372036854775807/restore").header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/manager/products/"+hidden.getId()+"/restore").header("Authorization",bearer)).andExpect(status().isNoContent());
        em.flush();em.clear();assertTrue(em.find(Product.class,hidden.getId()).isActive());assertEquals(0,em.find(Product.class,hidden.getId()).getStock());
        mvc.perform(get("/api/products/"+hidden.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(0));
    }

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
