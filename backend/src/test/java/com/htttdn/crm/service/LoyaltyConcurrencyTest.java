package com.htttdn.crm.service;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.UserRepository;
import com.htttdn.crm.repository.StoreVoucherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=${CRM_POINTS_QA_URL:jdbc:h2:mem:loyaltyconcurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000}","spring.datasource.driver-class-name=${CRM_POINTS_QA_DRIVER:org.h2.Driver}","spring.datasource.username=${CRM_POINTS_QA_USER:sa}","spring.datasource.password=${CRM_POINTS_QA_PASSWORD:}","spring.jpa.database-platform=${CRM_POINTS_QA_DIALECT:org.hibernate.dialect.H2Dialect}","spring.jpa.hibernate.ddl-auto=${CRM_POINTS_QA_DDL:create-drop}","crm.care-reminders.enabled=false"})
class LoyaltyConcurrencyTest {
    @Autowired LoyaltyService service;
    @Autowired UserRepository users;
    @Autowired StoreVoucherRepository vouchers;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean NotificationService notifications;

    private User fundedCustomer() {
        return new TransactionTemplate(manager).execute(tx -> {
            var user=new User();user.setEmail(UUID.randomUUID()+"@points.test");
            user.setFullName("Khách thử");user.setRole("CUSTOMER");user.setPasswordHash("test-only");
            users.saveAndFlush(user);
            var staff=new User();staff.setEmail(UUID.randomUUID()+"@staff.test");staff.setFullName("Quản lý");
            staff.setRole("MANAGER");staff.setPasswordHash("test-only");users.saveAndFlush(staff);
            service.adjust(user,100,"Dữ liệu thử",new AuditTrail.Actor(staff.getId(),staff.getFullName(),staff.getRole()));
            return user;
        });
    }

    @Test void concurrentRequestsCannotSpendTheSameBalanceTwice() throws Exception {
        User customer=fundedCustomer();var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call=()->{start.await();try{service.redeem(customer,UUID.randomUUID().toString());return true;}
                catch(com.htttdn.crm.exception.ApiException e){assertEquals("INSUFFICIENT_POINTS",e.code());return false;}};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        assertEquals(0,service.balance(customer.getId()));
    }

    @Test void concurrentReplaysReturnTheSameVoucher() throws Exception {
        User customer=fundedCustomer();String key=UUID.randomUUID().toString();var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<LoyaltyService.RedeemResult> call=()->{start.await();return service.redeem(customer,key);};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();
            assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        assertEquals(0,service.balance(customer.getId()));
        verify(notifications,times(1)).createVoucher(any(),anyString(),anyString());
    }

    @Test void failureRollsBackBothVoucherAndPoints() {
        User customer=fundedCustomer();long before=vouchers.count();
        doThrow(new IllegalStateException("simulated failure")).when(notifications).createVoucher(any(),anyString(),anyString());
        assertThrows(IllegalStateException.class,()->service.redeem(customer,UUID.randomUUID().toString()));
        assertEquals(100,service.balance(customer.getId()));assertEquals(before,vouchers.count());
    }
}
