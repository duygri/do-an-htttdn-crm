package com.htttdn.crm.controller;

import com.htttdn.crm.service.CashflowReportService;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

/** Root-only finance summary for the admin dashboard; /api/admin/** is ADMIN-only. */
@RestController @RequestMapping("/api/admin/reports")
public class AdminFinanceReportController {
    private final CashflowReportService service;
    public AdminFinanceReportController(CashflowReportService service){this.service=service;}
    @GetMapping("/finance-summary") public Map<String,Object> summary(){
        var r=service.report(null,null,"year");
        return Map.of("collected",r.collected(),"purchaseCost",r.purchaseCost(),
            "paidToSuppliers",r.paidToSuppliers(),"difference",r.difference(),
            "collectedOrders",r.collectedOrders(),"updatedAt",r.updatedAt());
    }
    @GetMapping("/statistics") public CashflowReportService.View statistics(
        @RequestParam(required=false) Instant from,
        @RequestParam(required=false) Instant to,
        @RequestParam(defaultValue="month") String groupBy){
        return service.report(from,to,groupBy);
    }
}
