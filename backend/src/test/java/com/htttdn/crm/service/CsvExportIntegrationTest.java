package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.Supplier;
import com.htttdn.crm.entity.SupplierReceipt;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:csvexport;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false","app.notices.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class CsvExportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired EntityManager em;

    User user(String role, String name, String email) {
        var user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPhone("0900000000");
        user.setRole(role);
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    Order order(User customer, String status, String total, Instant createdAt) {
        var order = new Order();
        order.setCustomer(customer);
        order.setOrderCode(System.nanoTime());
        order.setStatus(status);
        order.setPaymentMethod("PAYOS");
        order.setPaymentStatus("PAID".equals(status) ? "PAID" : "PENDING");
        order.setTotalAmount(new BigDecimal(total));
        order.setCreatedAt(createdAt);
        em.persist(order);
        return order;
    }

    String token(User user) {
        return "Bearer " + jwt.issue(user.getId(), user.getRole());
    }

    @Test void exportsOrdersAsCsvWithBomAndFilters() throws Exception {
        var manager = user("MANAGER", "Quản lý", "manager@csv.test");
        var customer = user("CUSTOMER", "Nguyễn An", "an@csv.test");
        order(customer, "COMPLETED", "255000.00", Instant.parse("2026-11-05T03:00:00Z"));
        order(customer, "PENDING", "100000.00", Instant.parse("2026-11-06T03:00:00Z"));
        em.flush();

        String body = mvc.perform(get("/api/manager/orders.csv").header("Authorization", token(manager)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(body.startsWith("\uFEFF"));
        assertTrue(body.contains("orderCode,customer,phone,status,paymentMethod,paymentStatus,totalAmount,createdAt"));
        assertTrue(body.contains("Nguyễn An"));
        assertEquals(3, body.strip().split("\r\n").length);

        String completed = mvc.perform(get("/api/manager/orders.csv").param("status", "COMPLETED").header("Authorization", token(manager)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(completed.contains("255000.00"));
        assertFalse(completed.contains("100000.00"));
        assertTrue(mvc.perform(get("/api/manager/orders.csv").header("Authorization", token(manager)))
            .andReturn().getResponse().getContentType().startsWith("text/csv"));
    }

    @Test void exportsCustomersWithOrderCountAndCompletedValue() throws Exception {
        var manager = user("MANAGER", "Quản lý", "manager@csv.test");
        var customer = user("CUSTOMER", "Nguyễn An", "an@csv.test");
        var other = user("CUSTOMER", "Trần Bình", "binh@csv.test");
        order(customer, "COMPLETED", "255000.00", Instant.parse("2026-11-05T03:00:00Z"));
        order(customer, "PENDING", "100000.00", Instant.parse("2026-11-06T03:00:00Z"));
        order(other, "COMPLETED", "50000.00", Instant.parse("2026-11-07T03:00:00Z"));
        em.flush();

        String body = mvc.perform(get("/api/manager/customers.csv").param("q", "nguyễn").header("Authorization", token(manager)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("fullName,email,phone,orderCount,completedValue,createdAt"));
        assertTrue(body.contains("Nguyễn An,an@csv.test,0900000000,2,255000.00"));
        assertFalse(body.contains("Trần Bình"));

        mvc.perform(get("/api/manager/customers.csv").header("Authorization", token(customer)))
            .andExpect(status().isForbidden());
    }

    @Test void exportsRevenueSeriesMatchingTheCashflowReport() throws Exception {
        var manager = user("MANAGER", "Quản lý", "manager@csv.test");
        var customer = user("CUSTOMER", "Nguyễn An", "an@csv.test");
        var paid = order(customer, "COMPLETED", "255000.00", Instant.parse("2026-11-05T03:00:00Z"));
        paid.setPaymentMethod("PAYOS");
        paid.setPaymentStatus("PAID");
        paid.paidAt = Instant.parse("2026-11-05T04:00:00Z");
        var supplier = new Supplier();
        supplier.code = "NCC-CSV";
        supplier.name = "Nhà cung cấp CSV";
        em.persist(supplier);
        var receipt = new SupplierReceipt();
        receipt.setSupplier(supplier);
        receipt.setInvoiceNumber("HD-CSV-1");
        receipt.setInvoiceDate(LocalDate.of(2026, 11, 3));
        receipt.setTotalAmount(new BigDecimal("40000.00"));
        em.persist(receipt);
        em.flush();

        String body = mvc.perform(get("/api/manager/reports/revenue.csv")
                .param("from", "2026-10-31T17:00:00Z").param("to", "2026-11-30T17:00:00Z").param("groupBy", "month")
                .header("Authorization", token(manager)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("period,collected,collectedOrders,purchaseCost,difference"));
        assertTrue(body.contains("2026-11,255000.00,1,40000.00,215000.00"));

        mvc.perform(get("/api/manager/reports/revenue.csv")
                .param("from", "2026-11-30T17:00:00Z").param("to", "2026-10-31T17:00:00Z").header("Authorization", token(manager)))
            .andExpect(status().isBadRequest());
    }
}
