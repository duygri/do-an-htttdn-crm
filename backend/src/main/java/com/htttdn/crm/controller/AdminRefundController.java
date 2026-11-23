package com.htttdn.crm.controller;

import com.htttdn.crm.service.AdminRefundQuery;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

/** Admin oversight only: the refund ledger can be listed and summarised, never created or processed here. */
@RestController @RequestMapping("/api/admin/refunds")
public class AdminRefundController {
    private final AdminRefundQuery query;
    public AdminRefundController(AdminRefundQuery query){this.query=query;}
    @GetMapping public AdminRefundQuery.Page list(
        @RequestParam(required=false) String status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size){
        return query.list(status,from,to,q,page,size);
    }
    @GetMapping("/summary") public AdminRefundQuery.Summary summary(){return query.summary();}
}
