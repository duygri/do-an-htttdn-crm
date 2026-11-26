package com.htttdn.crm.service;

import com.htttdn.crm.entity.EmailOutbox;
import com.htttdn.crm.entity.EmailOutbox.Status;
import com.htttdn.crm.repository.EmailOutboxRepository;
import com.htttdn.crm.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;

@Service
public class EmailOutboxService {
    private final EmailOutboxRepository repository;
    private final TransactionalMail mail;
    private final AuditTrail audit;
    public EmailOutboxService(EmailOutboxRepository repository,TransactionalMail mail,AuditTrail audit) {
        this.repository=repository; this.mail=mail; this.audit=audit;
    }
    /** MUST join the business transaction; rollback must roll back the email too. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(String recipient,String subject,String body,String eventKey) {
        var row=new EmailOutbox(); row.recipient=recipient; row.subject=subject; row.body=body; row.eventKey=eventKey;
        if(!mail.available()) { row.status=Status.FAILED; row.reason="CONFIG_UNAVAILABLE"; }
        repository.save(row);
    }
    public record Claim(Long id,String token,String recipient,String subject,String body,String eventKey,int attempts) {}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<Claim> claim() {
        Instant now=Instant.now();
        repository.recover(now,Status.PROCESSING,Status.UNKNOWN);
        // Compare-and-set is the claim arbiter across JVMs. Losing workers never send.
        for(var row:repository.findByStatusAndNextAttemptAtLessThanEqualOrderById(Status.PENDING,now,PageRequest.of(0,20))) {
            String token=UUID.randomUUID().toString();
            if(repository.claim(row.id,token,now,now.plusSeconds(60),Status.PENDING,Status.PROCESSING)==1)
                return Optional.of(new Claim(row.id,token,row.recipient,row.subject,row.body,row.eventKey,row.attempts+1));
        }
        return Optional.empty();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(Claim claim,TransactionalMail.Outcome outcome,String reason) {
        Instant now=Instant.now();
        Status status=switch(outcome) {
            case ACCEPTED -> Status.ACCEPTED;
            case UNKNOWN -> Status.UNKNOWN;
            case FAILED -> Status.FAILED;
            case RETRYABLE -> claim.attempts()<5?Status.PENDING:Status.FAILED;
        };
        repository.finish(claim.id(),claim.token(),status,reason,
            now.plusSeconds(30L << Math.min(claim.attempts()-1,4)),now,Status.PROCESSING);
    }
    // Deliberately excludes subject, body, eventKey, raw recipient and transport configuration.
    public record Summary(Long id,Status status,int attempts,String recipientHint,String reason,
                          Instant createdAt,Instant updatedAt,Instant nextAttemptAt) {}
    private static String recipientHint(String recipient) {
        int at=recipient.lastIndexOf('@');
        return at>0?recipient.substring(0,1)+"***"+recipient.substring(at):"***";
    }
    @Transactional(readOnly=true)
    public Page<Summary> list(Status status,int page) {
        var pageable=PageRequest.of(Math.max(0,page),20,Sort.by(Sort.Direction.DESC,"id"));
        var rows=status==null?repository.findAll(pageable):repository.findByStatus(status,pageable);
        return rows.map(e->new Summary(e.id,e.status,e.attempts,recipientHint(e.recipient),e.reason,e.createdAt,e.updatedAt,e.nextAttemptAt));
    }
    @Transactional
    public void retry(Long id,boolean acknowledgeUnknown) {
        var row=repository.lockById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EMAIL_NOT_FOUND","Không tìm thấy email."));
        if(row.attempts>=5 || (row.status!=Status.FAILED && row.status!=Status.UNKNOWN)
            || (row.status==Status.UNKNOWN && !acknowledgeUnknown))
            throw new ApiException(HttpStatus.CONFLICT,"EMAIL_RETRY_BLOCKED","Không thể thử lại; UNKNOWN cần xác nhận nguy cơ gửi trùng, tối đa 5 lần gửi.");
        var before=Map.<String,Object>of("status",row.status.name(),"attempts",row.attempts);
        row.status=Status.PENDING; row.reason=null; row.nextAttemptAt=Instant.now(); row.updatedAt=Instant.now();
        row.claimToken=null; row.leaseUntil=null;
        audit.record("EMAIL_RETRY","EMAIL_OUTBOX",row.id,before,
            Map.of("status",row.status.name(),"attempts",row.attempts,"acknowledgeUnknown",acknowledgeUnknown));
    }
}
