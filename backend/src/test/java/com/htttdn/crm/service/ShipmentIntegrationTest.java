package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:shipmentTests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc @Transactional
class ShipmentIntegrationTest {
    @Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired JwtTokenService jwt;
    @Autowired ShipmentRepository shipments; @Autowired CustomerNotificationRepository notifications; @Autowired AuditLogRepository auditLogs;
    User manager, customer, admin; Order order;
    @BeforeEach void setup(){
        manager=user("Manager","MANAGER"); customer=user("Customer","CUSTOMER"); admin=user("Admin","ADMIN");
        order=new Order(); order.setOrderCode(System.nanoTime()); order.setCustomer(customer); order.setStatus("PREPARING");
        order.setPaymentMethod("COD"); order.setPaymentStatus("COD"); order.setTotalAmount(new BigDecimal("300000")); em.persist(order); em.flush();
    }
    User user(String name,String role){User u=new User();u.setFullName(name);u.setEmail(name+System.nanoTime()+"@test.local");u.setPasswordHash("hash");u.setRole(role);em.persist(u);return u;}
    String token(User u){return "Bearer "+jwt.issue(u.getId(),u.getRole());}

    @Test void managerCreatesShipmentAndOrderShipShips() throws Exception {
        mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(manager))
                .contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"GHN123456\",\"note\":\"Giao giờ hành chính\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED")).andExpect(jsonPath("$.trackingCode").value("GHN123456"));
        Order saved=em.find(Order.class,order.getId());
        assertEquals("SHIPPED",saved.getStatus()); assertEquals("GHN123456",saved.getTrackingCode());
        assertEquals("GHN",shipments.findByOrderId(order.getId()).orElseThrow().getCarrierCode());
        assertEquals(1,notifications.count());
        var event=auditLogs.findAll().stream().filter(a->"SHIPMENT_CREATE".equals(a.getAction())).findFirst().orElseThrow();
        assertEquals("ORDER",event.getEntityType()); assertEquals(manager.getId(),event.getActorId());
        assertTrue(event.getAfterData().contains("\"trackingCode\":\"GHN123456\""));
        assertTrue(AuditLogQuery.ACTIONS.contains("SHIPMENT_CREATE")); assertTrue(AuditLogQuery.ACTIONS.contains("SHIPMENT_UPDATE"));
    }
    @Test void rejectsDuplicateShipmentWrongStatusAndUnknownCarrier() throws Exception {
        // Harness notes: em.flush() rolls back shipments/notifications written by the earlier JPQL-write transactions of
        // the same test (shared default rollback-only session), so detach with em.clear() and re-read instead. Once the
        // order has a shipment the duplicate check answers first, so duplicate/status cases run on `order` while invalid
        // input (unknown carrier, blank tracking code, over-long note) is proven on the fresh CONFIRMED order.
        mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"A1\"}")).andExpect(status().isOk());
        em.clear();
        mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"A1\"}")).andExpect(status().isConflict());
        order=em.find(Order.class,order.getId());order.setStatus("PENDING");
        mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"A2\"}")).andExpect(status().isConflict());
        order=em.find(Order.class,order.getId());order.setStatus("CONFIRMED");
        mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/manager/orders/"+999999999L+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"A3\"}")).andExpect(status().isNotFound());
        Order fresh=order(10001L,"CONFIRMED");em.flush();
        mvc.perform(post("/api/manager/orders/"+fresh.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"NOPE\",\"trackingCode\":\"A2\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/manager/orders/"+fresh.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/manager/orders/"+fresh.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"A4\",\"note\":\""+"x".repeat(1001)+"\"}")).andExpect(status().isBadRequest());
        Order unchanged=em.find(Order.class,fresh.getId());assertEquals("CONFIRMED",unchanged.getStatus());assertNull(unchanged.getTrackingCode());
    }
    Order order(long code,String status){Order o=new Order();o.setOrderCode(code);o.setCustomer(customer);o.setStatus(status);o.setPaymentMethod("COD");o.setPaymentStatus("COD");o.setTotalAmount(new BigDecimal("300000"));em.persist(o);return o;}
    @Test void customerAndAdminCannotCreateOrListCarriers() throws Exception {
        for(User u:java.util.List.of(customer,admin)) {
            mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(u)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"A1\"}")).andExpect(status().isForbidden());
            mvc.perform(get("/api/manager/shipping/carriers").header("Authorization",token(u))).andExpect(status().isForbidden());
        }
    }
    @Test void carriersAreListedForManager() throws Exception {
        mvc.perform(get("/api/manager/shipping/carriers").header("Authorization",token(manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("GHN")).andExpect(jsonPath("$[0].name").value("Giao Hàng Nhanh"));
    }
    @Test void eventsBuildTimelineAndDeliveredMovesOrder() throws Exception {
        mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(manager))
            .contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"GHN9\"}")).andExpect(status().isOk());
        Long sid=shipments.findByOrderId(order.getId()).orElseThrow().getId();
        String base="/api/manager/orders/"+order.getId()+"/shipments/"+sid+"/events";
        mvc.perform(post(base).header("Authorization",token(manager)).contentType("application/json").content("{\"status\":\"IN_TRANSIT\",\"note\":\"Đến kho phân loại\"}")).andExpect(status().isOk());
        mvc.perform(post(base).header("Authorization",token(manager)).contentType("application/json").content("{\"status\":\"DELIVERED\",\"note\":\"Khách đã ký nhận\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"))
            .andExpect(jsonPath("$.shipment.events.length()").value(3))
            .andExpect(jsonPath("$.shipment.events[2].status").value("DELIVERED"))
            .andExpect(jsonPath("$.shipment.carrierName").value("Giao Hàng Nhanh"));
        Order saved=em.find(Order.class,order.getId());
        assertEquals("DELIVERED",saved.getStatus());
        mvc.perform(post(base).header("Authorization",token(manager)).contentType("application/json").content("{\"status\":\"OUT_FOR_DELIVERY\",\"note\":\"x\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/orders/"+order.getId()).header("Authorization",token(customer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.shipment.events[2].status").value("DELIVERED"))
            .andExpect(jsonPath("$.shipment.events[0].createdBy").doesNotExist());
    }
    @Test void failedEventRequiresNoteAndCancelledOrderRejectsEvents() throws Exception {
        mvc.perform(post("/api/manager/orders/"+order.getId()+"/shipments").header("Authorization",token(manager)).contentType("application/json").content("{\"carrierCode\":\"GHN\",\"trackingCode\":\"GHN10\"}")).andExpect(status().isOk());
        Long sid=shipments.findByOrderId(order.getId()).orElseThrow().getId();
        String base="/api/manager/orders/"+order.getId()+"/shipments/"+sid+"/events";
        mvc.perform(post(base).header("Authorization",token(manager)).contentType("application/json").content("{\"status\":\"FAILED\",\"note\":\" \"}")).andExpect(status().isBadRequest());
        Order owned=em.find(Order.class,order.getId());owned.setStatus("CANCELLED");em.flush();
        mvc.perform(post(base).header("Authorization",token(manager)).contentType("application/json").content("{\"status\":\"FAILED\",\"note\":\"Khách không nhận\"}")).andExpect(status().isConflict());
    }
}
