package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Harness note: like {@link ShipmentIntegrationTest}, MockMvc runs nested inside the test transaction. Entities written
 * by a request (or by the service) are only visible after {@code em.clear()} and a re-read, so every helper below
 * detaches the session first and never carries a stale entity across calls.
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:refundLedgerTests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_MODE=0;DEFAULT_LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.hikari.auto-commit=false","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc @Transactional
class RefundLedgerIntegrationTest {
    @Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired JwtTokenService jwt;
    @Autowired RefundLedgerService service; @Autowired OrderRefundRepository refunds;
    @Autowired CustomerNotificationRepository notifications; @Autowired AuditLogRepository auditLogs;
    @Autowired ObjectMapper json; @Autowired PlatformTransactionManager tx;
    User manager, customer, admin; Order order;

    @BeforeEach void setup(){
        manager=user("Manager","MANAGER"); customer=user("Customer","CUSTOMER"); admin=user("Admin","ADMIN");
        order=new Order(); order.setOrderCode(System.nanoTime()); order.setCustomer(customer); order.setStatus("COMPLETED");
        order.setPaymentMethod("PAYOS"); order.setPaymentStatus("PAID"); order.setTotalAmount(new BigDecimal("300000"));
        order.setReturnStatus("APPROVED");
        em.persist(order); em.flush();
    }
    User user(String name,String role){User u=new User();u.setFullName(name);u.setEmail(name+System.nanoTime()+"@test.local");u.setPasswordHash("hash");u.setRole(role);em.persist(u);return u;}
    String token(User u){return "Bearer "+jwt.issue(u.getId(),u.getRole());}
    /** Order mutations are flushed before the next call so the request transaction sees them. */
    Order update(Consumer<Order> change){em.flush();change.accept(em.find(Order.class,order.getId()));em.flush();em.clear();return refresh();}
    Order refresh(){em.clear();order=em.find(Order.class,order.getId());return order;}
    /** Runs a service call as its own committed unit of work, then detaches so later reads skip the stale first-level cache. */
    <T> T call(Supplier<T> body){try{return new TransactionTemplate(tx).execute(status->body.get());}finally{refresh();}}
    /** Asserts the service rejects the call; the thrown value is inspected by the caller. */
    RuntimeException fails(Supplier<?> body){
        var failure=assertThrows(RuntimeException.class,()->call(()->{body.get();return null;}));
        refresh();
        return failure;
    }
    ApiException apiFails(Supplier<?> body){
        var failure=fails(body);
        assertInstanceOf(ApiException.class,failure,()->"expected ApiException but got "+failure);
        return (ApiException)failure;
    }
    int webStatus(MockHttpServletRequestBuilder request)throws Exception{
        return web(request).getResponse().getStatus();
    }
    /**
     * MockMvc joins the test transaction through a nested (rollback-only) transaction, and H2 hands each session its own
     * MVCC snapshot outside READ_COMMITTED: neither plain requests nor REQUIRES_NEW service calls can see writes made by
     * the other. Every HTTP call therefore runs in one explicit transaction that is committed (released into the store)
     * and the service is called in another, so both sides observe the same data.
     */
    MvcResult web(MockHttpServletRequestBuilder request)throws Exception{
        MvcResult result=new TransactionTemplate(tx).execute(status->{
            try{return mvc.perform(request).andReturn();}
            catch(Exception e){throw new IllegalStateException(e);}
        });
        refresh();
        return result;
    }
    JsonNode expect(MockHttpServletRequestBuilder request,int status)throws Exception{
        var result=web(request).getResponse();
        String text=result.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(status,result.getStatus(),text);
        return json.readTree(text.isEmpty()?"null":text);
    }
    MockHttpServletRequestBuilder postRefund(User actor,Object body)throws Exception{
        return post("/api/manager/orders/"+order.getId()+"/refunds").header("Authorization",token(actor))
            .contentType("application/json").content(json.writeValueAsString(body));
    }
    MockHttpServletRequestBuilder patchRefund(User actor,Long refundId,Object body)throws Exception{
        return patch("/api/manager/orders/"+order.getId()+"/refunds/"+refundId).header("Authorization",token(actor))
            .contentType("application/json").content(json.writeValueAsString(body));
    }
    Map<String,Object> amount(String value,String method){
        Map<String,Object> body=new HashMap<>();body.put("amount",new BigDecimal(value));if(method!=null)body.put("method",method);return body;
    }
    RefundLedgerService.CreateInput input(String value,String method,String ref,String note,String key){
        return new RefundLedgerService.CreateInput(new BigDecimal(value),method,ref,note,null,key);
    }
    RefundLedgerService.ProcessInput decide(String status,String note,String bank){
        return new RefundLedgerService.ProcessInput(status,note,bank,null);
    }
    ManagerOrderQuery.View create(String actor,String value,String method,String ref,String note,String key){
        return call(()->service.create(actor,order.getId(),input(value,method,ref,note,key)));
    }
    ManagerOrderQuery.View process(String actor,Long refundId,String status,String note,String bank){
        return call(()->service.process(actor,order.getId(),refundId,decide(status,note,bank)));
    }
    List<OrderRefund> ledger(){refresh();return refunds.findByOrderIdOrderByIdAsc(order.getId());}
    Long onlyRefundId(){return ledger().getFirst().getId();}
    int refundCount(){return ledger().size();}
    long notificationCount(String type){refresh();return notifications.findAll().stream().filter(n->type.equals(n.getType())).count();}
    AuditLog audit(String action){refresh();return auditLogs.findAll().stream().filter(a->action.equals(a.getAction())).findFirst().orElseThrow();}
    boolean audited(String action){refresh();return auditLogs.findAll().stream().anyMatch(a->action.equals(a.getAction()));}

    @Test void createsPendingRefundWithinCapThenCompletesIt() throws Exception {
        var created=expect(postRefund(manager,Map.of("amount",100000,"method","BANK_TRANSFER","note","Hoàn tiền áo","bankReference","FT-1")),200);
        assertEquals("PENDING",created.at("/refunds/0/status").asText());
        assertEquals(0,created.at("/finance/refundPendingAmount").decimalValue().compareTo(new BigDecimal("100000")));
        assertEquals(0,created.at("/finance/refundableAmount").decimalValue().compareTo(new BigDecimal("200000")));
        var completed=expect(patchRefund(manager,onlyRefundId(),Map.of("status","COMPLETED","processingNote","Đã chuyển khoản","bankReference","FT-1")),200);
        assertEquals("COMPLETED",completed.at("/refunds/0/status").asText());
        assertEquals("Đã chuyển khoản",completed.at("/refunds/0/processingNote").asText());
        assertEquals(0,completed.at("/finance/refundedAmount").decimalValue().compareTo(new BigDecimal("100000")));
        assertEquals("PARTIAL",completed.at("/finance/refundStatus").asText());
        assertEquals(0,completed.at("/finance/refundPendingAmount").decimalValue().signum());
        assertEquals(0,completed.at("/finance/refundableAmount").decimalValue().compareTo(new BigDecimal("200000")));
        assertEquals(1,notificationCount("REFUND_REQUEST"));
        assertEquals(1,notificationCount("REFUND_COMPLETED"));
        assertEquals(0,order.getRefundPendingAmount().signum());
        assertEquals(0,order.getRefundedAmount().compareTo(new BigDecimal("100000")));
        var entry=audit("REFUND_COMPLETE");
        assertEquals("ORDER",entry.getEntityType()); assertEquals(manager.getId(),entry.getActorId());
        assertTrue(entry.getAfterData().contains("\"refundedAmount\":100000"));
        assertTrue(AuditLogQuery.ACTIONS.containsAll(List.of("REFUND_REQUEST","REFUND_COMPLETE","REFUND_REJECT")));
    }
    @Test void rejectsOverCapUnpaidAndInvalidTransitions() throws Exception {
        update(o->o.setPaymentStatus("PENDING"));
        assertEquals(400,webStatus(postRefund(manager,amount("1000",null))));
        update(o->o.setPaymentStatus("PAID"));
        assertEquals(400,webStatus(postRefund(manager,amount("400000",null))));
        assertEquals(400,webStatus(postRefund(manager,amount("0",null))));
        assertEquals(400,webStatus(postRefund(manager,amount("-5",null))));
        assertEquals(400,webStatus(postRefund(manager,amount("1000","CRYPTO"))));
        update(o->o.setReturnStatus("REQUESTED"));
        assertEquals(409,webStatus(postRefund(manager,amount("1000",null))));
        assertEquals(0,refundCount());
        assertEquals(0,order.getRefundPendingAmount().signum());
    }
    @Test void completedRefundRequiresBankReferenceAndRejectsDoubleProcess(){
        create(token(manager),"50000","CASH",null,"Thu tiền mặt",null);
        Long rid=onlyRefundId();
        assertEquals("REFUND_REFERENCE_REQUIRED",apiFails(()->process(token(manager),rid,"COMPLETED","Đã trả",null)).code());
        assertEquals("REFUND_NOTE_REQUIRED",apiFails(()->process(token(manager),rid,"COMPLETED","  ","CASH-1")).code());
        assertEquals(400,apiFails(()->process(token(manager),rid,"PENDING","x","CASH-1")).status().value());
        var done=process(token(manager),rid,"COMPLETED","Đã trả tiền mặt","CASH-1");
        assertEquals("COMPLETED",done.refunds().getFirst().status());
        var replay=process(token(manager),rid,"COMPLETED","Đã trả tiền mặt","CASH-1");
        assertEquals("COMPLETED",replay.refunds().getFirst().status());
        assertEquals(1,replay.refunds().size());
        assertEquals(409,apiFails(()->process(token(manager),rid,"REJECTED","x",null)).status().value());
        assertEquals(1,notificationCount("REFUND_COMPLETED"));
        assertEquals(0,order.getRefundPendingAmount().signum());
        assertEquals(0,order.getRefundedAmount().compareTo(new BigDecimal("50000")));
        assertEquals("PARTIAL",order.getRefundStatus());
    }
    @Test void customerAdminCannotOperateAndCustomerViewHidesBankData() throws Exception {
        create(token(manager),"1000","BANK_TRANSFER","FT-9","x",null);
        Long rid=onlyRefundId();
        process(token(manager),rid,"COMPLETED","Đã chuyển","FT-9");
        for(User u:List.of(customer,admin)){
            assertEquals(403,webStatus(postRefund(u,amount("1000",null))));
            assertEquals(403,webStatus(patchRefund(u,rid,Map.of("status","REJECTED","processingNote","x"))));
        }
        assertEquals(401,webStatus(post("/api/manager/orders/"+order.getId()+"/refunds").contentType("application/json").content("{\"amount\":1000}")));
        var customerView=expect(get("/api/orders/"+order.getId()).header("Authorization",token(customer)),200);
        assertEquals("COMPLETED",customerView.at("/refunds/0/status").asText());
        assertEquals(0,customerView.at("/refunds/0/amount").decimalValue().compareTo(new BigDecimal("1000")));
        assertFalse(customerView.at("/refunds/0").has("bankReference"));
        assertFalse(customerView.at("/refunds/0").has("note"));
        assertFalse(customerView.at("/refunds/0").has("processingNote"));
        assertFalse(customerView.at("/refunds/0").has("requestedBy"));
        assertFalse(customerView.at("/refunds/0").has("processedBy"));
        assertFalse(customerView.has("refundedAmount"));
        var managerView=expect(get("/api/manager/orders/"+order.getId()).header("Authorization",token(manager)),200);
        assertEquals("FT-9",managerView.at("/refunds/0/bankReference").asText());
        assertEquals("RETURN",managerView.at("/refunds/0/reasonType").asText());
        assertEquals(0,managerView.at("/finance/refundedAmount").decimalValue().compareTo(new BigDecimal("1000")));
    }
    @Test void replayingTheSameRequestKeyDoesNotDuplicateTheLedgerEntry(){
        var first=create(token(manager),"1000","BANK_TRANSFER","FT","x","KEY-1");
        assertEquals(1,first.refunds().size());
        var replay=create(token(manager),"1000","BANK_TRANSFER","FT","x","KEY-1");
        assertEquals(1,replay.refunds().size());
        assertEquals(first.refunds().getFirst().id(),replay.refunds().getFirst().id());
        assertEquals(1,refundCount());
        assertEquals(0,order.getRefundPendingAmount().compareTo(new BigDecimal("1000")));
        assertEquals(1,notificationCount("REFUND_REQUEST"));
    }
    @Test void pendingRefundsReserveTheCapAndFullRefundIsReachedInSteps(){
        create(token(manager),"200000","BANK_TRANSFER","FT-1","a",null);
        assertEquals(400,apiFails(()->create(token(manager),"100001","BANK_TRANSFER","FT-2","b",null)).status().value());
        var second=create(token(manager),"100000","CASH",null,"c","K-2");
        assertEquals(0,(second.finance().refundableAmount().signum()));
        for(OrderRefund r:ledger())process(token(manager),r.getId(),"COMPLETED","Đã trả","REF-"+r.getId());
        assertEquals(0,order.getRefundedAmount().compareTo(new BigDecimal("300000")));
        assertEquals("FULL",order.getRefundStatus());
        assertEquals(0,order.getRefundPendingAmount().signum());
        assertEquals(2,refundCount());
    }
    @Test void rejectionReleasesTheCapBackAndNotifies() throws Exception {
        create(token(manager),"120000","CASH",null,"giữ",null);
        var view=expect(patchRefund(manager,onlyRefundId(),Map.of("status","REJECTED","processingNote","Không đủ điều kiện")),200);
        assertEquals("REJECTED",view.at("/refunds/0/status").asText());
        assertEquals(0,view.at("/finance/refundPendingAmount").decimalValue().signum());
        assertEquals(0,view.at("/finance/refundedAmount").decimalValue().signum());
        assertEquals("NONE",view.at("/finance/refundStatus").asText());
        assertEquals(0,view.at("/finance/refundableAmount").decimalValue().compareTo(new BigDecimal("300000")));
        assertEquals("NONE",order.getRefundStatus());
        assertTrue(audited("REFUND_REJECT"));
        assertEquals(1,notificationCount("REFUND_REJECTED"));
    }
    @Test void rejectingOneOfTwoPendingRefundsKeepsTheRemainingReservationVisible(){
        create(token(manager),"100000","BANK_TRANSFER","FT-1","a",null);
        create(token(manager),"100000","BANK_TRANSFER","FT-2","b",null);
        process(token(manager),ledger().get(1).getId(),"REJECTED","Không đủ điều kiện",null);
        assertEquals(1L,ledger().stream().filter(r->"PENDING".equals(r.getStatus())).count());
        assertEquals("PENDING",order.getRefundStatus());
        assertEquals(0,order.getRefundPendingAmount().compareTo(new BigDecimal("100000")));
        assertEquals(0,order.getRefundedAmount().signum());
        assertEquals(1,notificationCount("REFUND_REJECTED"));
    }
    @Test void codOrdersOnlyRefundCollectedCash(){
        update(o->{o.setPaymentMethod("COD");o.setPaymentStatus("COD");o.codCollectedAmount=new BigDecimal("150000");});
        create(token(manager),"50000","CASH",null,"thu một phần",null);
        assertEquals(400,apiFails(()->create(token(manager),"100001","CASH",null,"y",null)).status().value());
        update(o->o.codCollectedAmount=null);
        assertEquals(400,apiFails(()->create(token(manager),"1000","CASH",null,"z",null)).status().value());
    }
    @Test void cancelledOrderRefundIsMarkedAsCancelReason(){
        update(o->{o.setStatus("CANCELLED");o.setReturnStatus("NONE");});
        var view=create(token(manager),"1000","BANK_TRANSFER","FT","hủy",null);
        assertEquals("CANCEL",view.refunds().getFirst().reasonType());
        assertEquals("PENDING",view.finance().refundStatus());
        assertEquals("PENDING",view.refunds().getFirst().status());
    }
    @Test void refundOutsideCancelledOrApprovedReturnIsRejected(){
        update(o->{o.setStatus("PREPARING");o.setReturnStatus("NONE");});
        assertEquals(409,apiFails(()->create(token(manager),"1000","CASH",null,"x",null)).status().value());
        update(o->o.setReturnStatus("REQUESTED"));
        assertEquals(409,apiFails(()->create(token(manager),"1000","CASH",null,"x",null)).status().value());
        assertEquals(0,refundCount());
    }
    @Test void unknownOrderOrRefundReturnsNotFound(){
        assertEquals(404,apiFails(()->call(()->service.create(token(manager),999999999L,input("1000","CASH",null,"x",null)))).status().value());
        create(token(manager),"1000","CASH",null,"x",null);
        Long rid=onlyRefundId();
        assertEquals(404,apiFails(()->call(()->service.process(token(manager),999999999L,rid,decide("COMPLETED","x","REF")))).status().value());
        assertEquals(404,apiFails(()->process(token(manager),999999999L,"COMPLETED","x","REF")).status().value());
    }
}
