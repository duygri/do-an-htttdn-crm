package com.htttdn.crm.controller;

import com.htttdn.crm.entity.EmailOutbox.Status;
import com.htttdn.crm.service.EmailOutboxService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/emails")
public class AdminEmailController {
    private final EmailOutboxService outbox;
    public AdminEmailController(EmailOutboxService outbox) { this.outbox=outbox; }
    @GetMapping
    public Page<EmailOutboxService.Summary> list(@RequestParam(required=false) Status status,@RequestParam(defaultValue="0") int page) {
        return outbox.list(status,page);
    }
    public record Retry(boolean acknowledgeUnknown) {}
    @PostMapping("/{id}/retry")
    public void retry(@PathVariable Long id,@RequestBody Retry request) { outbox.retry(id,request.acknowledgeUnknown()); }
}
