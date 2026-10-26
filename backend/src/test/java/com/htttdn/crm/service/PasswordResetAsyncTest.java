package com.htttdn.crm.service;

import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.PasswordResetTokenRepository;
import com.htttdn.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:passwordResetAsync;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.auth.jwt-secret=synthetic-password-reset-test-secret-2026"
})
class PasswordResetAsyncTest {
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired ThreadPoolTaskExecutor transactionalMailExecutor;
    @MockitoBean TransactionalMail mail;

    @Test void responsesDoNotWaitForDeliveryAndWorkerRetainsTransaction() throws Exception {
        var customer = new User();
        customer.setEmail("async@example.com");customer.setFullName("Async Customer");
        customer.setRole("CUSTOMER");customer.setPasswordHash("unused-test-hash");
        customer = users.saveAndFlush(customer);
        when(mail.available()).thenReturn(true);when(mail.permits(anyString())).thenReturn(true);
        when(mail.link(anyString())).thenAnswer(a -> "https://example.com" + a.getArgument(0));
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var transactionActive = new AtomicBoolean();
        var separateThread = new AtomicBoolean();
        doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            separateThread.set(Thread.currentThread().getName().startsWith("transactional-mail-"));
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test delivery timed out");
            return null;
        }).when(mail).send(anyString(),anyString(),anyString(),anyString());
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertNull(auth.requestPasswordReset("async@example.com")));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertNull(auth.requestPasswordReset("missing@example.com")));
            assertTrue(transactionActive.get());assertTrue(separateThread.get());
        } finally {
            release.countDown();
            transactionalMailExecutor.submit(() -> {}).get(10, TimeUnit.SECONDS);
        }
        assertTrue(tokens.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId()).isPresent());
        verify(mail,times(1)).send(anyString(),anyString(),anyString(),anyString());
    }
}
