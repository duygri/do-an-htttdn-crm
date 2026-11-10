package com.htttdn.crm.controller;

import com.htttdn.crm.entity.SupplierReceipt;
import com.htttdn.crm.service.SupplierReceiptService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/manager/supplier-receipts")
public class SupplierReceiptController {
    private final SupplierReceiptService service;
    public SupplierReceiptController(SupplierReceiptService service){this.service=service;}
    @GetMapping public Page<SupplierReceipt> list(@RequestParam(required=false)Long supplierId,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(supplierId,from,to,page,size);}
    @GetMapping("/{id}") public SupplierReceipt get(@PathVariable Long id){return service.get(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public SupplierReceipt create(@Valid @RequestBody SupplierReceiptService.CreateRequest request){return service.create(request);}
}
