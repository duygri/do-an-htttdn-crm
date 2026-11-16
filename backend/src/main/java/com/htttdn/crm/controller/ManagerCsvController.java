package com.htttdn.crm.controller;

import com.htttdn.crm.service.CsvReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Manager-only CSV downloads; /api/manager/** is limited to MANAGER by AdminAccessGuard. */
@RestController
@RequestMapping("/api/manager")
public class ManagerCsvController {
    private final CsvReportService csv;

    public ManagerCsvController(CsvReportService csv) {
        this.csv = csv;
    }

    @GetMapping("/reports/revenue.csv")
    public ResponseEntity<String> revenue(@RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to,
                                          @RequestParam(defaultValue = "month") String groupBy) {
        return csv.revenueCsv(from, to, groupBy);
    }

    @GetMapping("/orders.csv")
    public ResponseEntity<String> orders(@RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "") String q,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        return csv.ordersCsv(status, q, from, to);
    }

    @GetMapping("/customers.csv")
    public ResponseEntity<String> customers(@RequestParam(defaultValue = "") String q) {
        return csv.customersCsv(q);
    }
}
