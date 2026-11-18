package com.htttdn.crm.controller;

import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.service.ManagerOrderQuery;
import com.htttdn.crm.service.PaymentCollectionService;
import com.htttdn.crm.service.PaymentReconciliationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager/orders")
public class ManagerOrderPaymentController {
    private final PaymentCollectionService collection;
    private final ManagerOrderQuery query;
    private final OrderRepository orders;
    private final PaymentReconciliationService reconciliation;

    public ManagerOrderPaymentController(PaymentCollectionService collection, ManagerOrderQuery query,
                                         OrderRepository orders, PaymentReconciliationService reconciliation) {
        this.collection = collection;
        this.query = query;
        this.orders = orders;
        this.reconciliation = reconciliation;
    }

    public record Note(@Size(max = 1000) String note) {}

    @PatchMapping("/{id}/cod-collection")
    public ManagerOrderQuery.View cod(@PathVariable Long id, @Valid @RequestBody Note input) {
        collection.confirmCod(id, input == null ? null : input.note());
        return query.detail(id);
    }

    @PostMapping("/{id}/payment/reconcile")
    @Transactional
    public ManagerOrderQuery.View reconcile(@PathVariable Long id) {
        var order = orders.findForStatusUpdate(id).orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng."));
        if (!"PAYOS".equals(order.getPaymentMethod())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYOS_REQUIRED", "Đơn không dùng PayOS.");
        }
        reconciliation.reconcile(order, false);
        orders.saveAndFlush(order);
        return query.detail(id);
    }
}
