package com.htttdn.crm.controller;

import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.service.CodCollectionService;
import com.htttdn.crm.service.ManagerOrderQuery;
import com.htttdn.crm.service.PaymentReconciliationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Xác nhận thu COD và đối soát PayOS. Hoàn tiền đã được gỡ khỏi hệ thống. */
@RestController @RequestMapping("/api/manager")
public class ManagerOrderFinanceController {
    private final CodCollectionService cod;private final ManagerOrderQuery query;private final OrderRepository orders;private final PaymentReconciliationService reconciliation;
    public ManagerOrderFinanceController(CodCollectionService cod,ManagerOrderQuery query,OrderRepository orders,PaymentReconciliationService reconciliation){this.cod=cod;this.query=query;this.orders=orders;this.reconciliation=reconciliation;}
    public record Note(@Size(max=1000) String note){}
    @PatchMapping("/orders/{id}/cod-collection") public ManagerOrderQuery.View confirmCod(@PathVariable Long id,@Valid @RequestBody Note input){cod.confirm(id,input==null?null:input.note());return query.detail(id);}
    @PostMapping("/orders/{id}/payment/reconcile") @Transactional public ManagerOrderQuery.View reconcile(@PathVariable Long id){
        var o=orders.findForStatusUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng."));
        if(!"PAYOS".equals(o.getPaymentMethod()))throw new ApiException(HttpStatus.CONFLICT,"PAYOS_REQUIRED","Đơn không dùng PayOS.");
        reconciliation.reconcile(o,false);orders.saveAndFlush(o);return query.detail(id);
    }
}
