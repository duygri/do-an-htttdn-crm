package com.htttdn.crm.controller;

import com.htttdn.crm.service.*;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api/manager")
public class ManagerRefundController {
    private final RefundService refunds;private final ManagerOrderQuery query;private final OrderRepository orders;private final PaymentReconciliationService reconciliation;
    public ManagerRefundController(RefundService refunds,ManagerOrderQuery query,OrderRepository orders,PaymentReconciliationService reconciliation){this.refunds=refunds;this.query=query;this.orders=orders;this.reconciliation=reconciliation;}
    @GetMapping("/refunds") public org.springframework.data.domain.Page<RefundService.View> list(@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return refunds.list(status,q,page,size);}
    @GetMapping("/orders/{id}/refunds") public java.util.List<RefundService.View> ledger(@PathVariable Long id){query.detail(id);return refunds.forOrder(id);}
    @PostMapping("/orders/{id}/refunds") public RefundService.View create(@PathVariable Long id,@RequestBody RefundService.Input input){return refunds.create(id,input);}
    @PostMapping("/refunds/{id}/complete") public RefundService.View complete(@PathVariable Long id,@RequestBody RefundService.Process input){return refunds.process(id,input,true);}
    @PostMapping("/refunds/{id}/reject") public RefundService.View reject(@PathVariable Long id,@RequestBody RefundService.Process input){return refunds.process(id,input,false);}
    public record Note(String note){}
    @PatchMapping("/orders/{id}/cod-collection") public ManagerOrderQuery.View cod(@PathVariable Long id,@RequestBody Note input){refunds.confirmCod(id,input==null?null:input.note());return query.detail(id);}
    @PostMapping("/orders/{id}/payment/reconcile") @Transactional public ManagerOrderQuery.View reconcile(@PathVariable Long id){
        var o=orders.findForStatusUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng."));
        if(!"PAYOS".equals(o.getPaymentMethod()))throw new ApiException(HttpStatus.CONFLICT,"PAYOS_REQUIRED","Đơn không dùng PayOS.");
        reconciliation.reconcile(o,false);orders.saveAndFlush(o);return query.detail(id);
    }
}
