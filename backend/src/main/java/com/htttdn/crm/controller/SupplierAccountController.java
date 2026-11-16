package com.htttdn.crm.controller;

import com.htttdn.crm.entity.Supplier;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.SupplierRepository;
import com.htttdn.crm.service.AuditTrail;
import com.htttdn.crm.service.SupplierAccountService;
import com.htttdn.crm.util.Csv;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager/suppliers")
public class SupplierAccountController {
    private final SupplierAccountService accounts;
    private final SupplierRepository suppliers;
    private final AuditTrail audit;

    public SupplierAccountController(SupplierAccountService accounts, SupplierRepository suppliers, AuditTrail audit) {
        this.accounts = accounts;
        this.suppliers = suppliers;
        this.audit = audit;
    }

    public record StatusRequest(Boolean active) {}

    @GetMapping("/{id}")
    public SupplierAccountService.SupplierDetail detail(@PathVariable Long id) {
        return accounts.detail(id);
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public Supplier status(@PathVariable Long id, @RequestBody StatusRequest request,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        if (request == null || request.active() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUPPLIER_STATUS", "Trạng thái nhà cung cấp không hợp lệ.");
        }
        if (actor == null) throw new IllegalStateException("Verified audit actor required");
        Supplier supplier = suppliers.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp."));
        boolean target = request.active();
        if (supplier.active != target) {
            supplier.active = target;
            suppliers.save(supplier);
            audit.recordAs(actor, "SUPPLIER_STATUS", "SUPPLIER", supplier.id,
                Map.of("active", !target), Map.of("active", target));
        }
        return supplier;
    }

    @GetMapping("/{id}/payments")
    public Page<SupplierAccountService.PaymentView> payments(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return accounts.payments(id, page, size);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierAccountService.PaymentView pay(@PathVariable Long id,
            @Valid @RequestBody SupplierAccountService.PaymentRequest request,
            @RequestAttribute(AuditTrail.ACTOR_ATTRIBUTE) AuditTrail.Actor actor) {
        return accounts.pay(id, request, actor);
    }

    @GetMapping("/{id}/reports/summary.csv")
    public ResponseEntity<String> payablesCsv(@PathVariable Long id) {
        var receipts = accounts.allReceipts(id);
        List<String> rows = new ArrayList<>();
        rows.add(Csv.line("receiptId", "invoiceNumber", "invoiceDate", "totalAmount", "paidAmount", "outstanding"));
        receipts.forEach(row -> rows.add(Csv.line(row.id(), row.invoiceNumber(), row.invoiceDate(),
            row.totalAmount(), row.paidAmount(), row.outstanding())));
        return Csv.response("cong-no-ncc.csv", rows);
    }
}
