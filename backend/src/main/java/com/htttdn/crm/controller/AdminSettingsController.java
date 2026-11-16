package com.htttdn.crm.controller;

import com.htttdn.crm.service.AuditTrail;
import com.htttdn.crm.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {
    private final SettingsService settings;

    public AdminSettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public Map<String,String> get() {
        return settings.all();
    }

    @PutMapping
    public Map<String,String> update(@RequestBody Map<String,String> values,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        return settings.update(values, actor);
    }
}
