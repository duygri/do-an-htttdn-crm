package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Xác nhận đã thu tiền mặt khi giao hàng (COD). Không liên quan tới hoàn tiền. */
@Service
public class CodCollectionService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final AuditTrail audit;
    public CodCollectionService(OrderRepository orders,PaymentRepository payments,AuditTrail audit){this.orders=orders;this.payments=payments;this.audit=audit;}
    private AuditTrail.Actor actor(){
        var a=AuditTrail.actor();
        if(a==null||!"MANAGER".equals(a.role()))throw new ApiException(HttpStatus.FORBIDDEN,"MANAGER_REQUIRED","Chỉ Manager được xác nhận thu COD.");
        return a;
    }
    @Transactional public void confirm(Long id,String note){
        var a=actor();
        Order o=orders.findForStatusUpdate(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng."));
        String clean=note==null?"":note.trim();
        if(clean.isEmpty()||clean.length()>1000)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_NOTE","Vui lòng nhập ghi chú thu tiền từ 1 đến 1.000 ký tự.");
        if(!"COD".equals(o.getPaymentMethod())||!Set.of("DELIVERED","COMPLETED").contains(o.getStatus()))
            throw new ApiException(HttpStatus.CONFLICT,"COD_NOT_ELIGIBLE","Chỉ xác nhận thu COD cho đơn đã giao hoặc hoàn thành.");
        if(o.codCollectedAt!=null)return;
        o.codCollectedAmount=o.getTotalAmount();o.codCollectedAt=Instant.now();o.codCollectedBy=a.id();o.codCollectionNote=clean;
        o.setPaymentStatus("PAID");o.setUpdatedAt(Instant.now());
        payments.findByOrderId(id).ifPresent(p->{p.setStatus("PAID");p.setUpdatedAt(Instant.now());payments.save(p);});
        orders.save(o);
        audit.record("COD_COLLECTED","ORDER",id,Map.of(),Map.of("amount",o.codCollectedAmount));
    }
}
