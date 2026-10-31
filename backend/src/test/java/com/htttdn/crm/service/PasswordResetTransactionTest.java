package com.htttdn.crm.service;

import com.htttdn.crm.entity.PasswordResetToken;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.repository.PasswordResetTokenRepository;
import com.htttdn.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:resetTx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=0",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.rate-limit.enabled=false"})
class PasswordResetTransactionTest {
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PlatformTransactionManager tm;
    @Autowired DataSource dataSource;
    @MockitoBean TransactionalMail mail;

    private User customer() {
        var user = new User();
        user.setEmail("reset-" + UUID.randomUUID() + "@example.com");
        user.setFullName("Reset Test");
        user.setRole("CUSTOMER");
        user.setPasswordHash("test-only");
        return users.save(user);
    }

    private void stubMail(User user) {
        when(mail.available()).thenReturn(true);
        when(mail.permits(user.getEmail())).thenReturn(true);
        when(mail.link(anyString())).thenAnswer(a -> "https://example.com" + a.getArgument(0));
    }

    private int tokenCount(User user) {
        return new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from password_reset_tokens where customer_id=?", Integer.class, user.getId());
    }

    private PasswordResetToken previousToken(User user) {
        var previous = new PasswordResetToken();
        previous.setCustomer(user);
        previous.setTokenHash(AuthService.hash("previous-token"));
        previous.setExpiresAt(Instant.now().plusSeconds(600));
        previous.setCreatedAt(Instant.now().minusSeconds(300));
        return tokens.saveAndFlush(previous);
    }

    @Test void tokenIsCommittedBeforeMailIsSentAndUserRowIsUnlocked() {
        User user = customer();
        stubMail(user);
        var separate = new TransactionTemplate(tm);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var jdbc = new JdbcTemplate(dataSource);
        doAnswer(invocation -> {
            Integer stored = separate.execute(status -> jdbc.queryForObject(
                    "select count(*) from password_reset_tokens where customer_id=?", Integer.class, user.getId()));
            assertEquals(1, stored);
            separate.executeWithoutResult(status -> jdbc.queryForList(
                    "select customer_id from customers where customer_id=? for update", user.getId()));
            return null;
        }).when(mail).send(eq(user.getEmail()), anyString(), anyString(), anyString());

        assertNull(auth.requestPasswordReset(user.getEmail()));

        verify(mail).send(eq(user.getEmail()), anyString(), anyString(), anyString());
        assertEquals(1, tokenCount(user));
    }

    @Test void successfulSendPrunesOlderTokens() {
        User user = customer();
        var previous = previousToken(user);
        stubMail(user);

        assertNull(auth.requestPasswordReset(user.getEmail()));

        assertEquals(1, tokenCount(user));
        assertFalse(tokens.findByTokenHash(previous.getTokenHash()).isPresent());
    }

    @Test void mailFailureRemovesNewTokenAndKeepsPreviousOne() {
        User user = customer();
        var previous = previousToken(user);
        stubMail(user);
        doThrow(new IllegalStateException("provider down")).when(mail).send(anyString(), anyString(), anyString(), anyString());

        assertNull(auth.requestPasswordReset(user.getEmail()));

        assertEquals(1, tokenCount(user));
        assertTrue(tokens.findByTokenHash(previous.getTokenHash()).isPresent());
    }
}
