package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:shippingSchema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@Transactional
class ShippingSchemaIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ShippingCarrierRepository carriers;
    @Autowired OrderRefundRepository refunds;
    @Autowired ShippingCarrierSeeder seeder;

    @Test void seedsSevenCarriersExactlyOnce() {
        assertEquals(7, carriers.count());
        seeder.run(null);
        assertEquals(7, carriers.count());
        assertTrue(carriers.findById("GHN").orElseThrow().isActive());
    }

    @Test void refundDefaultsMatchDatabaseContract() {
        User c = new User(); c.setFullName("Khách"); c.setEmail(UUID.randomUUID()+"@test.local"); c.setPasswordHash("hash"); c.setRole("CUSTOMER"); em.persist(c);
        Order o = new Order(); o.setOrderCode(System.nanoTime()); o.setCustomer(c); o.setTotalAmount(new BigDecimal("300000")); em.persist(o);
        OrderRefund r = new OrderRefund();
        r.setOrderId(o.getId()); r.setReasonType("RETURN"); r.setAmount(new BigDecimal("100000")); r.setRequestKey("REFUND-TEST-1");
        em.persist(r); em.flush();
        assertEquals("PENDING", r.getStatus());
        assertEquals("BANK_TRANSFER", r.getMethod());
        assertNotNull(r.getRequestedAt());
        assertEquals("NONE", o.getRefundStatus());
        assertEquals(BigDecimal.ZERO, o.getRefundedAmount());
    }
}
