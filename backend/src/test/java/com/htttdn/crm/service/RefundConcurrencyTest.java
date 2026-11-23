package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:refundConcurrent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
class RefundConcurrencyTest {
    @Autowired RefundLedgerService service;
    @Autowired EntityManager em;
    @Autowired OrderRepository orders;
    @Autowired OrderRefundRepository refunds;
    @Autowired JwtTokenService jwt;
    @Autowired PlatformTransactionManager tx;
    @MockitoSpyBean NotificationService notifications;
    record Fixture(Long orderId,String token){}
    Fixture fixture(){
        String key="FT-"+UUID.randomUUID();
        return new TransactionTemplate(tx).execute(status->{
            User m=new User();m.setFullName("Manager");m.setEmail(UUID.randomUUID()+"@test.local");m.setPasswordHash("hash");m.setRole("MANAGER");em.persist(m);
            User c=new User();c.setFullName("Customer");c.setEmail(UUID.randomUUID()+"@test.local");c.setPasswordHash("hash");em.persist(c);
            Order o=new Order();o.setOrderCode(Math.abs(UUID.randomUUID().getMostSignificantBits()));o.setCustomer(c);o.setStatus("COMPLETED");
            o.setReturnStatus("APPROVED");o.setPaymentMethod("PAYOS");o.setPaymentStatus("PAID");o.setTotalAmount(new BigDecimal("100000"));em.persist(o);em.flush();
            return new Fixture(o.getId(),"Bearer "+jwt.issue(m.getId(),"MANAGER"));
        });
    }
    RefundLedgerService.CreateInput amount(String value){return new RefundLedgerService.CreateInput(new BigDecimal(value),"BANK_TRANSFER","FT","x",null,null);}

    @Test void concurrentPendingRefundsCannotExceedTheCap() throws Exception {
        var f=fixture();var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try{
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<2;i++)results.add(executor.submit(()->{start.await();
                try{service.create(f.token(),f.orderId(),amount("80000"));return true;}
                catch(ApiException e){return false;}}));
            start.countDown();int success=0;for(var r:results)if(r.get(10,TimeUnit.SECONDS))success++;
            assertEquals(1,success);
            new TransactionTemplate(tx).execute(s->{assertEquals(1,refunds.findByOrderIdOrderByIdAsc(f.orderId()).size());return null;});
            Order saved=new TransactionTemplate(tx).execute(s->orders.findById(f.orderId()).orElseThrow());
            assertEquals(0,saved.getRefundPendingAmount().compareTo(new BigDecimal("80000")));
            verify(notifications,times(1)).create(any(),anyString(),anyString(),eq("REFUND_REQUEST"),eq(f.orderId()));
        }finally{executor.shutdownNow();}
    }
    @Test void notificationFailureRollsBackTheLedgerEntryAndReservation(){
        var f=fixture();doThrow(new IllegalStateException("Storage unavailable")).when(notifications).create(any(),anyString(),anyString(),eq("REFUND_REQUEST"),eq(f.orderId()));
        try{
            assertThrows(IllegalStateException.class,()->service.create(f.token(),f.orderId(),amount("10000")));
            new TransactionTemplate(tx).execute(s->{
                assertEquals(0,refunds.findByOrderIdOrderByIdAsc(f.orderId()).size());
                Order unchanged=em.find(Order.class,f.orderId());
                em.refresh(unchanged);
                assertEquals(0,unchanged.getRefundPendingAmount().signum());
                assertEquals("NONE",unchanged.getRefundStatus());
                return null;});
        }finally{reset(notifications);}
    }
}
