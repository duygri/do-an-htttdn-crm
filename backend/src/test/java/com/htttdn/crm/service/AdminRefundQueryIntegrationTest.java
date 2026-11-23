package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Admin oversight of the refund ledger. Harness mirrors {@link RefundLedgerIntegrationTest}: MockMvc runs inside the test
 * transaction, so every HTTP call is executed in its own committed transaction before the response is asserted.
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:adminRefundQueryTests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_MODE=0;DEFAULT_LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.hikari.auto-commit=false","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc @Transactional
class AdminRefundQueryIntegrationTest {
    @Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired JwtTokenService jwt;
    @Autowired ObjectMapper json; @Autowired PlatformTransactionManager tx;
    User admin, manager, customer; Order order;

    @BeforeEach void setup(){
        admin=user("Quản trị","ADMIN"); manager=user("Quản lý","MANAGER"); customer=user("Khách Hàng","CUSTOMER");
        order=new Order(); order.setOrderCode(System.nanoTime()); order.setCustomer(customer); order.setStatus("COMPLETED");
        order.setPaymentMethod("PAYOS"); order.setPaymentStatus("PAID"); order.setTotalAmount(new BigDecimal("300000"));
        em.persist(order); em.flush();
    }
    User user(String name,String role){User u=new User();u.setFullName(name);u.setEmail(name+System.nanoTime()+"@test.local");u.setPasswordHash("hash");u.setRole(role);em.persist(u);return u;}
    String token(User u){return "Bearer "+jwt.issue(u.getId(),u.getRole());}
    /** Seeds a ledger row directly so the query is tested independently of the manager workflow. */
    OrderRefund seed(String status,String amount){
        OrderRefund r=new OrderRefund(); r.setOrderId(order.getId()); r.setReasonType("RETURN"); r.setAmount(new BigDecimal(amount));
        r.setStatus(status); r.setMethod("BANK_TRANSFER"); r.setRequestKey("K-"+System.nanoTime());
        if("COMPLETED".equals(status)) r.setProcessedAt(Instant.now());
        em.persist(r); em.flush();
        return r;
    }
    MvcResult web(MockHttpServletRequestBuilder request)throws Exception{
        MvcResult result=new TransactionTemplate(tx).execute(status->{
            try{return mvc.perform(request).andReturn();}
            catch(Exception e){throw new IllegalStateException(e);}
        });
        em.clear();
        return result;
    }
    JsonNode expect(MockHttpServletRequestBuilder request,int status)throws Exception{
        var response=web(request).getResponse();
        String text=response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(status,response.getStatus(),text);
        return json.readTree(text.isEmpty()?"null":text);
    }
    String iso(Instant value){return value.toString();}

    @Test void adminListsAndSummarizesRefundsOnly() throws Exception {
        seed("COMPLETED","100000"); seed("PENDING","50000");
        var list=expect(get("/api/admin/refunds?status=COMPLETED").header("Authorization",token(admin)),200);
        assertEquals(1,list.at("/content").size(),list.toString());
        assertEquals(String.valueOf(order.getOrderCode()),list.at("/content/0/orderCode").asText());
        assertEquals("Khách Hàng",list.at("/content/0/customerName").asText());
        assertEquals(0,list.at("/content/0/amount").decimalValue().compareTo(new BigDecimal("100000")));
        assertEquals("RETURN",list.at("/content/0/reasonType").asText());
        assertEquals("BANK_TRANSFER",list.at("/content/0/method").asText());
        assertFalse(list.at("/content/0/requestedAt").isNull());
        assertFalse(list.at("/content/0/processedAt").isNull());
        assertFalse(list.at("/content/0").has("bankReference"));
        assertEquals(1,list.at("/totalElements").asLong());
        var summary=expect(get("/api/admin/refunds/summary").header("Authorization",token(admin)),200);
        assertEquals(1,summary.at("/pendingCount").asLong());
        assertEquals(0,summary.at("/pendingAmount").decimalValue().compareTo(new BigDecimal("50000")));
        assertEquals(1,summary.at("/completedCount").asLong());
        assertEquals(0,summary.at("/completedAmount").decimalValue().compareTo(new BigDecimal("100000")));
        assertEquals(0,summary.at("/rejectedCount").asLong());
        assertEquals(5,summary.size());
        assertEquals(403,web(get("/api/admin/refunds").header("Authorization",token(manager))).getResponse().getStatus());
        assertEquals(403,web(get("/api/admin/refunds/summary").header("Authorization",token(manager))).getResponse().getStatus());
        assertEquals(403,web(get("/api/admin/refunds").header("Authorization",token(customer))).getResponse().getStatus());
        assertEquals(401,web(get("/api/admin/refunds")).getResponse().getStatus());
    }

    @Test void filtersDateRangeSearchAndPaginateNewestFirst() throws Exception {
        OrderRefund first=seed("COMPLETED","10000"); OrderRefund second=seed("PENDING","20000"); OrderRefund third=seed("REJECTED","30000");
        var all=expect(get("/api/admin/refunds?page=0&size=2").header("Authorization",token(admin)),200);
        assertEquals(3,all.at("/totalElements").asLong());
        assertEquals(2,all.at("/totalPages").asInt());
        assertEquals(0,all.at("/number").asInt());
        assertEquals(2,all.at("/size").asInt());
        assertEquals(2,all.at("/content").size());
        assertEquals(third.getId(),all.at("/content/0/id").asLong());
        assertEquals(second.getId(),all.at("/content/1/id").asLong());
        var secondPage=expect(get("/api/admin/refunds?page=1&size=2").header("Authorization",token(admin)),200);
        assertEquals(1,secondPage.at("/content").size());
        assertEquals(first.getId(),secondPage.at("/content/0/id").asLong());

        var byCode=expect(get("/api/admin/refunds").param("q",String.valueOf(order.getOrderCode())).header("Authorization",token(admin)),200);
        assertEquals(3,byCode.at("/totalElements").asLong());
        var byName=expect(get("/api/admin/refunds").param("q","khách hàng").header("Authorization",token(admin)),200);
        assertEquals(3,byName.at("/totalElements").asLong());
        var noMatch=expect(get("/api/admin/refunds").param("q","không-có-ai").header("Authorization",token(admin)),200);
        assertEquals(0,noMatch.at("/totalElements").asLong());
        assertEquals(0,noMatch.at("/content").size());
        var wildcard=expect(get("/api/admin/refunds").param("q","%").header("Authorization",token(admin)),200);
        assertEquals(0,wildcard.at("/totalElements").asLong());

        var future=Instant.now().plusSeconds(3600);
        var past=Instant.now().minusSeconds(3600);
        assertEquals(0,expect(get("/api/admin/refunds").param("from",iso(future)).header("Authorization",token(admin)),200).at("/totalElements").asLong());
        assertEquals(3,expect(get("/api/admin/refunds").param("from",iso(past)).param("to",iso(future)).header("Authorization",token(admin)),200).at("/totalElements").asLong());
        assertEquals(3,expect(get("/api/admin/refunds").param("to",iso(future)).param("from",iso(past)).header("Authorization",token(admin)),200).at("/totalElements").asLong());
    }

    @Test void invalidFiltersAreRejectedWithFourHundred() throws Exception {
        seed("PENDING","10000");
        for(String path:List.of("/api/admin/refunds?status=UNKNOWN","/api/admin/refunds?page=-1","/api/admin/refunds?size=0",
            "/api/admin/refunds?size=101")){
            var body=expect(get(path).header("Authorization",token(admin)),400);
            assertEquals("INVALID_REFUND_FILTER",body.at("/code").asText(),path);
        }
        // A non-numeric page never reaches the query: the argument binder answers 400 first.
        assertEquals(400,web(get("/api/admin/refunds?page=abc").header("Authorization",token(admin))).getResponse().getStatus());
        var longQuery="x".repeat(121);
        assertEquals("INVALID_REFUND_FILTER",expect(get("/api/admin/refunds").param("q",longQuery).header("Authorization",token(admin)),400).at("/code").asText());
        var later=Instant.now().plusSeconds(60);
        var inverted=expect(get("/api/admin/refunds").param("from",iso(later)).param("to",iso(Instant.now())).header("Authorization",token(admin)),400);
        assertEquals("INVALID_REFUND_FILTER",inverted.at("/code").asText());
        // `to` is exclusive, so an empty window (from == to) is rejected as invalid rather than silently returning nothing.
        var instant=Instant.now().minusSeconds(60);
        var emptyWindow=expect(get("/api/admin/refunds").param("from",iso(instant)).param("to",iso(instant)).header("Authorization",token(admin)),400);
        assertEquals("INVALID_REFUND_FILTER",emptyWindow.at("/code").asText());
    }
}
