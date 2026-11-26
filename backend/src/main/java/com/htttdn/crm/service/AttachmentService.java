package com.htttdn.crm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.Instant;
import java.util.*;

@Service
public class AttachmentService {
    public boolean enabled(){return cloud.configured();}
    public static final int MAX_BYTES=5*1024*1024,MAX_FILES=5;
    private final EntityManager em;private final ObjectMapper json;private final TransactionTemplate tx;private final AttachmentCloudinary cloud;
    public AttachmentService(EntityManager em,ObjectMapper json,org.springframework.transaction.PlatformTransactionManager manager,AttachmentCloudinary cloud){this.em=em;this.json=json;this.cloud=cloud;this.tx=new TransactionTemplate(manager);this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    /** Ledger shares the existing customer row; bound entries remain durable for private delivery authorization. */
    public record Metadata(String id,String publicId,String name,String contentType,String format,long size,long createdAt,String state,String targetType,Long targetId,int attempts,long retryAt){}
    public record View(String id,String name,String contentType,long size,String url){}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT",message);}
    private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"ATTACHMENT_NOT_FOUND","Không tìm thấy ảnh đính kèm.");}
    private List<Metadata> ledger(User u){try{return new ArrayList<>(json.readValue(u.pendingAttachments==null?"[]":u.pendingAttachments,new TypeReference<List<Metadata>>(){}));}catch(Exception e){throw new IllegalStateException("Invalid attachment metadata",e);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void save(User user,List<Metadata> items){user.pendingAttachments=encode(items);}
    private User owner(long id){var u=em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE);if(u==null||u.isLocked()||u.getDeletedAt()!=null||!Set.of("CUSTOMER","MANAGER").contains(u.getRole()))throw missing();return u;}
    public static String detect(byte[] b){
        if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "jpg";
        byte[] png={(byte)137,80,78,71,13,10,26,10};if(b.length>=8&&Arrays.equals(Arrays.copyOf(b,8),png))return "png";
        if(b.length>=12&&new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "webp";
        throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT","Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP hợp lệ.");
    }
    @Transactional(propagation=Propagation.NEVER)
    public View upload(long userId,MultipartFile file){
        cloud.available();if(file==null||file.isEmpty()||file.getSize()>MAX_BYTES)throw bad("Mỗi ảnh tối đa 5 MB.");
        byte[] bytes;try(var input=file.getInputStream()){bytes=input.readNBytes(MAX_BYTES+1);}catch(IOException e){throw bad("Không đọc được ảnh.");}if(bytes.length>MAX_BYTES)throw bad("Mỗi ảnh tối đa 5 MB.");
        String format=detect(bytes);AttachmentImageValidator.validate(bytes,format);String mime="image/"+(format.equals("jpg")?"jpeg":format);String original=Optional.ofNullable(file.getOriginalFilename()).orElse("attachment."+format).replaceAll("[\\\\/\\p{Cntrl}]","_");if(original.length()>150)original=original.substring(original.length()-150);
        String id=UUID.randomUUID().toString();var item=new Metadata(id,"crm_attachments/"+userId+"/"+id,original,mime,format,bytes.length,Instant.now().getEpochSecond(),"UPLOADING",null,null,0,0);
        tx.executeWithoutResult(status->{var u=owner(userId);var list=ledger(u);if(list.stream().filter(m->!"BOUND".equals(m.state())).count()>=20)throw bad("Có quá nhiều ảnh chờ gửi. Vui lòng thử lại sau.");list.add(item);save(u,list);});
        // Reservation survives provider failure, process crashes and finalization failures; cleanup can delete by deterministic public id.
        cloud.upload(item.publicId(),bytes,format);
        tx.executeWithoutResult(status->{var u=owner(userId);var items=ledger(u);replace(items,id,m->copy(m,"READY",null,null,m.attempts(),0));save(u,items);});
        return view(item);
    }
    private Metadata copy(Metadata m,String state,String type,Long target,int attempts,long retry){return new Metadata(m.id(),m.publicId(),m.name(),m.contentType(),m.format(),m.size(),m.createdAt(),state,type,target,attempts,retry);}
    private void replace(List<Metadata> items,String id,java.util.function.UnaryOperator<Metadata> f){for(int i=0;i<items.size();i++)if(items.get(i).id().equals(id)){items.set(i,f.apply(items.get(i)));return;}throw missing();}
    /** MUST join the transaction creating the target. Never accepts client metadata or provider IDs. */
    @Transactional(propagation=Propagation.MANDATORY)
    public String consume(long uploaderId,List<String> ids,String targetType,long targetId){
        if(ids==null||ids.isEmpty())return "[]";if(ids.size()>MAX_FILES||new HashSet<>(ids).size()!=ids.size())throw bad("Tối đa 5 ảnh, không được trùng lặp.");
        if(!Set.of("SUPPORT","SUPPORT_INTERNAL","RETURN").contains(targetType)||targetId<=0)throw bad("Đích đính kèm không hợp lệ.");
        var user=owner(uploaderId);var items=ledger(user);var result=new ArrayList<Metadata>();long now=Instant.now().getEpochSecond();
        for(String id:ids){var m=items.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(this::missing);if(!"READY".equals(m.state())||m.createdAt()+86400<=now)throw bad("Ảnh đã sử dụng hoặc hết hạn. Hãy tải lại ảnh.");var bound=copy(m,"BOUND",targetType,targetId,0,0);replace(items,id,old->bound);result.add(bound);}
        save(user,items);return encode(result);
    }
    @Transactional(readOnly=true)
    public List<View> returnAttachmentsForManager(long orderId){var order=em.find(Order.class,orderId);if(order==null)throw missing();return read(order.returnAttachments);}
    public List<View> read(String data){try{if(data==null||data.isBlank())return List.of();return json.readValue(data,new TypeReference<List<Metadata>>(){}).stream().map(this::view).toList();}catch(Exception e){throw new IllegalStateException("Invalid bound attachment metadata",e);}}
    private View view(Metadata m){return new View(m.id(),m.name(),m.contentType(),m.size(),"/api/attachments/"+m.id()+"/content");}
    @Transactional(readOnly=true)
    public Metadata authorized(User actor,String id){
        if(actor==null||actor.isLocked()||actor.getDeletedAt()!=null||!Set.of("CUSTOMER","MANAGER").contains(actor.getRole()))throw missing();
        // UUID lookup is bounded to rows containing this opaque id; values are parameterized, never SQL interpolated.
        var rows=em.createQuery("select u from User u where u.pendingAttachments like :id",User.class).setParameter("id","%\"id\":\""+uuid(id)+"\"%").setMaxResults(2).getResultList();
        for(var owner:rows)for(var m:ledger(owner))if(m.id().equals(id)){
            if(!"BOUND".equals(m.state())){if(owner.getId().equals(actor.getId())&&"READY".equals(m.state())&&m.createdAt()+86400>Instant.now().getEpochSecond())return m;throw missing();}
            if("RETURN".equals(m.targetType())){var order=em.find(Order.class,m.targetId());if(order!=null&&(actor.getRole().equals("MANAGER")||order.getCustomer().getId().equals(actor.getId())))return m;}
            else {var ticket=em.find(SupportTicket.class,m.targetId());if(ticket!=null&&(actor.getRole().equals("MANAGER")||!"SUPPORT_INTERNAL".equals(m.targetType())&&ticket.customerId.equals(actor.getId())))return m;}
        }throw missing();
    }
    private String uuid(String id){try{return UUID.fromString(id).toString();}catch(Exception e){throw missing();}}
    public void stream(Metadata m,OutputStream output){cloud.stream(m.publicId(),m.format(),output);}
    private long cleanupCursor;
    @Scheduled(fixedDelayString="${app.attachments.cleanup-ms:3600000}")
    public void cleanup(){
        // Keyset pagination prevents a few permanently failing assets from starving newer customers.
        long after=cleanupCursor;for(int page=0;page<10;page++){
            final long cursor=after;var ids=tx.execute(status->em.createQuery("select u.id from User u where u.id>:after and u.pendingAttachments is not null and u.pendingAttachments<>'[]' order by u.id",Long.class).setParameter("after",cursor).setMaxResults(100).getResultList());if(ids==null||ids.isEmpty()){cleanupCursor=0;return;}
            for(long id:ids){after=id;cleanupCursor=id;cleanupCustomer(id);}
        }
    }
    void cleanupCustomer(long id){
        long now=Instant.now().getEpochSecond();var claimed=tx.execute(status->{var u=em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE);if(u==null)return List.<Metadata>of();var items=ledger(u);var out=new ArrayList<Metadata>();for(var m:new ArrayList<>(items)){if(!"BOUND".equals(m.state())&&m.createdAt()+86400<=now&&m.attempts()<5&&m.retryAt()<=now&&out.size()<20){var claim=copy(m,"DELETING",null,null,m.attempts()+1,now+3600);replace(items,m.id(),old->claim);out.add(claim);}}save(u,items);return out;});
        if(claimed==null)return;for(var m:claimed){try{cloud.destroy(m.publicId());tx.executeWithoutResult(status->{var u=em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE);var items=ledger(u);items.removeIf(a->a.id().equals(m.id())&&"DELETING".equals(a.state()));save(u,items);});}catch(Exception ignored){/* Durable retry claim; at most five provider attempts. No URLs/secrets in logs. */}}
    }
}
