package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.entity.EmailOutbox.Status;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:emailOutbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop", "crm.care-reminders.enabled=false", "EMAIL_OUTBOX_WORKER_ENABLED=false"})
@AutoConfigureMockMvc
class EmailOutboxIntegrationTest {
    @Autowired EmailOutboxService service;
    @Autowired NotificationService notifications;
    @Autowired EmailOutboxRepository rows;
    @Autowired CustomerNotificationRepository inApp;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @MockitoBean TransactionalMail mail;
    @MockitoBean AuditTrail audit;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager); rows.deleteAll();
        when(mail.available()).thenReturn(true);when(mail.sandboxExcludes(anyString())).thenReturn(false);
        when(mail.link(anyString())).thenAnswer(a->"https://example.test"+a.getArgument(0));
    }
    long enqueue() {
        tx.executeWithoutResult(s->service.enqueue("private@example.test","Private subject","Private body","event-"+UUID.randomUUID()));
        long id=rows.findAll().getLast().id;
        // A ready-job fixture must be unambiguously in the past, independent of
        // JDBC timestamp rounding and the host clock while threads are started.
        tx.executeWithoutResult(s->{var row=rows.findById(id).orElseThrow();row.nextAttemptAt=Instant.now().minusSeconds(5);});
        return id;
    }
    @Test void businessRollbackRemovesBothNotificationAndEmailAndEnqueueNeedsTransaction() {
        long count=inApp.count();
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->service.enqueue("a","s","b","k"));
        tx.executeWithoutResult(s->{
            var user=new User();user.setFullName("Customer");user.setEmail(UUID.randomUUID()+"@example.test");user.setPasswordHash("test");em.persist(user);
            notifications.create(user,"Order","Public content","ORDER_STATUS",7L);
            assertEquals(1,rows.count());s.setRollbackOnly();
        });
        assertEquals(0,rows.count());assertEquals(count,inApp.count());
    }
    @Test void commitPersistsNotificationAndOutboxWithoutCallingProvider() {
        tx.executeWithoutResult(s->{
            var user=new User();user.setFullName("Customer");user.setEmail(UUID.randomUUID()+"@example.test");user.setPasswordHash("test");em.persist(user);
            notifications.create(user,"Order","Public content","ORDER_STATUS",7L);
        });
        assertEquals(1,rows.count());assertEquals(Status.PENDING,rows.findAll().getFirst().status);
        verify(mail,never()).send(anyString(),anyString(),anyString(),anyString());
    }
    @Test void configurationFailureIsVisibleButSandboxExclusionNeverQueues() {
        when(mail.available()).thenReturn(false);enqueue();
        assertEquals(Status.FAILED,rows.findAll().getFirst().status);
        assertEquals("CONFIG_UNAVAILABLE",rows.findAll().getFirst().reason);
        tx.executeWithoutResult(s->{
            var user=new User();user.setFullName("Customer");user.setEmail(UUID.randomUUID()+"@example.test");user.setPasswordHash("test");em.persist(user);
            when(mail.sandboxExcludes(anyString())).thenReturn(true);
            notifications.create(user,"Order","Public content","ORDER_STATUS",7L);
        });
        assertEquals(1,rows.count());
    }
    @Test void twoWorkersCannotClaimSameRowAndExpiredLeaseBecomesUnknown() throws Exception {
        long id=enqueue();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Optional<EmailOutboxService.Claim>> action=()->{start.await();return service.claim();};
            var first=pool.submit(action);var second=pool.submit(action);start.countDown();
            var claims=List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertEquals(1,claims.stream().filter(Optional::isPresent).count());
            var claim=claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            tx.executeWithoutResult(s->{var row=rows.findById(id).orElseThrow();row.leaseUntil=Instant.now().minusSeconds(1);rows.save(row);});
            assertTrue(service.claim().isEmpty());assertEquals(Status.UNKNOWN,rows.findById(id).orElseThrow().status);
            service.finish(claim,TransactionalMail.Outcome.ACCEPTED,"PROVIDER_ACCEPTED");
            assertEquals(Status.UNKNOWN,rows.findById(id).orElseThrow().status);
            assertThrows(com.htttdn.crm.exception.ApiException.class,()->service.retry(id,false));
            service.retry(id,true);assertEquals(Status.PENDING,rows.findById(id).orElseThrow().status);
            verify(audit).record(eq("EMAIL_RETRY"),eq("EMAIL_OUTBOX"),eq(id),anyMap(),anyMap());
        } finally {pool.shutdownNow();}
    }
    @Test void retriesBackOffAndStopAtFivePermissionAndUnknownNeverAutoRetry() {
        long id=enqueue();
        for(int attempt=1;attempt<=5;attempt++) {
            var claim=service.claim().orElseThrow();assertEquals(attempt,claim.attempts());
            Instant before=Instant.now();service.finish(claim,TransactionalMail.Outcome.RETRYABLE,"HTTP_429");
            var row=rows.findById(id).orElseThrow();
            assertEquals(attempt<5?Status.PENDING:Status.FAILED,row.status);
            // JDBC TIMESTAMP precision can truncate the Java nanosecond instant.
            assertFalse(row.nextAttemptAt.isBefore(before.plusSeconds(30L << (attempt-1)).minusMillis(1)));
            assertTrue(service.claim().isEmpty());
            if(attempt<5)tx.executeWithoutResult(s->{var e=rows.findById(id).orElseThrow();e.nextAttemptAt=Instant.now().minusSeconds(1);rows.save(e);});
        }
        assertThrows(com.htttdn.crm.exception.ApiException.class,()->service.retry(id,true));
        rows.deleteAll();enqueue();service.finish(service.claim().orElseThrow(),TransactionalMail.Outcome.FAILED,"PERMISSION_DENIED");
        assertTrue(service.claim().isEmpty());assertEquals(Status.FAILED,rows.findAll().getFirst().status);
        rows.deleteAll();enqueue();service.finish(service.claim().orElseThrow(),TransactionalMail.Outcome.UNKNOWN,"AMBIGUOUS_IO");
        assertTrue(service.claim().isEmpty());assertEquals(Status.UNKNOWN,rows.findAll().getFirst().status);
    }
    @Test void workerSendsOutsideTransactionAndMarksAccepted() {
        enqueue();
        doAnswer(a->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return null;})
            .when(mail).send(anyString(),anyString(),anyString(),anyString());
        new EmailOutboxWorker(service,mail).poll();
        assertEquals(Status.ACCEPTED,rows.findAll().getFirst().status);
        verify(mail,times(1)).send(anyString(),anyString(),anyString(),anyString());
    }
    String bearer(String role) {
        return tx.execute(s->{var user=new User();user.setFullName(role);user.setRole(role);user.setEmail(UUID.randomUUID()+"@example.test");user.setPasswordHash("test");em.persist(user);return "Bearer "+jwt.issue(user.getId(),role);});
    }
    @Test void adminRoutesProtectSensitiveContentAndRequireUnknownAcknowledgement() throws Exception {
        long id=enqueue();
        mvc.perform(get("/api/admin/emails")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/emails").header("Authorization",bearer("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/emails/"+id+"/retry").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        String admin=bearer("ADMIN");
        mvc.perform(get("/api/admin/emails").header("Authorization",admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].body").doesNotExist()).andExpect(jsonPath("$.content[0].subject").doesNotExist())
            .andExpect(jsonPath("$.content[0].recipient").doesNotExist()).andExpect(jsonPath("$.content[0].recipientHint").value("p***@example.test"));
        service.finish(service.claim().orElseThrow(),TransactionalMail.Outcome.UNKNOWN,"AMBIGUOUS_IO");
        mvc.perform(post("/api/admin/emails/"+id+"/retry").header("Authorization",admin).contentType("application/json").content("{}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/emails/"+id+"/retry").header("Authorization",admin).contentType("application/json").content("{\"acknowledgeUnknown\":true}"))
            .andExpect(status().isOk());
        assertEquals(Status.PENDING,rows.findById(id).orElseThrow().status);
    }
}
