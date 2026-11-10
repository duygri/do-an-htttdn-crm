package com.htttdn.crm.controller;

import com.htttdn.crm.service.SalesInvoiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/manager")
public class SalesInvoiceController {
    private final SalesInvoiceService service;
    public SalesInvoiceController(SalesInvoiceService service){this.service=service;}
    @GetMapping("/invoices") public Page<SalesInvoiceService.View> list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(q,page,size);}
    @GetMapping("/invoices/{id}") public SalesInvoiceService.View get(@PathVariable Long id){return service.get(id);}
    @GetMapping("/orders/{id}/invoice") public ResponseEntity<SalesInvoiceService.View> forOrder(@PathVariable Long id){return ResponseEntity.of(service.forOrder(id));}
    @PostMapping("/orders/{id}/invoice") public SalesInvoiceService.View issue(@PathVariable Long id,@Valid @RequestBody(required=false) SalesInvoiceService.IssueRequest request){return service.issue(id,request);}
}
