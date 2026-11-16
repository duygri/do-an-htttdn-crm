package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.PointTransactionRepository;
import com.htttdn.crm.repository.StoreVoucherRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:loyaltyservice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@Transactional
class LoyaltyServiceTest {
    @Autowired LoyaltyService loyalty;
    @Autowired SettingsService settings;
    @Autowired PointTransactionRepository transactions;
    @Autowired StoreVoucherRepository vouchers;
    @Autowired EntityManager em;

    User customer() {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@loyalty.test");
        user.setFullName("Khách hàng");
        user.setPhone("0900000000");
        user.setRole("CUSTOMER");
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    User manager() {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@loyalty-manager.test");
        user.setFullName("Quản lý");
        user.setRole("MANAGER");
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    Order completedOrder(User customer, String total) {
        var order = new Order();
        order.setCustomer(customer);
        order.setOrderCode(System.nanoTime());
        order.setTotalAmount(new BigDecimal(total));
        order.setStatus("COMPLETED");
        em.persist(order);
        return order;
    }

    AuditTrail.Actor actor(User manager) {
        return new AuditTrail.Actor(manager.getId(), manager.getFullName(), manager.getRole());
    }

    @Test void awardsPointsOnceForCompletedOrders() {
        var customer = customer();
        var order = completedOrder(customer, "255000.00");
        em.flush();

        loyalty.awardForCompletedOrder(order);
        loyalty.awardForCompletedOrder(order);
        em.flush();
        assertEquals(25, loyalty.balance(customer.getId()));
        assertEquals(1, transactions.findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId()).size());

        settings.update(Map.of("loyalty.pointsPer10000", "0"), actor(manager()));
        var free = completedOrder(customer, "500000.00");
        em.flush();
        loyalty.awardForCompletedOrder(free);
        em.flush();
        assertEquals(25, loyalty.balance(customer.getId()));
    }

    @Test void redeemsPointsForAFixedAmountVoucher() {
        var customer = customer();
        loyalty.adjust(customer, 250, "Thưởng sinh nhật", actor(manager()));
        em.flush();

        var result = loyalty.redeem(customer);
        em.flush();

        assertEquals(150, result.balance());
        assertEquals(100, result.spentPoints());
        assertEquals(new BigDecimal("20000"), result.discountValue());
        assertTrue(result.code().startsWith("DIEM-"));
        var voucher = vouchers.findByCodeIgnoreCase(result.code()).orElseThrow();
        assertEquals("FIXED_AMOUNT", voucher.getDiscountType());
        assertEquals(0, new BigDecimal("20000").compareTo(voucher.getDiscountValue()));
        assertEquals(customer.getId(), voucher.getOwnerCustomerId());
        assertEquals(150, loyalty.balance(customer.getId()));

        var summary = loyalty.summary(customer);
        assertEquals(150, summary.balance());
        assertTrue(summary.history().stream().anyMatch(row -> row.type().equals("REDEEM") && row.points() == -100));
    }

    @Test void rejectsRedemptionWithoutEnoughPoints() {
        var customer = customer();
        loyalty.adjust(customer, 40, "Tích lũy", actor(manager()));
        em.flush();
        var failure = assertThrows(ApiException.class, () -> loyalty.redeem(customer));
        assertEquals("INSUFFICIENT_POINTS", failure.code());
    }

    @Test void adjustsPointsWithNotesAndValidatesInput() {
        var customer = customer();
        var manager = manager();
        var actor = actor(manager);
        var summary = loyalty.adjust(customer, 50, "Bù điểm khiếu nại", actor);
        assertEquals(50, summary.balance());
        assertEquals(1, summary.history().size());
        assertEquals("ADJUST", summary.history().get(0).type());

        assertEquals("INVALID_POINTS", assertThrows(ApiException.class,
            () -> loyalty.adjust(customer, 0, "Không đổi", actor)).code());
        assertEquals("INVALID_POINTS", assertThrows(ApiException.class,
            () -> loyalty.adjust(customer, 5, "  ", actor)).code());
        assertEquals("INVALID_POINTS", assertThrows(ApiException.class,
            () -> loyalty.adjust(customer, 5, "x".repeat(501), actor)).code());
        assertEquals("INVALID_POINTS", assertThrows(ApiException.class,
            () -> loyalty.adjust(customer, 5, "Thiếu người duyệt", null)).code());
    }
}
