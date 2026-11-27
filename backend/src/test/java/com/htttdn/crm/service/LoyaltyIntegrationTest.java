package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:loyaltyapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class LoyaltyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired EntityManager em;

    User user(String role) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@loyalty-api.test");
        user.setFullName(role);
        user.setPhone("0900000000");
        user.setRole(role);
        user.setPasswordHash("test-only");
        em.persist(user);
        return user;
    }

    Order delivered(User customer, String total) {
        var order = new Order();
        order.setCustomer(customer);
        order.setOrderCode(System.nanoTime());
        order.setTotalAmount(new BigDecimal(total));
        order.setStatus("DELIVERED");
        order.setReturnStatus("NONE");
        em.persist(order);
        em.flush();
        return order;
    }

    @Test void completionChangesMembershipWithoutAwardingPoints()throws Exception{
        var customer=user("CUSTOMER");var order=delivered(customer,"2000000");var token=jwt.issue(customer.getId(),"CUSTOMER");
        mvc.perform(patch("/api/orders/"+order.getId()+"/confirm-receipt").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        em.flush();
        mvc.perform(get("/api/account/membership").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.tier").value("SILVER"));
        org.junit.jupiter.api.Assertions.assertEquals(0L,em.createQuery("select count(p) from PointTransaction p",Long.class).getSingleResult());
        mvc.perform(get("/api/account/points").header("Authorization","Bearer "+token)).andExpect(status().isGone());
        mvc.perform(post("/api/account/points/redeem").header("Authorization","Bearer "+token)).andExpect(status().isGone());
        var manager=user("MANAGER");var managerToken=jwt.issue(manager.getId(),"MANAGER");
        mvc.perform(post("/api/manager/customers/"+customer.getId()+"/points").header("Authorization","Bearer "+managerToken).contentType(MediaType.APPLICATION_JSON).content("{\"points\":50,\"note\":\"test\"}")).andExpect(status().isGone());
        mvc.perform(get("/api/account/membership").header("Authorization","Bearer "+managerToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/membership")).andExpect(status().isUnauthorized());
    }
}
