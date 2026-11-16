package com.htttdn.crm.service;

import com.htttdn.crm.entity.InternalNotice;
import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.InternalNoticeRepository;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:internalnotices;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false","app.notices.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class InternalNoticeIntegrationTest {
    @Autowired InternalNoticeService notices;
    @Autowired StockService stock;
    @Autowired OperationsNoticeScheduler scheduler;
    @Autowired InternalNoticeRepository repo;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;

    User user(String role, boolean locked) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@notices.test");
        user.setFullName(role);
        user.setPhone("0900000000");
        user.setRole(role);
        user.setLocked(locked);
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    Product product(String name, int current, Integer threshold) {
        var category = new com.htttdn.crm.entity.Category();
        category.setName("Áo");
        em.persist(category);
        var product = new Product();
        product.setName(name);
        product.setCategoryEntity(category);
        product.setPrice(new BigDecimal("100000"));
        product.setStock(current);
        product.setLowStockThreshold(threshold);
        em.persist(product);
        return product;
    }

    @Test void notifiesEveryActiveManagerOncePerDedupeKey() {
        var first = user("MANAGER", false);
        var second = user("MANAGER", false);
        var locked = user("MANAGER", true);
        var customer = user("CUSTOMER", false);

        assertEquals(2, notices.notifyManagers("LOW_STOCK", "Sắp hết", "Nội dung", "/manager/inventory", "LOW_STOCK:7:2026-11-16"));
        assertEquals(0, notices.notifyManagers("LOW_STOCK", "Sắp hết", "Nội dung", "/manager/inventory", "LOW_STOCK:7:2026-11-16"));

        assertEquals(1, repo.countByRecipientId(first.getId()));
        assertEquals(1, repo.countByRecipientId(second.getId()));
        assertEquals(0, repo.countByRecipientId(locked.getId()));
        assertEquals(0, repo.countByRecipientId(customer.getId()));
    }

    @Test void stockCrossingTheThresholdRaisesOneNoticePerManagerPerDay() {
        var manager = user("MANAGER", false);
        var product = product("Áo sơ mi", 6, null);

        stock.apply(product, -1, "SALE", "ORDER", 9L, "Bán đơn #9009");
        assertEquals(1, repo.countByRecipientId(manager.getId()));

        stock.apply(product, -1, "SALE", "ORDER", 9L, "Bán đơn #9009");
        assertEquals(1, repo.countByRecipientId(manager.getId()));

        var alreadyLow = product("Quần jean", 2, 0);
        stock.apply(alreadyLow, -1, "SALE", "ORDER", 9L, "Bán đơn #9009");
        assertEquals(1, repo.countByRecipientId(manager.getId()));
    }

    @Test void staleOrdersNotifyOnlyWhenTheSchedulerIsEnabled() {
        var manager = user("MANAGER", false);
        var customer = user("CUSTOMER", false);
        var stale = new Order();
        stale.setCustomer(customer);
        stale.setOrderCode(System.nanoTime());
        stale.setTotalAmount(new BigDecimal("100000"));
        stale.setStatus("PENDING");
        stale.setCreatedAt(Instant.now().minus(25, ChronoUnit.HOURS));
        em.persist(stale);
        em.flush();

        scheduler.scheduledCheck();
        assertEquals(0, repo.countByRecipientId(manager.getId()));

        ReflectionTestUtils.setField(scheduler, "enabled", true);
        scheduler.scheduledCheck();
        assertEquals(1, repo.countByRecipientId(manager.getId()));

        scheduler.scheduledCheck();
        assertEquals(1, repo.countByRecipientId(manager.getId()));
        InternalNotice notice = repo.findAll().get(0);
        assertEquals("STALE_ORDERS", notice.getNoticeType());
        assertEquals("/manager/orders", notice.getLink());
    }

    @Test void managersOnlySeeAndReadTheirOwnNoticesThroughTheApi() throws Exception {
        var first = user("MANAGER", false);
        var second = user("MANAGER", false);
        var customer = user("CUSTOMER", false);
        notices.notifyManagers("LOW_STOCK", "Sắp hết", "Áo sơ mi còn 3", "/manager/inventory", "LOW_STOCK:7:2026-11-16");
        String firstToken = "Bearer " + jwt.issue(first.getId(), "MANAGER");
        String secondToken = "Bearer " + jwt.issue(second.getId(), "MANAGER");

        mvc.perform(get("/api/manager/notices").header("Authorization", firstToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.content.length()").value(1))
            .andExpect(jsonPath("$.page.content[0].type").value("LOW_STOCK"))
            .andExpect(jsonPath("$.unread").value(1));
        mvc.perform(get("/api/manager/notices").header("Authorization", secondToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.content.length()").value(1));

        Long firstNoticeId = repo.findAll().stream()
            .filter(notice -> notice.getRecipient().getId().equals(first.getId()))
            .findFirst().orElseThrow().getId();
        mvc.perform(patch("/api/manager/notices/" + firstNoticeId + "/read").header("Authorization", secondToken))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/manager/notices/" + firstNoticeId + "/read").header("Authorization", firstToken))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/manager/notices/summary").header("Authorization", firstToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));

        mvc.perform(patch("/api/manager/notices/read-all").header("Authorization", secondToken))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/manager/notices/summary").header("Authorization", secondToken))
            .andExpect(jsonPath("$.unreadCount").value(0));

        mvc.perform(get("/api/manager/notices").header("Authorization", "Bearer " + jwt.issue(customer.getId(), "CUSTOMER")))
            .andExpect(status().isForbidden());
    }
}
