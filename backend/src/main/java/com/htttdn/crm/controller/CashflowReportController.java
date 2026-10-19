package com.htttdn.crm.controller;
import com.htttdn.crm.service.CashflowReportService;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestController @RequestMapping("/api/manager/reports")
public class CashflowReportController {
    private final CashflowReportService service;
    public CashflowReportController(CashflowReportService service){this.service=service;}
    @GetMapping("/cashflow") public CashflowReportService.View get(@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to,@RequestParam(defaultValue="day")String groupBy){return service.report(from,to,groupBy);}
}
