package com.htttdn.crm.controller;

import com.htttdn.crm.service.ManagerOrderQuery;
import com.htttdn.crm.service.RefundLedgerService;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/manager/orders")
public class ManagerRefundController {
    private final RefundLedgerService service;
    private final ManagerOrderQuery query;
    public ManagerRefundController(RefundLedgerService service,ManagerOrderQuery query){this.service=service;this.query=query;}
    @PostMapping("/{id}/refunds") public ManagerOrderQuery.View create(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable Long id,@RequestBody RefundLedgerService.CreateInput input){
        service.create(authorization,id,input);return query.detail(id);
    }
    @PatchMapping("/{id}/refunds/{refundId}") public ManagerOrderQuery.View process(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable Long id,@PathVariable Long refundId,@RequestBody RefundLedgerService.ProcessInput input){
        service.process(authorization,id,refundId,input);return query.detail(id);
    }
}
