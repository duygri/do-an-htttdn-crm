package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.AuditLogRepository;
import com.htttdn.crm.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${CRM_COMPLETION_QA_URL:jdbc:h2:mem:support;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}","spring.datasource.driver-class-name=${CRM_COMPLETION_QA_DRIVER:org.h2.Driver}","spring.datasource.username=${CRM_COMPLETION_QA_USER:sa}","spring.datasource.password=${CRM_COMPLETION_QA_PASSWORD:}","spring.jpa.database-platform=${CRM_COMPLETION_QA_DIALECT:org.hibernate.dialect.H2Dialect}","spring.jpa.hibernate.ddl-auto=${CRM_COMPLETION_QA_DDL:create-drop}","crm.care-reminders.enabled=false"})
@AutoConfigureMockMvc @Transactional
class SupportTicketIntegrationTest {
    @Autowired AttachmentService attachments;@Autowired com.fasterxml.jackson.databind.ObjectMapper json;@Autowired SupportTicketService service;@Autowired EntityManager em;@Autowired ManagerCareInbox inbox;@Autowired AuditLogRepository logs;@Autowired MockMvc mvc;@Autowired JwtTokenService jwt;@Autowired PlatformTransactionManager transactions;
    User user(String role){var u=new User();u.setFullName("Support "+role);u.setEmail(UUID.randomUUID()+"@support.test");u.setPasswordHash("SECRET_HASH");u.setRole(role);em.persist(u);em.flush();return u;}
    String key(){return UUID.randomUUID().toString();}String auth(User u){return "Bearer "+jwt.issue(u.getId(),u.getRole());}
    SupportTicketService.Actor actor(User u){return new SupportTicketService.Actor(u.getId(),"MANAGER".equals(u.getRole()));}
    SupportTicketService.Create input(Long c,Long o,Long a){return new SupportTicketService.Create(c,o,a,"Help order","PRIVATE_DESCRIPTION",key());}
    @Test void customerCreatesOwnTicketAndManagerHandlesWaitingCustomerReplyThenResolution(){
        var buyer=user("CUSTOMER");var manager=user("MANAGER");var create=input(null,null,null);var ticket=service.create(actor(buyer),create).ticket();
        assertEquals(ticket.id(),service.create(actor(buyer),create).ticket().id());assertEquals(1,service.list(actor(buyer),"Help",null,null,null,0,1).totalElements());assertNull(ticket.customerEmail());assertNull(ticket.assigneeId());
        final var initial=ticket;assertThrows(ApiException.class,()->service.update(actor(manager),initial.id(),new SupportTicketService.Update(manager.getId(),"RESOLVED",initial.version(),"Solved",key())));
        ticket=service.update(actor(manager),ticket.id(),new SupportTicketService.Update(manager.getId(),"IN_PROGRESS",ticket.version(),"",null)).ticket();
        assertEquals(1,inbox.unread(manager.getId(),Instant.now()));assertEquals(ticket.id(),inbox.notifications(manager.getId(),0,10,Instant.now()).page().content().getFirst().ticketId());
        var hidden=new SupportTicketService.Reply("PRIVATE_INTERNAL",true,key());service.reply(actor(manager),ticket.id(),hidden);assertEquals(0,service.detail(actor(buyer),ticket.id()).messages().size());
        assertEquals(1,service.detail(actor(manager),ticket.id()).messages().size());
        ticket=service.detail(actor(manager),ticket.id()).ticket();var wait=new SupportTicketService.Update(manager.getId(),"WAITING_CUSTOMER",ticket.version(),"Please clarify",key());service.update(actor(manager),ticket.id(),wait);service.update(actor(manager),ticket.id(),wait);
        var reply=new SupportTicketService.Reply("Customer answer",false,key());service.reply(actor(buyer),ticket.id(),reply);service.reply(actor(buyer),ticket.id(),reply);
        var detail=service.detail(actor(manager),ticket.id());assertEquals("IN_PROGRESS",detail.ticket().status());assertEquals(3,detail.messages().size());
        var resolve=new SupportTicketService.Update(manager.getId(),"RESOLVED",detail.ticket().version(),"Solution for customer",key());service.update(actor(manager),ticket.id(),resolve);service.update(actor(manager),ticket.id(),resolve);assertEquals(0,inbox.unread(manager.getId(),Instant.now()));
        assertThrows(ApiException.class,()->service.reply(actor(buyer),detail.ticket().id(),new SupportTicketService.Reply("Another",false,key())));
        for(var log:logs.findAll())assertFalse((log.getBeforeData()+log.getAfterData()).contains("PRIVATE_"));
    }
    @Test void assigneeDefaultsToActingManagerAndKeepsExistingOwner(){
        var buyer=user("CUSTOMER");var one=user("MANAGER");var two=user("MANAGER");
        var fresh=service.create(actor(buyer),input(null,null,null)).ticket();assertNull(fresh.assigneeId());
        fresh=service.update(actor(one),fresh.id(),new SupportTicketService.Update(null,"IN_PROGRESS",fresh.version(),"",null)).ticket();
        assertEquals(one.getId(),fresh.assigneeId());
        fresh=service.update(actor(two),fresh.id(),new SupportTicketService.Update(null,"WAITING_CUSTOMER",fresh.version(),"Please clarify",key())).ticket();
        assertEquals(one.getId(),fresh.assigneeId());
        var second=service.create(actor(buyer),input(null,null,null)).ticket();
        service.reply(actor(two),second.id(),new SupportTicketService.Reply("First response",false,key()));
        assertEquals(two.getId(),service.detail(actor(two),second.id()).ticket().assigneeId());
    }
    @Test void managerCreatesForLockedCustomerButMustUseOwnedOrderAndActiveAssignee(){
        long initialLogs=logs.count();
        var buyer=user("CUSTOMER");buyer.setLocked(true);var other=user("CUSTOMER");var manager=user("MANAGER");var inactive=user("MANAGER");inactive.setLocked(true);
        var order=new Order();order.setCustomer(buyer);order.setOrderCode(554411L);order.setTotalAmount(BigDecimal.TEN);order.setPaymentMethod("COD");order.setPaymentStatus("COD");order.setStatus("DELIVERED");em.persist(order);em.flush();
        assertThrows(ApiException.class,()->service.create(actor(manager),input(other.getId(),order.getId(),manager.getId())));
        assertThrows(ApiException.class,()->service.create(actor(manager),input(buyer.getId(),order.getId(),inactive.getId())));
        var t=service.create(actor(manager),input(buyer.getId(),order.getId(),manager.getId())).ticket();assertEquals(buyer.getId(),t.customerId());assertEquals(554411L,t.orderCode());
        service.update(actor(manager),t.id(),new SupportTicketService.Update(manager.getId(),"IN_PROGRESS",t.version(),"",null));em.refresh(order);assertEquals("DELIVERED",order.getStatus());assertEquals("COD",order.getPaymentStatus());assertEquals(0,BigDecimal.TEN.compareTo(order.getTotalAmount()));
        assertEquals(initialLogs+2,logs.count());
    }
    @Test void versionConflictValidationAndFiltersApplyBeforePagination(){
        var buyer=user("CUSTOMER");var manager=user("MANAGER");for(int i=0;i<3;i++)service.create(actor(buyer),new SupportTicketService.Create(null,null,null,"Find "+i,"Description",key()));
        var first=service.list(actor(manager),"Find",null,buyer.getId(),null,1,1);assertEquals(3,first.totalElements());assertEquals(3,first.totalPages());assertEquals(1,first.content().size());
        var t=first.content().getFirst();service.update(actor(manager),t.id(),new SupportTicketService.Update(manager.getId(),"IN_PROGRESS",t.version(),"",null));
        assertEquals("SUPPORT_CHANGED",assertThrows(ApiException.class,()->service.update(actor(manager),t.id(),new SupportTicketService.Update(manager.getId(),"WAITING_CUSTOMER",t.version(),"Clarify",key()))).code());
        assertEquals(2,service.list(actor(manager),"Find","NEW",buyer.getId(),null,0,1).totalElements());assertEquals(1,service.list(actor(manager),"Find","IN_PROGRESS",buyer.getId(),manager.getId(),0,1).totalElements());
        assertThrows(ApiException.class,()->service.create(actor(buyer),new SupportTicketService.Create(null,null,null," ","x",key())));assertThrows(ApiException.class,()->service.create(actor(buyer),new SupportTicketService.Create(null,null,null,"x","y",null)));
        assertThrows(ApiException.class,()->service.reply(actor(buyer),t.id(),new SupportTicketService.Reply("Secret",true,key())));
    }
    @Test void personalAccessAndPrivateNotesAreProtectedThroughApi()throws Exception{
        var buyer=user("CUSTOMER");var other=user("CUSTOMER");var manager=user("MANAGER");var admin=user("ADMIN");var t=service.create(actor(buyer),input(null,null,null)).ticket();service.reply(actor(manager),t.id(),new SupportTicketService.Reply("INTERNAL_SECRET",true,key()));
        mvc.perform(get("/api/support-tickets")).andExpect(status().isUnauthorized());mvc.perform(get("/api/support-tickets/"+t.id()).header("Authorization",auth(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/support-tickets?customerId="+buyer.getId()).header("Authorization",auth(other))).andExpect(jsonPath("$.totalElements").value(0));
        String body=mvc.perform(get("/api/support-tickets/"+t.id()).header("Authorization",auth(buyer))).andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(0)).andReturn().getResponse().getContentAsString();assertFalse(body.contains("INTERNAL_SECRET"));assertFalse(body.contains(manager.getEmail()));assertFalse(body.contains("SECRET_HASH"));
        for(User wrong:List.of(buyer,admin))mvc.perform(get("/api/manager/support-tickets").header("Authorization",auth(wrong))).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/support-tickets?status=BAD").header("Authorization",auth(manager))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-logs?type=SUPPORT_TICKET").header("Authorization",auth(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/support-tickets").header("Authorization",auth(buyer)).contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":"+other.getId()+",\"title\":\"Fake\",\"description\":\"Fake\",\"requestId\":\""+key()+"\"}")).andExpect(status().isBadRequest());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED) void rollbackRemovesTicketNotificationAndAuditTogether(){
        var tx=new TransactionTemplate(transactions);long[] before=tx.execute(s->new long[]{em.createQuery("select count(t) from SupportTicket t",Long.class).getSingleResult(),em.createQuery("select count(n) from CustomerNotification n",Long.class).getSingleResult(),logs.count()});
        tx.execute(s->{var buyer=user("CUSTOMER");var manager=user("MANAGER");service.create(actor(manager),input(buyer.getId(),null,manager.getId()));s.setRollbackOnly();return null;});
        tx.execute(s->{assertEquals(before[0],em.createQuery("select count(t) from SupportTicket t",Long.class).getSingleResult());assertEquals(before[1],em.createQuery("select count(n) from CustomerNotification n",Long.class).getSingleResult());assertEquals(before[2],logs.count());return null;});
    }
    @Test void slaUsesCreationTimeAndOnlyFirstPublicManagerResponse(){
        var buyer=user("CUSTOMER");var manager=user("MANAGER");var t=service.create(actor(buyer),input(null,null,null)).ticket();
        assertEquals("NORMAL",t.priority());assertEquals(t.createdAt().plusSeconds(86400),t.dueAt());
        var urgent=service.update(actor(manager),t.id(),new SupportTicketService.Update(null,"NEW",t.version(),"",null,"URGENT","Customer cannot use product")).ticket();
        assertEquals(t.createdAt().plusSeconds(14400),urgent.dueAt());assertTrue(urgent.version()>t.version());
        assertThrows(ApiException.class,()->service.update(actor(manager),t.id(),new SupportTicketService.Update(null,"NEW",t.version(),"",null,"LOW","Stale")));
        assertThrows(ApiException.class,()->service.update(actor(manager),t.id(),new SupportTicketService.Update(null,"NEW",urgent.version(),"",null,"LOW"," ")));
        service.reply(actor(manager),t.id(),new SupportTicketService.Reply("Internal",true,key()));
        service.reply(actor(buyer),t.id(),new SupportTicketService.Reply("Customer",false,key()));assertNull(service.detail(actor(manager),t.id()).ticket().firstResponseAt());
        var response=service.reply(actor(manager),t.id(),new SupportTicketService.Reply("Public",false,key())).ticket().firstResponseAt();assertNotNull(response);
        service.reply(actor(manager),t.id(),new SupportTicketService.Reply("Second",false,key()));assertEquals(response,service.detail(actor(manager),t.id()).ticket().firstResponseAt());
        var current=service.detail(actor(manager),t.id()).ticket();var low=service.update(actor(manager),t.id(),new SupportTicketService.Update(null,"NEW",current.version(),"",null,"LOW","Lower impact")).ticket();assertEquals(t.createdAt().plusSeconds(172800),low.dueAt());
    }
    @Test void legacySlaUnknownFiltersBeforePagingAndDueSort(){
        var buyer=user("CUSTOMER");var manager=user("MANAGER");var one=service.create(actor(buyer),input(null,null,null)).ticket();var two=service.create(actor(buyer),input(null,null,null)).ticket();var legacy=service.create(actor(buyer),input(null,null,null)).ticket();
        var e=em.find(SupportTicket.class,one.id());e.createdAt=Instant.now().minusSeconds(100000);e.dueAt=e.createdAt.plusSeconds(86400);
        var old=em.find(SupportTicket.class,legacy.id());old.priority=null;old.dueAt=null;em.flush();
        assertEquals("UNKNOWN",service.detail(actor(manager),legacy.id()).ticket().slaStatus());
        var filtered=service.list(actor(manager),"",null,null,null,0,1,"NORMAL",true,true,"due");assertEquals(1,filtered.totalElements());assertEquals(one.id(),filtered.content().getFirst().id());
        assertEquals(2,service.list(actor(manager),"",null,null,null,0,1,null,true,false,"due").totalElements());
        service.reply(actor(manager),one.id(),new SupportTicketService.Reply("Late",false,key()));assertEquals("BREACHED",service.detail(actor(manager),one.id()).ticket().slaStatus());
        assertEquals(0,service.list(actor(manager),"",null,null,null,0,10,null,false,true,null).totalElements());
    }
    @Test void csatOwnedResolvedOnceAndReportUsesResolvedDateExcludingUnrecorded()throws Exception{
        var buyer=user("CUSTOMER");var other=user("CUSTOMER");var manager=user("MANAGER");var t=service.create(actor(buyer),input(null,null,null)).ticket();
        var rating=new SupportTicketService.Csat(4,"Helpful",key());assertThrows(ApiException.class,()->service.csat(actor(buyer),t.id(),rating));
        var active=service.update(actor(manager),t.id(),new SupportTicketService.Update(null,"IN_PROGRESS",t.version(),"",null)).ticket();
        service.update(actor(manager),t.id(),new SupportTicketService.Update(null,"RESOLVED",active.version(),"Resolved",key()));
        assertThrows(ApiException.class,()->service.csat(actor(other),t.id(),rating));assertThrows(ApiException.class,()->service.csat(actor(manager),t.id(),rating));
        var rated=service.csat(actor(buyer),t.id(),rating);assertEquals(4,rated.ticket().csatRating());assertEquals(rated.ticket().version(),service.csat(actor(buyer),t.id(),rating).ticket().version());
        assertThrows(ApiException.class,()->service.csat(actor(buyer),t.id(),new SupportTicketService.Csat(5,"Changed",key())));
        var unrecorded=service.create(actor(buyer),input(null,null,null)).ticket();var raw=em.find(SupportTicket.class,unrecorded.id());raw.status="RESOLVED";raw.resolvedAt=Instant.parse("2026-10-31T17:00:00Z");raw.dueAt=null;raw.priority=null;
        em.find(SupportTicket.class,t.id()).resolvedAt=Instant.parse("2026-10-31T17:00:00Z");em.flush();
        var report=service.report(actor(manager),java.time.LocalDate.parse("2026-11-01"),java.time.LocalDate.parse("2026-11-01"));assertEquals(2L,report.get("resolvedCount"));assertEquals(1L,report.get("recordedCount"));assertEquals(1L,report.get("unrecordedCount"));assertEquals(4.0,report.get("averageRating"));assertEquals(0.5,report.get("responseRatio"));assertEquals(1L,report.get("slaKnownCount"));
        assertEquals(0L,service.report(actor(manager),java.time.LocalDate.parse("2026-10-31"),java.time.LocalDate.parse("2026-10-31")).get("resolvedCount"));
        mvc.perform(post("/api/support-tickets/"+t.id()+"/csat").header("Authorization",auth(buyer)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":6,\"requestId\":\""+key()+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/manager/support-tickets/report?from=2026-11-01&to=2026-11-01").header("Authorization",auth(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(4.0));
    }

    String upload(User owner)throws Exception{
        String id=key();var metadata=new AttachmentService.Metadata(id,"private/"+id,"photo.png","image/png","png",100,Instant.now().getEpochSecond(),"READY",null,null,0,0);
        owner.pendingAttachments=json.writeValueAsString(List.of(metadata));em.flush();return id;
    }
    @Test void supportAttachmentsAreBoundOnceAndInternalMetadataIsNotExposed()throws Exception{
        var buyer=user("CUSTOMER");var manager=user("MANAGER");String image=upload(buyer);
        var input=new SupportTicketService.Create(null,null,null,"With image","Description",key(),List.of(image));
        var created=service.create(actor(buyer),input);assertEquals(image,created.attachments().getFirst().id());assertEquals(created.ticket().id(),service.create(actor(buyer),input).ticket().id());
        assertEquals(created.ticket().id(),attachments.authorized(buyer,image).targetId());
        String privateImage=upload(manager);var note=new SupportTicketService.Reply("Internal note",true,key(),List.of(privateImage));
        var result=service.reply(actor(manager),created.ticket().id(),note);assertEquals(privateImage,result.messages().getFirst().attachments().getFirst().id());assertEquals(1,service.reply(actor(manager),created.ticket().id(),note).messages().size());
        assertTrue(service.detail(actor(buyer),created.ticket().id()).messages().isEmpty());assertEquals("SUPPORT_INTERNAL",attachments.authorized(manager,privateImage).targetType());
        assertThrows(ApiException.class,()->attachments.authorized(buyer,privateImage));
    }
    @Test void foreignAttachmentIdsCannotBeBoundToSupport()throws Exception{
        var buyer=user("CUSTOMER");var other=user("CUSTOMER");String foreign=upload(other);
        assertThrows(ApiException.class,()->service.create(actor(buyer),new SupportTicketService.Create(null,null,null,"Foreign","Description",key(),List.of(foreign))));
    }

    @Test void reportExcludesDeletedOrNonCustomerOwnersButIncludesLockedCustomers(){
        var buyer=user("CUSTOMER");var deleted=user("CUSTOMER");var changedRole=user("CUSTOMER");var manager=user("MANAGER");
        for(var owner:List.of(buyer,deleted,changedRole)){var t=service.create(actor(owner),input(null,null,null)).ticket();var entity=em.find(SupportTicket.class,t.id());entity.status="RESOLVED";entity.resolvedAt=Instant.parse("2026-11-01T01:00:00Z");entity.csatRating=4;}
        buyer.setLocked(true);deleted.setDeletedAt(Instant.now());changedRole.setRole("ADMIN");em.flush();
        var result=service.report(actor(manager),java.time.LocalDate.parse("2026-11-01"),java.time.LocalDate.parse("2026-11-01"));assertEquals(1L,result.get("resolvedCount"));assertEquals(4.0,result.get("averageRating"));assertEquals(1.0,result.get("responseRatio"));assertEquals("FRACTION_0_TO_1",result.get("ratioUnit"));
    }

}
