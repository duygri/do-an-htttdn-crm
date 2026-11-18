package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.Payment;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.repository.PaymentAttemptRepository;
import com.htttdn.crm.repository.PaymentRepository;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.security.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:payments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc
class PaymentAndCancellationIntegrationTest {
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired AdminOrderService adminOrders;
    @Autowired AbandonedOrderWorker worker;
    @Autowired OrderService customerOrders;
    @Autowired JwtTokenService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean PayosService payos;

    private User customer;
    private String managerToken;
    private TransactionTemplate tx;

    @BeforeEach void setUp() {
        tx = new TransactionTemplate(transactionManager);
        customer = user("CUSTOMER");
        managerToken = "Bearer " + jwt.issue(user("MANAGER").getId(), "MANAGER");
    }

    private User user(String role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@payments.test");
        user.setFullName(role);
        user.setRole(role);
        user.setPasswordHash("test-only");
        return users.save(user);
    }

    private Order order(String method, String status, String paymentStatus) {
        return tx.execute(s -> {
            Order order = new Order();
            order.setCustomer(customer);
            order.setOrderCode(Math.abs(UUID.randomUUID().getMostSignificantBits() % 900000000000L));
            order.setTotalAmount(new BigDecimal("100000"));
            order.setPaymentMethod(method);
            order.setStatus(status);
            order.setPaymentStatus(paymentStatus);
            order.setExpiresAt(Instant.now().minus(Duration.ofHours(25)));
            orders.saveAndFlush(order);
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(paymentStatus);
            payment.setPaymentLinkId("link-" + order.getId());
            payments.save(payment);
            return order;
        });
    }

    @Test void managerRefundRoutesAreNoLongerAvailable() throws Exception {
        Order order = order("PAYOS", "CANCELLED", "PAID");
        mvc.perform(get("/api/manager/refunds").header("Authorization", managerToken))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/manager/orders/{id}/refunds", order.getId()).header("Authorization", managerToken))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/manager/orders/{id}/refunds", order.getId()).header("Authorization", managerToken)
                .contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/manager/refunds/1/complete").header("Authorization", managerToken)
                .contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test void cancellingPaidOrderDoesNotCreateARefundWorkflowOrExposeRefundData() throws Exception {
        Order order = order("PAYOS", "PENDING", "PAID");
        adminOrders.updateStatus(order.getId(), new com.htttdn.crm.dto.admin.AdminDtos.StatusRequest("CANCELLED", "Khách yêu cầu hủy"));

        assertEquals("PAID", orders.findById(order.getId()).orElseThrow().getPaymentStatus());
        String response = mvc.perform(get("/api/orders/{id}", order.getId())
                .header("Authorization", "Bearer " + jwt.issue(customer.getId(), "CUSTOMER")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(mapper.readTree(response).has("refund"));
        assertFalse(response.toLowerCase().contains("refund"));
    }

    @Test void codStillRequiresManagerConfirmationAndPreservesTheCollectionMilestone() throws Exception {
        Order order = order("COD", "DELIVERED", "COD");
        String url = "/api/manager/orders/" + order.getId() + "/cod-collection";
        String response = mvc.perform(patch(url).header("Authorization", managerToken)
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("note", "Đã đối soát"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finance.codCollectedAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        var collectedAt = orders.findById(order.getId()).orElseThrow().codCollectedAt;

        mvc.perform(patch(url).header("Authorization", managerToken)
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("note", "Gửi lại"))))
            .andExpect(status().isOk());

        assertEquals(collectedAt, orders.findById(order.getId()).orElseThrow().codCollectedAt);
        assertEquals("PAID", payments.findByOrderId(order.getId()).orElseThrow().getStatus());
        assertFalse(mapper.readTree(response).path("finance").has("refund"));
    }

    @Test void providerFailureNeverReleasesResourcesOrCancelsTheOrder() {
        Order order = order("PAYOS", "PENDING", "EXPIRED");
        org.mockito.Mockito.when(payos.getPayment("link-" + order.getId())).thenThrow(
            new com.htttdn.crm.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Test"));
        assertEquals("RETRY", worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)));
        Order current = orders.findById(order.getId()).orElseThrow();
        assertFalse(current.isStockReleased());
        assertEquals("PENDING", current.getStatus());
        assertTrue(current.paymentReviewRequired);
    }

    @Test void everyKnownPaymentLinkIsCheckedBeforeAutoCancellation() {
        Order order = order("PAYOS", "PENDING", "EXPIRED");
        String latest = "link-" + order.getId(), old = "old-" + order.getId();
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 2; i++) {
                var attempt = new com.htttdn.crm.entity.PaymentAttempt();
                attempt.setOrder(orders.findById(order.getId()).orElseThrow());
                attempt.setProviderOrderCode(order.getOrderCode() + i);
                attempt.setPaymentLinkId(i == 0 ? old : latest);
                attempt.setExpiresAt(Instant.now().minus(Duration.ofHours(25)));
                attempts.save(attempt);
            }
        });
        org.mockito.Mockito.when(payos.getPayment(old)).thenReturn(new PayosService.ProviderPayment(order.getOrderCode(), 100000, 100000, "PAID", old));
        org.mockito.Mockito.when(payos.getPayment(latest)).thenReturn(new PayosService.ProviderPayment(order.getOrderCode() + 1, 100000, 0, "EXPIRED", latest));
        assertEquals("PAID", worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)));
        assertEquals("PENDING", orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals("PAID", payments.findByOrderId(order.getId()).orElseThrow().getStatus());
        assertFalse(orders.findById(order.getId()).orElseThrow().isStockReleased());
    }

    @Test void cancellationIsConfirmedByPayOSBeforeResourcesAreReleased() {
        Order order = order("PAYOS", "PENDING", "EXPIRED");
        String link = "link-" + order.getId();
        org.mockito.Mockito.when(payos.getPayment(link)).thenReturn(
            new PayosService.ProviderPayment(order.getOrderCode(), 100000, 0, "PENDING", link),
            new PayosService.ProviderPayment(order.getOrderCode(), 100000, 0, "CANCELLED", link));
        assertEquals("CANCELLED", worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)));
        assertEquals("SKIPPED", worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)));
        org.mockito.Mockito.verify(payos, org.mockito.Mockito.times(1)).cancelPaymentLink(link);
        assertTrue(orders.findById(order.getId()).orElseThrow().isStockReleased());
    }

    @Test void newerPaymentLinkAndConfirmedOrdersCannotBeAutoCancelled() {
        Order pending = order("PAYOS", "PENDING", "EXPIRED");
        tx.executeWithoutResult(s -> {
            var attempt = new com.htttdn.crm.entity.PaymentAttempt();
            attempt.setOrder(orders.findById(pending.getId()).orElseThrow());
            attempt.setProviderOrderCode(pending.getOrderCode());
            attempt.setPaymentLinkId("new-link");
            attempt.setExpiresAt(Instant.now().plusSeconds(900));
            attempts.save(attempt);
        });
        assertEquals("SKIPPED", worker.cancel(pending.getId(), Instant.now(), Duration.ofHours(24)));
        Order confirmed = order("PAYOS", "CONFIRMED", "EXPIRED");
        assertEquals("SKIPPED", worker.cancel(confirmed.getId(), Instant.now(), Duration.ofHours(24)));
        org.mockito.Mockito.verifyNoInteractions(payos);
    }

    @Test void schedulerHonorsTheExactGraceBoundary() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Order before = order("PAYOS", "PENDING", "EXPIRED"), due = order("PAYOS", "PENDING", "EXPIRED");
        tx.executeWithoutResult(s -> {
            orders.findById(before.getId()).orElseThrow().setExpiresAt(now.minus(Duration.ofHours(24)).plusSeconds(1));
            orders.findById(due.getId()).orElseThrow().setExpiresAt(now.minus(Duration.ofHours(24)));
        });
        assertEquals("SKIPPED", worker.cancel(before.getId(), now, Duration.ofHours(24)));
        String link = "link-" + due.getId();
        org.mockito.Mockito.when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(due.getOrderCode(), 100000, 0, "EXPIRED", link));
        assertEquals("CANCELLED", worker.cancel(due.getId(), now, Duration.ofHours(24)));
    }

    @Test void contradictoryProviderStatusCannotOverwriteKnownPaidAttempt() {
        Order order = order("PAYOS", "PENDING", "EXPIRED");
        String link = "link-" + order.getId();
        tx.executeWithoutResult(s -> {
            var attempt = new com.htttdn.crm.entity.PaymentAttempt();
            attempt.setOrder(orders.findById(order.getId()).orElseThrow());
            attempt.setProviderOrderCode(order.getOrderCode());
            attempt.setPaymentLinkId(link);
            attempt.setStatus("PAID");
            attempts.save(attempt);
        });
        org.mockito.Mockito.when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(order.getOrderCode(), 100000, 0, "CANCELLED", link));
        assertEquals("NEEDS_REVIEW", worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)));
        assertEquals("PAID", attempts.findFirstByOrderIdOrderByIdDesc(order.getId()).orElseThrow().getStatus());
        assertFalse(orders.findById(order.getId()).orElseThrow().isStockReleased());
    }

    @Test void parallelSchedulersCancelAnOrderOnlyOnce() throws Exception {
        Order order = order("PAYOS", "PENDING", "EXPIRED");
        String link = "link-" + order.getId();
        org.mockito.Mockito.when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(order.getOrderCode(), 100000, 0, "EXPIRED", link));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)); });
            var second = pool.submit(() -> { start.await(); return worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)); });
            start.countDown();
            assertEquals(Set.of("CANCELLED", "SKIPPED"), Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        }
        org.mockito.Mockito.verify(payos, org.mockito.Mockito.times(1)).getPayment(link);
    }

    @Test void unknownOrPartialPaymentsAreFlaggedInsteadOfAutoCancelled() {
        for (String state : List.of("PARTIAL", "UNKNOWN", "MISSING")) {
            Order order = order("PAYOS", "PENDING", "EXPIRED");
            String link = "link-" + order.getId();
            if (state.equals("MISSING")) tx.executeWithoutResult(s -> payments.findByOrderId(order.getId()).orElseThrow().setPaymentLinkId(null));
            else org.mockito.Mockito.when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(order.getOrderCode(), 100000, state.equals("PARTIAL") ? 1 : 0, state, link));
            assertEquals("NEEDS_REVIEW", worker.cancel(order.getId(), Instant.now(), Duration.ofHours(24)));
            Order current = orders.findById(order.getId()).orElseThrow();
            assertEquals("PENDING", current.getStatus());
            assertTrue(current.paymentReviewRequired);
            assertFalse(current.isStockReleased());
        }
    }

    @Test void latePaymentAfterCancellationUpdatesPaymentWithoutStartingARefundWorkflow() throws Exception {
        Order order = order("PAYOS", "CANCELLED", "CANCELLED");
        String link = "link-" + order.getId();
        tx.executeWithoutResult(s -> {
            var attempt = new com.htttdn.crm.entity.PaymentAttempt();
            attempt.setOrder(orders.findById(order.getId()).orElseThrow());
            attempt.setProviderOrderCode(order.getOrderCode());
            attempt.setPaymentLinkId(link);
            attempts.save(attempt);
        });
        org.mockito.Mockito.when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(order.getOrderCode(), 100000, 100000, "PAID", link));
        customerOrders.syncPayment(customer, order.getId());
        customerOrders.syncPayment(customer, order.getId());
        assertEquals("PAID", orders.findById(order.getId()).orElseThrow().getPaymentStatus());
        assertFalse(mapper.readTree(mvc.perform(get("/api/orders/{id}", order.getId())
            .header("Authorization", "Bearer " + jwt.issue(customer.getId(), "CUSTOMER")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).has("refund"));
    }
}
