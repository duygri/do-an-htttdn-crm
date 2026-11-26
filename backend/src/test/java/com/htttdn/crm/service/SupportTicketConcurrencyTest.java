package com.htttdn.crm.service;

import com.htttdn.crm.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:supportRace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","crm.care-reminders.enabled=false"})
class SupportTicketConcurrencyTest {
    @Autowired EntityManager em;@Autowired SupportTicketService service;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager transactions;
    @Test void simultaneousRetriesCreateOneTicketAndConcurrentMessagesAreNotLost()throws Exception{
        var tx=new TransactionTemplate(transactions);long customer=tx.execute(s->{var u=new User();u.setFullName("Support race");u.setEmail(UUID.randomUUID()+"@race.test");u.setPasswordHash("test");u.setRole("CUSTOMER");em.persist(u);em.flush();return u.getId();});
        var actor=new SupportTicketService.Actor(customer,false);var create=new SupportTicketService.Create(null,null,null,"Race","Description",UUID.randomUUID().toString());var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Callable<Long> submit=()->{assertTrue(start.await(5,TimeUnit.SECONDS));return service.create(actor,create).ticket().id();};var one=pool.submit(submit);var two=pool.submit(submit);start.countDown();long id=one.get(15,TimeUnit.SECONDS);assertEquals(id,two.get(15,TimeUnit.SECONDS));assertEquals(1,db.queryForObject("select count(*) from support_tickets where customer_id=?",Long.class,customer));assertEquals(1,db.queryForObject("select count(*) from customer_notifications where support_ticket_id=?",Long.class,id));
            var replyStart=new CountDownLatch(1);var first=pool.submit(()->{assertTrue(replyStart.await(5,TimeUnit.SECONDS));return service.reply(actor,id,new SupportTicketService.Reply("First",false,UUID.randomUUID().toString()));});var second=pool.submit(()->{assertTrue(replyStart.await(5,TimeUnit.SECONDS));return service.reply(actor,id,new SupportTicketService.Reply("Second",false,UUID.randomUUID().toString()));});replyStart.countDown();first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);assertEquals(Set.of("First","Second"),new HashSet<>(service.detail(actor,id).messages().stream().map(SupportTicketService.Message::content).toList()));
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));tx.execute(s->{db.update("delete from customer_notifications where customer_id=?",customer);db.update("delete from support_tickets where customer_id=?",customer);db.update("delete from customers where customer_id=?",customer);return null;});}
    }
    @Test void concurrentCsatRetriesRecordOneImmutableEvaluation()throws Exception{
        var tx=new TransactionTemplate(transactions);long[] ids=tx.execute(s->{var buyer=new User();buyer.setFullName("CSAT race");buyer.setEmail(UUID.randomUUID()+"@csat.test");buyer.setPasswordHash("test");buyer.setRole("CUSTOMER");em.persist(buyer);em.flush();var ticket=service.create(new SupportTicketService.Actor(buyer.getId(),false),new SupportTicketService.Create(null,null,null,"CSAT","Description",UUID.randomUUID().toString())).ticket();var entity=em.find(com.htttdn.crm.entity.SupportTicket.class,ticket.id());entity.status="RESOLVED";entity.resolvedAt=java.time.Instant.now();return new long[]{buyer.getId(),ticket.id()};});
        var actor=new SupportTicketService.Actor(ids[0],false);var input=new SupportTicketService.Csat(5,"Helpful",UUID.randomUUID().toString());var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Callable<SupportTicketService.Detail> submit=()->{assertTrue(start.await(5,TimeUnit.SECONDS));return service.csat(actor,ids[1],input);};var first=pool.submit(submit);var second=pool.submit(submit);start.countDown();var one=first.get(15,TimeUnit.SECONDS);var two=second.get(15,TimeUnit.SECONDS);assertEquals(one.ticket().version(),two.ticket().version());assertEquals(5,two.ticket().csatRating());
            assertThrows(com.htttdn.crm.exception.ApiException.class,()->service.csat(actor,ids[1],new SupportTicketService.Csat(1,"Changed",UUID.randomUUID().toString())));
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));tx.execute(s->{db.update("delete from customer_notifications where customer_id=?",ids[0]);db.update("delete from support_tickets where id=?",ids[1]);db.update("delete from customers where customer_id=?",ids[0]);return null;});}
    }

}
