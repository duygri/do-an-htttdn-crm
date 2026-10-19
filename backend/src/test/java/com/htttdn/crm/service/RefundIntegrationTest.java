package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import com.htttdn.crm.dto.admin.AdminDtos.StatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:refunds;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc
class RefundIntegrationTest {
    @Autowired UserRepository users;@Autowired OrderRepository orders;@Autowired PaymentRepository payments;@Autowired OrderRefundRepository refunds;@Autowired PaymentAttemptRepository attempts;
    @Autowired AdminOrderService adminOrders;@Autowired AbandonedOrderWorker worker;@Autowired OrderService customerOrders;@Autowired JwtTokenService jwt;@Autowired ObjectMapper mapper;@Autowired MockMvc mvc;@Autowired PlatformTransactionManager tm;
    @MockitoBean PayosService payos;
    User customer,manager;String token;TransactionTemplate tx;
    @BeforeEach void setup(){tx=new TransactionTemplate(tm);customer=user("CUSTOMER");manager=user("MANAGER");token="Bearer "+jwt.issue(manager.getId(),"MANAGER");}
    User user(String role){User u=new User();u.setEmail(UUID.randomUUID()+"@refund.test");u.setFullName(role);u.setRole(role);u.setPasswordHash("test-only");return users.save(u);}
    Order order(String method,String status,String payment){return tx.execute(s->{Order o=new Order();o.setCustomer(customer);o.setOrderCode(Math.abs(UUID.randomUUID().getMostSignificantBits()%900000000000L));o.setTotalAmount(new BigDecimal("100000"));o.setPaymentMethod(method);o.setStatus(status);o.setPaymentStatus(payment);o.setExpiresAt(Instant.now().minus(Duration.ofHours(25)));orders.saveAndFlush(o);Payment p=new Payment();p.setOrder(o);p.setAmount(o.getTotalAmount());p.setStatus(payment);p.setPaymentLinkId("link-"+o.getId());payments.save(p);return o;});}
    String create(Long id,int amount,String key,String type) throws Exception{return mvc.perform(post("/api/manager/orders/"+id+"/refunds").header("Authorization",token).contentType("application/json").content(mapper.writeValueAsString(Map.of("reasonType",type,"amount",amount,"note","Ghi nhận thủ công","requestKey",key)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
    void returns(Order o){tx.executeWithoutResult(s->orders.findById(o.getId()).orElseThrow().setReturnStatus("APPROVED"));}
    @Test void concurrentCreationUsesOneIdempotencyKeyAndOneReservation()throws Exception{
        Order o=order("PAYOS","DELIVERED","PAID");returns(o);CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return create(o.getId(),100000,"concurrent","RETURN");});
            var b=pool.submit(()->{start.await();return create(o.getId(),100000,"concurrent","RETURN");});
            start.countDown();assertEquals(mapper.readTree(a.get(20,TimeUnit.SECONDS)).path("id"),mapper.readTree(b.get(20,TimeUnit.SECONDS)).path("id"));
        }
        assertEquals(1,refunds.findByOrderIdOrderByIdDesc(o.getId()).size());assertEquals(0,new BigDecimal("100000").compareTo(orders.findById(o.getId()).orElseThrow().refundPendingAmount));
    }
    @Test void networkFailureAndChangedStatusNeverReleaseResources(){
        Order o=order("PAYOS","PENDING","EXPIRED");when(payos.getPayment("link-"+o.getId())).thenThrow(new com.htttdn.crm.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"PAYMENT_PROVIDER_ERROR","Test"));
        assertEquals("RETRY",worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24)));Order current=orders.findById(o.getId()).orElseThrow();assertFalse(current.isStockReleased());assertEquals("PENDING",current.getStatus());assertTrue(current.paymentReviewRequired);
    }
    @Test void olderPaidAttemptPreventsCancellationEvenWhenLatestExpired(){
        Order o=order("PAYOS","PENDING","EXPIRED");String latest="link-"+o.getId(),old="old-"+o.getId();
        tx.executeWithoutResult(s->{for(int i=0;i<2;i++){var a=new PaymentAttempt();a.setOrder(orders.findById(o.getId()).orElseThrow());a.setProviderOrderCode(o.getOrderCode()+i);a.setPaymentLinkId(i==0?old:latest);a.setExpiresAt(Instant.now().minus(Duration.ofHours(25)));attempts.save(a);}});
        when(payos.getPayment(old)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode(),100000,100000,"PAID",old));when(payos.getPayment(latest)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode()+1,100000,0,"EXPIRED",latest));
        assertEquals("PAID",worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24)));assertEquals("PENDING",orders.findById(o.getId()).orElseThrow().getStatus());assertEquals("PAID",payments.findByOrderId(o.getId()).orElseThrow().getStatus());assertFalse(orders.findById(o.getId()).orElseThrow().isStockReleased());
    }
    @Test void paidCancellationCreatesOneRefundAndDoesNotRecreateAfterRejection()throws Exception{
        Order o=order("PAYOS","PENDING","PAID");adminOrders.updateStatus(o.getId(),new StatusRequest("CANCELLED","Test"));adminOrders.updateStatus(o.getId(),new StatusRequest("CANCELLED","Test"));
        var rows=refunds.findByOrderIdOrderByIdDesc(o.getId());assertEquals(1,rows.size());assertEquals("PAID",payments.findByOrderId(o.getId()).orElseThrow().getStatus());
        Long id=rows.getFirst().id;
        mvc.perform(post("/api/manager/refunds/"+id+"/reject").header("Authorization",token).contentType("application/json").content("{\"note\":\"Đối soát lại\"}")).andExpect(status().isOk());
        adminOrders.updateStatus(o.getId(),new StatusRequest("CANCELLED","Test"));assertEquals(1,refunds.findByOrderIdOrderByIdDesc(o.getId()).size());
        assertEquals("PENDING",mapper.readTree(create(o.getId(),100000,"replacement","CANCEL")).path("status").asText());
    }
    @Test void partialRefundsAreBoundedIdempotentAndPublicResponseDoesNotLeakNotes()throws Exception{
        Order o=order("PAYOS","DELIVERED","PAID");returns(o);
        long first=mapper.readTree(create(o.getId(),30000,"part-1","RETURN")).path("id").asLong();
        assertEquals(first,mapper.readTree(create(o.getId(),30000,"part-1","RETURN")).path("id").asLong());
        long second=mapper.readTree(create(o.getId(),70000,"part-2","RETURN")).path("id").asLong();
        mvc.perform(get("/api/manager/refunds").param("q",o.getOrderCode().toString()).param("status","PENDING").param("size","1").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(post("/api/manager/orders/"+o.getId()+"/refunds").header("Authorization",token).contentType("application/json").content("{\"reasonType\":\"RETURN\",\"amount\":1,\"note\":\"Vượt\",\"requestKey\":\"extra\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/manager/refunds/"+first+"/complete").header("Authorization",token).contentType("application/json").content("{\"note\":\"Test\"}")).andExpect(status().isBadRequest());
        String body="{\"note\":\"Nội bộ bí mật\",\"bankReference\":\"BANK-PRIVATE\"}";
        for(int i=0;i<2;i++)mvc.perform(post("/api/manager/refunds/"+first+"/complete").header("Authorization",token).contentType("application/json").content(body)).andExpect(status().isOk());
        Order current=orders.findById(o.getId()).orElseThrow();assertEquals("PARTIAL",current.refundStatus);assertEquals(0,new BigDecimal("70000").compareTo(current.refundPendingAmount));
        mvc.perform(get("/api/manager/orders").param("q",o.getOrderCode().toString()).param("refundStatus","PARTIAL").param("paymentReview","false").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/manager/audit-logs").param("action","REFUND_COMPLETE").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(post("/api/manager/refunds/"+first+"/complete").header("Authorization",token).contentType("application/json").content("{\"note\":\"Nội bộ bí mật\",\"bankReference\":\"DIFFERENT\"}")).andExpect(status().isConflict());
        String response=mvc.perform(get("/api/orders/"+o.getId()).header("Authorization","Bearer "+jwt.issue(customer.getId(),"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.refund.status").value("PARTIAL")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("BANK-PRIVATE"));assertFalse(response.contains("Nội bộ bí mật"));
        mvc.perform(post("/api/manager/refunds/"+second+"/complete").header("Authorization",token).contentType("application/json").content(body)).andExpect(status().isOk());
        assertEquals("FULL",orders.findById(o.getId()).orElseThrow().refundStatus);
        for(String role:List.of("ADMIN","CUSTOMER"))mvc.perform(get("/api/manager/refunds").header("Authorization","Bearer "+jwt.issue(user(role).getId(),role))).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/refunds")).andExpect(status().isUnauthorized());
    }
    @Test void codRequiresExplicitCollectionAndRepeatedConfirmationPreservesEvidence()throws Exception{
        Order o=order("COD","DELIVERED","COD");returns(o);
        String request="{\"reasonType\":\"RETURN\",\"amount\":10000,\"note\":\"Test\",\"requestKey\":\"cod\"}";
        mvc.perform(post("/api/manager/orders/"+o.getId()+"/refunds").header("Authorization",token).contentType("application/json").content(request)).andExpect(status().isConflict());
        String url="/api/manager/orders/"+o.getId()+"/cod-collection";
        mvc.perform(patch(url).header("Authorization",token).contentType("application/json").content("{\"note\":\"Đã đối soát\"}")).andExpect(status().isOk());
        Instant collected=orders.findById(o.getId()).orElseThrow().codCollectedAt;
        mvc.perform(patch(url).header("Authorization",token).contentType("application/json").content("{\"note\":\"Gửi lại\"}")).andExpect(status().isOk());
        assertEquals(collected,orders.findById(o.getId()).orElseThrow().codCollectedAt);assertEquals("PAID",payments.findByOrderId(o.getId()).orElseThrow().getStatus());create(o.getId(),10000,"cod","RETURN");
        Order pending=order("COD","PENDING","COD");mvc.perform(patch("/api/manager/orders/"+pending.getId()+"/cod-collection").header("Authorization",token).contentType("application/json").content("{\"note\":\"Test\"}")).andExpect(status().isConflict());
    }
    @Test void autoCancelChecksProviderAgainAndRepeatedWorkersDoNotDuplicateEffects(){
        Order o=order("PAYOS","PENDING","EXPIRED");String link="link-"+o.getId();
        when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode(),100000,0,"PENDING",link),new PayosService.ProviderPayment(o.getOrderCode(),100000,0,"CANCELLED",link));
        assertEquals("CANCELLED",worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24)));assertEquals("SKIPPED",worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24)));verify(payos,times(1)).cancelPaymentLink(link);
        assertTrue(orders.findById(o.getId()).orElseThrow().isStockReleased());
    }
    @Test void newerQrAndConfirmedOrdersCannotAutoCancel(){
        Order o=order("PAYOS","PENDING","EXPIRED");tx.executeWithoutResult(s->{PaymentAttempt a=new PaymentAttempt();a.setOrder(orders.findById(o.getId()).orElseThrow());a.setProviderOrderCode(o.getOrderCode());a.setPaymentLinkId("new-link");a.setExpiresAt(Instant.now().plusSeconds(900));attempts.save(a);});
        assertEquals("SKIPPED",worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24)));
        Order confirmed=order("PAYOS","CONFIRMED","EXPIRED");assertEquals("SKIPPED",worker.cancel(confirmed.getId(),Instant.now(),Duration.ofHours(24)));verifyNoInteractions(payos);
    }
    @Test void boundaryIsExactlyTwentyFourHoursAfterLatestExpiry(){
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);Order before=order("PAYOS","PENDING","EXPIRED"),due=order("PAYOS","PENDING","EXPIRED");
        tx.executeWithoutResult(s->{orders.findById(before.getId()).orElseThrow().setExpiresAt(now.minus(Duration.ofHours(24)).plusSeconds(1));orders.findById(due.getId()).orElseThrow().setExpiresAt(now.minus(Duration.ofHours(24)));});
        assertEquals("SKIPPED",worker.cancel(before.getId(),now,Duration.ofHours(24)));
        String link="link-"+due.getId();when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(due.getOrderCode(),100000,0,"EXPIRED",link));
        assertEquals("CANCELLED",worker.cancel(due.getId(),now,Duration.ofHours(24)));
    }
    @Test void localPaidAttemptIsNeverOverwrittenByContradictoryProviderStatus(){
        Order o=order("PAYOS","PENDING","EXPIRED");String link="link-"+o.getId();
        tx.executeWithoutResult(s->{var a=new PaymentAttempt();a.setOrder(orders.findById(o.getId()).orElseThrow());a.setProviderOrderCode(o.getOrderCode());a.setPaymentLinkId(link);a.setStatus("PAID");attempts.save(a);});
        when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode(),100000,0,"CANCELLED",link));
        assertEquals("NEEDS_REVIEW",worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24)));assertEquals("PAID",attempts.findFirstByOrderIdOrderByIdDesc(o.getId()).orElseThrow().getStatus());assertFalse(orders.findById(o.getId()).orElseThrow().isStockReleased());
    }
    @Test void twoSchedulersCancelTheSameOrderOnlyOnce()throws Exception{
        Order o=order("PAYOS","PENDING","EXPIRED");String link="link-"+o.getId();when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode(),100000,0,"EXPIRED",link));CountDownLatch start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24));});var b=pool.submit(()->{start.await();return worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24));});start.countDown();
            assertEquals(Set.of("CANCELLED","SKIPPED"),Set.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));
        }
        verify(payos,times(1)).getPayment(link);
    }
    @Test void partialMoneyUnknownProviderAndMissingLinkAreFlaggedNotCancelled(){
        for(String state:List.of("PARTIAL","UNKNOWN","MISSING")){
            Order o=order("PAYOS","PENDING","EXPIRED");String link="link-"+o.getId();
            if(state.equals("MISSING"))tx.executeWithoutResult(s->payments.findByOrderId(o.getId()).orElseThrow().setPaymentLinkId(null));
            else when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode(),100000,state.equals("PARTIAL")?1:0,state,link));
            assertEquals("NEEDS_REVIEW",worker.cancel(o.getId(),Instant.now(),Duration.ofHours(24)));Order current=orders.findById(o.getId()).orElseThrow();assertEquals("PENDING",current.getStatus());assertTrue(current.paymentReviewRequired);assertFalse(current.isStockReleased());
        }
    }
    @Test void syncOfCancelledPaidOrderCreatesRefundOnce()throws Exception{
        Order o=order("PAYOS","CANCELLED","CANCELLED");String link="link-"+o.getId();
        tx.executeWithoutResult(s->{var a=new PaymentAttempt();a.setOrder(orders.findById(o.getId()).orElseThrow());a.setProviderOrderCode(o.getOrderCode());a.setPaymentLinkId(link);attempts.save(a);});
        when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode(),100000,100000,"PAID",link));
        customerOrders.syncPayment(customer,o.getId());customerOrders.syncPayment(customer,o.getId());assertEquals(1,refunds.findByOrderIdOrderByIdDesc(o.getId()).size());assertEquals("PAID",orders.findById(o.getId()).orElseThrow().getPaymentStatus());
    }
    @Test void reconcilingLegacyAlreadyPaidCancellationDoesNotBackfillRefunds()throws Exception{
        Order o=order("PAYOS","CANCELLED","PAID");String link="link-"+o.getId();when(payos.getPayment(link)).thenReturn(new PayosService.ProviderPayment(o.getOrderCode(),100000,100000,"PAID",link));
        mvc.perform(post("/api/manager/orders/"+o.getId()+"/payment/reconcile").header("Authorization",token)).andExpect(status().isOk());
        assertTrue(refunds.findByOrderIdOrderByIdDesc(o.getId()).isEmpty());
    }
}
