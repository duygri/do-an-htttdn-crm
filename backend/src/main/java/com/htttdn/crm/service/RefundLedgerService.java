package com.htttdn.crm.service;

import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import com.htttdn.crm.security.JwtTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Manual refund ledger. Money movement itself stays outside the system (bank transfer / cash handed back at the
 * counter); this service only records requests, reserves the refundable cap and settles or rejects them.
 */
@Service
public class RefundLedgerService extends AdminServiceSupport {
    private static final Set<String> METHODS=Set.of("BANK_TRANSFER","CASH","OTHER");
    private static final Set<String> FINAL=Set.of("COMPLETED","REJECTED");
    private final OrderRepository orders;
    private final OrderRefundRepository refunds;
    private final UserRepository users;
    private final JwtTokenService jwt;
    private final NotificationService notifications;
    private final ManagerOrderQuery query;

    public RefundLedgerService(OrderRepository orders,OrderRefundRepository refunds,UserRepository users,JwtTokenService jwt,NotificationService notifications,ManagerOrderQuery query){
        this.orders=orders;this.refunds=refunds;this.users=users;this.jwt=jwt;this.notifications=notifications;this.query=query;
    }

    /** Reserves part of the refundable cap. Replays of the same requestKey return the order detail unchanged. */
    @Transactional public ManagerOrderQuery.View create(String authorization, Long orderId, CreateInput input) {
        User manager=manager(authorization);
        Order order=orders.findForStatusUpdate(orderId).orElseThrow(()->notFound());
        boolean cancelled="CANCELLED".equals(order.getStatus());
        boolean returned=order.getReturnStatus()!=null&&Set.of("APPROVED","COMPLETED").contains(order.getReturnStatus());
        if(!cancelled&&!returned)throw new ApiException(HttpStatus.CONFLICT,"REFUND_NOT_ALLOWED","Chỉ hoàn tiền cho đơn đã hủy hoặc đổi trả đã được chấp thuận.");
        if(input==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND","Thiếu thông tin phiếu hoàn tiền.");
        String key=input.requestKey()==null||input.requestKey().isBlank()?"REFUND-"+UUID.randomUUID():input.requestKey().trim();
        if(key.length()>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND_KEY","Mã yêu cầu tối đa 100 ký tự.");
        if(refunds.findByOrderIdAndRequestKey(orderId,key).isPresent())return query.detail(orderId);
        BigDecimal amount=validAmount(input.amount());
        BigDecimal available=available(order);
        if(amount.signum()<=0||amount.compareTo(available)>0)
            throw new ApiException(HttpStatus.BAD_REQUEST,"REFUND_AMOUNT_INVALID","Số tiền hoàn phải lớn hơn 0 và không vượt quá "+money(available)+" còn lại.");
        String method=input.method()==null||input.method().isBlank()?"BANK_TRANSFER":input.method().trim().toUpperCase(Locale.ROOT);
        if(!METHODS.contains(method))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND_METHOD","Phương thức hoàn tiền không hợp lệ.");
        var before=snapshot(order);
        OrderRefund refund=new OrderRefund();
        refund.setOrderId(orderId);refund.setReasonType(cancelled?"CANCEL":"RETURN");refund.setAmount(amount);refund.setStatus("PENDING");refund.setRequestKey(key);
        refund.setMethod(method);refund.setBankReference(clean(input.bankReference(),120));refund.setNote(clean(input.note(),1000));
        refund.setEvidenceUrl(clean(input.evidenceUrl(),500));refund.setRequestedBy(manager.getId());
        refunds.saveAndFlush(refund);
        order.setRefundPendingAmount(nz(order.getRefundPendingAmount()).add(amount));
        order.setRefundStatus(nz(order.getRefundedAmount()).signum()==0?"PENDING":"PARTIAL");
        order.setUpdatedAt(Instant.now());orders.saveAndFlush(order);
        notifications.create(order.getCustomer(),"Đã tiếp nhận yêu cầu hoàn tiền","Đơn #"+order.getOrderCode()+": "+money(amount)+" đang chờ xử lý.","REFUND_REQUEST",orderId);
        audit("REFUND_REQUEST","ORDER",orderId,before,order);
        return query.detail(orderId);
    }

    /** Settles or rejects a pending refund. Replaying the same final status is idempotent; any other change is a conflict. */
    @Transactional public ManagerOrderQuery.View process(String authorization, Long orderId, Long refundId, ProcessInput input) {
        User manager=manager(authorization);
        Order order=orders.findForStatusUpdate(orderId).orElseThrow(()->notFound());
        OrderRefund refund=refunds.findById(refundId).filter(x->x.getOrderId().equals(orderId))
            .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"REFUND_NOT_FOUND","Không tìm thấy phiếu hoàn tiền."));
        String status=input==null||input.status()==null?"":input.status().trim().toUpperCase(Locale.ROOT);
        if(FINAL.contains(refund.getStatus())){
            if(status.equals(refund.getStatus()))return query.detail(orderId);
            throw new ApiException(HttpStatus.CONFLICT,"REFUND_ALREADY_PROCESSED","Phiếu hoàn tiền đã được xử lý.");
        }
        if(!FINAL.contains(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND_STATUS","Chỉ hoàn tất hoặc từ chối phiếu đang chờ.");
        String note=input.processingNote()==null?"":input.processingNote().trim();
        String bank=input.bankReference()==null?"":input.bankReference().trim();
        if(note.isEmpty()||note.length()>1000)throw new ApiException(HttpStatus.BAD_REQUEST,"REFUND_NOTE_REQUIRED","Ghi chú xử lý là bắt buộc, tối đa 1.000 ký tự.");
        boolean complete="COMPLETED".equals(status);
        if(complete&&(bank.isEmpty()||bank.length()>120))throw new ApiException(HttpStatus.BAD_REQUEST,"REFUND_REFERENCE_REQUIRED","Mã giao dịch ngân hàng là bắt buộc khi hoàn tất.");
        var before=snapshot(order);
        BigDecimal amount=nz(refund.getAmount());
        refund.setStatus(status);refund.setProcessingNote(note);refund.setProcessedBy(manager.getId());refund.setProcessedAt(Instant.now());
        if(complete)refund.setBankReference(bank);
        if(input.evidenceUrl()!=null)refund.setEvidenceUrl(clean(input.evidenceUrl(),500));
        refunds.saveAndFlush(refund);
        order.setRefundPendingAmount(nz(order.getRefundPendingAmount()).subtract(amount).max(BigDecimal.ZERO));
        if(complete){
            BigDecimal refunded=nz(order.getRefundedAmount()).add(amount);
            order.setRefundedAmount(refunded);
            order.setRefundStatus(refunded.compareTo(paidAmount(order))>=0?"FULL":"PARTIAL");
            notifications.create(order.getCustomer(),"Đã hoàn tiền","Đơn #"+order.getOrderCode()+" đã được hoàn "+money(amount)+" qua "+methodLabel(refund.getMethod())+".","REFUND_COMPLETED",orderId);
        } else {
            order.setRefundStatus(nz(order.getRefundedAmount()).signum()>0?"PARTIAL":nz(order.getRefundPendingAmount()).signum()>0?"PENDING":"NONE");
            notifications.create(order.getCustomer(),"Yêu cầu hoàn tiền bị từ chối","Đơn #"+order.getOrderCode()+": "+note,"REFUND_REJECTED",orderId);
        }
        order.setUpdatedAt(Instant.now());orders.saveAndFlush(order);
        audit(complete?"REFUND_COMPLETE":"REFUND_REJECT","ORDER",orderId,before,order);
        return query.detail(orderId);
    }

    /** Money actually received: PAYOS only counts once PAID; COD only counts cash the courier already collected. */
    private BigDecimal paidAmount(Order o){
        if("PAYOS".equals(o.getPaymentMethod()))return "PAID".equals(o.getPaymentStatus())?nz(o.getTotalAmount()):BigDecimal.ZERO;
        return nz(o.codCollectedAmount);
    }
    private BigDecimal available(Order o){return paidAmount(o).subtract(nz(o.getRefundedAmount())).subtract(nz(o.getRefundPendingAmount())).max(BigDecimal.ZERO);}
    private static BigDecimal nz(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    /** Rejects NaN/infinite/overflowing values and normalises to 2 decimals; non-positive stays invalid later. */
    private static BigDecimal validAmount(BigDecimal v){
        if(v==null)return BigDecimal.ZERO;
        try{return v.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException ex){throw new ApiException(HttpStatus.BAD_REQUEST,"REFUND_AMOUNT_INVALID","Số tiền hoàn phải có tối đa 2 chữ số thập phân.");}
    }
    private static String clean(String v,int max){
        String s=v==null?"":v.trim();
        if(s.length()>max)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND_TEXT","Nội dung hoàn tiền quá dài.");
        return s.isEmpty()?null:s;
    }
    private static String methodLabel(String method){
        if("CASH".equals(method))return "tiền mặt";
        if("OTHER".equals(method))return "phương thức khác";
        return "chuyển khoản";
    }
    private static String money(BigDecimal v){
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(v)+" ₫";
    }
    private User manager(String authorization){
        var claims=jwt.parse(authorization);
        User u=users.findById(claims.userId()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Phiên không hợp lệ."));
        if(!"MANAGER".equals(claims.role())||!"MANAGER".equals(u.getRole())||u.isLocked()||u.getDeletedAt()!=null)
            throw new ApiException(HttpStatus.FORBIDDEN,"MANAGER_REQUIRED","Chỉ quản lý được xử lý hoàn tiền.");
        return u;
    }
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng.");}

    public record CreateInput(BigDecimal amount,String method,String bankReference,String note,String evidenceUrl,String requestKey){}
    public record ProcessInput(String status,String processingNote,String bankReference,String evidenceUrl){}
}
