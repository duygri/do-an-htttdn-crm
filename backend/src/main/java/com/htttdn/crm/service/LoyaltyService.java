package com.htttdn.crm.service;
import com.htttdn.crm.entity.*;import com.htttdn.crm.exception.ApiException;import com.htttdn.crm.repository.*;
import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;import java.time.Instant;import java.util.List;
/** Read-only archive. The former write entrypoints are deliberately retired. */
@Service
public class LoyaltyService {
 private final PointTransactionRepository transactions;
 public LoyaltyService(PointTransactionRepository transactions,StoreVoucherRepository vouchers,SettingsService settings,NotificationService notifications,AuditTrail audit){this.transactions=transactions;}
 public static ApiException retired(){return new ApiException(HttpStatus.GONE,"POINTS_RETIRED","Điểm thưởng đã được thay bằng hạng thành viên.");}
 public record Entry(Long id,String type,int points,String note,Long orderId,Instant createdAt){}
 public record Summary(long balance,List<Entry> history,Long customerId){}
 public record RedeemResult(String code,BigDecimal discountValue,int spentPoints,long balance,Instant expiresAt){}
 @Transactional(readOnly=true) public long balance(Long id){return transactions.balance(id);}
 @Transactional(readOnly=true) public Summary summary(User customer){return new Summary(balance(customer.getId()),transactions.findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId()).stream().map(r->new Entry(r.getId(),r.getTxnType(),r.getPoints(),r.getNote(),r.getOrderId(),r.getCreatedAt())).toList(),customer.getId());}
 public void awardForCompletedOrder(Order order){/* Retained compatibility entrypoint: no new points. */}
 public RedeemResult redeem(User customer){throw retired();}
 public RedeemResult redeem(User customer,String requestKey){throw retired();}
 public Summary adjust(User customer,int points,String note,AuditTrail.Actor actor){throw retired();}
}
