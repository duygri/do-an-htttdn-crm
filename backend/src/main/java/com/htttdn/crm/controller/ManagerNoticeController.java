package com.htttdn.crm.controller;

import com.htttdn.crm.service.AuditTrail;
import com.htttdn.crm.service.InternalNoticeService;
import com.htttdn.crm.service.InternalNoticeService.Inbox;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/manager/notices")
public class ManagerNoticeController {
    private final InternalNoticeService notices;

    public ManagerNoticeController(InternalNoticeService notices) {
        this.notices = notices;
    }

    @GetMapping
    public Inbox list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                      @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        return notices.inbox(actor.id(), page, size);
    }

    @GetMapping("/summary")
    public Map<String, Long> summary(@RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        return Map.of("unreadCount", notices.unread(actor.id()));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id, @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        notices.markRead(id, actor.id());
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        notices.markAllRead(actor.id());
    }
}
