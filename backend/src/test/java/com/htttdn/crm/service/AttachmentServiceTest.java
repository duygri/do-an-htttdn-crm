package com.htttdn.crm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.mock.web.MockMultipartFile;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({AttachmentService.class,AttachmentServiceTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AttachmentServiceTest {
    @TestConfiguration static class Config {@Bean ObjectMapper attachmentJson(){return new ObjectMapper().findAndRegisterModules();}}
    @Autowired AttachmentService service;@Autowired EntityManager em;@Autowired ObjectMapper json;@Autowired PlatformTransactionManager manager;
    @MockBean AttachmentCloudinary cloud;
    TransactionTemplate tx(){return new TransactionTemplate(manager);}
    long user(String role){return tx().execute(s->{var u=new User();u.setEmail(UUID.randomUUID()+"@attachment.test");u.setFullName("Attachments");u.setPasswordHash("hash");u.setRole(role);em.persist(u);em.flush();return u.getId();});}
    User user(long id){return tx().execute(s->em.find(User.class,id));}
    List<AttachmentService.Metadata> metadata(long user){return tx().execute(s->{try{return json.readValue(em.find(User.class,user).pendingAttachments,new TypeReference<List<AttachmentService.Metadata>>(){});}catch(Exception e){throw new RuntimeException(e);}});}
    void seed(long user,List<AttachmentService.Metadata> data){tx().executeWithoutResult(s->{try{em.find(User.class,user).pendingAttachments=json.writeValueAsString(data);}catch(Exception e){throw new RuntimeException(e);}});}
    AttachmentService.Metadata pending(String state,long created){String id=UUID.randomUUID().toString();return new AttachmentService.Metadata(id,"crm_attachments/test/"+id,"image.png","image/png","png",70,created,state,null,null,0,0);}
    @Test void uploadReservesBeforeHttpAndFinalizesWithoutAnHttpTransaction()throws Exception{
        long id=user("CUSTOMER");doAnswer(invocation->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals("UPLOADING",metadata(id).getFirst().state());return null;}).when(cloud).upload(anyString(),any(),anyString());
        var view=service.upload(id,new MockMultipartFile("file","evil.jpg","text/html",AttachmentValidationTest.image("png")));assertEquals("image/png",view.contentType());assertFalse(view.url().contains("cloudinary"));assertEquals("READY",metadata(id).getFirst().state());
    }
    @Test void uploadFailureRetainsDurableReservation()throws Exception{
        long id=user("CUSTOMER");doThrow(new RuntimeException("provider")).when(cloud).upload(anyString(),any(),anyString());assertThrows(RuntimeException.class,()->service.upload(id,new MockMultipartFile("file","a.png","image/png",AttachmentValidationTest.image("png"))));assertEquals("UPLOADING",metadata(id).getFirst().state());
    }
    @Test void consumeRequiresTransactionAndIsAtomicWithTargetRollback(){
        long id=user("CUSTOMER"),other=user("CUSTOMER");var p=pending("READY",Instant.now().getEpochSecond());seed(id,List.of(p));
        assertThrows(IllegalTransactionStateException.class,()->service.consume(id,List.of(p.id()),"RETURN",1));
        assertThrows(ApiException.class,()->tx().execute(s->service.consume(other,List.of(p.id()),"RETURN",1)));
        tx().executeWithoutResult(s->{String bound=service.consume(id,List.of(p.id()),"RETURN",1);assertEquals(p.id(),service.read(bound).getFirst().id());s.setRollbackOnly();});assertEquals("READY",metadata(id).getFirst().state());
        tx().execute(s->service.consume(id,List.of(p.id()),"RETURN",1));assertEquals("BOUND",metadata(id).getFirst().state());
        assertThrows(ApiException.class,()->tx().execute(s->service.consume(id,List.of(p.id()),"RETURN",1)));
    }
    @Test void rejectsDuplicatesOversizeExpiredAndNonImagesBeforeProvider()throws Exception{
        long id=user("CUSTOMER");var p=pending("READY",Instant.now().getEpochSecond()-86401);seed(id,List.of(p));
        assertThrows(ApiException.class,()->tx().execute(s->service.consume(id,List.of(p.id()),"RETURN",1)));
        assertThrows(ApiException.class,()->tx().execute(s->service.consume(id,List.of(p.id(),p.id()),"RETURN",1)));
        assertThrows(ApiException.class,()->tx().execute(s->service.consume(id,Collections.nCopies(6,p.id()),"RETURN",1)));
        assertThrows(ApiException.class,()->service.upload(id,new MockMultipartFile("file","a.png","image/png",new byte[AttachmentService.MAX_BYTES+1])));
        assertThrows(ApiException.class,()->service.upload(id,new MockMultipartFile("file","a.png","image/png","<script>bad</script>".getBytes())));verify(cloud,never()).upload(anyString(),any(),anyString());
    }
    @Test void internalAttachmentsStayInvisibleToCustomerAndForeignActors(){
        long buyer=user("CUSTOMER"),other=user("CUSTOMER"),staff=user("MANAGER");
        long ticket=tx().execute(s->{var t=new SupportTicket();t.customerId=buyer;t.createdBy=buyer;t.requestKey=UUID.randomUUID().toString();t.title="Help";t.description="Help";t.createdAt=Instant.now();t.updatedAt=t.createdAt;em.persist(t);em.flush();return t.id;});
        var p=pending("READY",Instant.now().getEpochSecond());seed(staff,List.of(p));tx().execute(s->service.consume(staff,List.of(p.id()),"SUPPORT_INTERNAL",ticket));
        assertThrows(ApiException.class,()->service.authorized(user(buyer),p.id()));assertThrows(ApiException.class,()->service.authorized(user(other),p.id()));assertNotNull(service.authorized(user(staff),p.id()));
        var publicImage=pending("READY",Instant.now().getEpochSecond());seed(buyer,List.of(publicImage));tx().execute(s->service.consume(buyer,List.of(publicImage.id()),"SUPPORT",ticket));assertNotNull(service.authorized(user(buyer),publicImage.id()));assertThrows(ApiException.class,()->service.authorized(user(other),publicImage.id()));
    }
    @Test void cleanupDeletesOnlyExpiredUnboundAssetsOutsideTransactionAndRetriesAreBounded(){
        long id=user("CUSTOMER");long old=Instant.now().getEpochSecond()-86401;var expired=pending("READY",old);var fresh=pending("READY",old+86400);var bound=pending("BOUND",old);seed(id,List.of(expired,fresh,bound));
        doAnswer(invocation->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return null;}).when(cloud).destroy(anyString());service.cleanupCustomer(id);assertEquals(2,metadata(id).size());verify(cloud).destroy(expired.publicId());
        var fail=pending("UPLOADING",old);seed(id,List.of(fail));doThrow(new RuntimeException("failed")).when(cloud).destroy(fail.publicId());
        for(int i=0;i<7;i++){service.cleanupCustomer(id);var m=metadata(id).getFirst();seed(id,List.of(new AttachmentService.Metadata(m.id(),m.publicId(),m.name(),m.contentType(),m.format(),m.size(),m.createdAt(),m.state(),null,null,m.attempts(),0)));}
        verify(cloud,times(5)).destroy(fail.publicId());assertEquals(5,metadata(id).getFirst().attempts());
    }
}
