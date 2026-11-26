package com.htttdn.crm.service;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.*;

@Component
@ConditionalOnProperty(name="EMAIL_OUTBOX_WORKER_ENABLED",havingValue="true",matchIfMissing=true)
public class EmailOutboxWorker {
    private final EmailOutboxService outbox;
    private final TransactionalMail mail;
    public EmailOutboxWorker(EmailOutboxService outbox,TransactionalMail mail) { this.outbox=outbox; this.mail=mail; }
    @Scheduled(fixedDelayString="${EMAIL_OUTBOX_POLL_MS:5000}")
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void poll() {
        for(int i=0;i<20;i++) {
            var next=outbox.claim(); if(next.isEmpty())return;
            var claim=next.get();
            TransactionalMail.Outcome outcome; String reason;
            try {
                mail.send(claim.recipient(),claim.subject(),claim.body(),claim.eventKey());
                outcome=TransactionalMail.Outcome.ACCEPTED; reason="PROVIDER_ACCEPTED";
            } catch(TransactionalMail.MailFailure failure) {
                outcome=failure.outcome(); reason=failure.getMessage();
            } catch(RuntimeException failure) {
                outcome=TransactionalMail.Outcome.UNKNOWN; reason="UNEXPECTED_TRANSPORT_FAILURE";
            }
            outbox.finish(claim,outcome,reason);
        }
    }
}
