package com.htttdn.crm.service;

import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.PointTransaction;
import com.htttdn.crm.entity.StoreVoucher;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.PointTransactionRepository;
import com.htttdn.crm.repository.StoreVoucherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Điểm thưởng: cộng khi đơn hoàn tất, đổi điểm lấy voucher, điều chỉnh thủ công. */
@Service
public class LoyaltyService {
    private final PointTransactionRepository transactions;
    private final StoreVoucherRepository vouchers;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final AuditTrail audit;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;

    private void lockCustomer(User customer) {
        User current = em.find(User.class, customer.getId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (current == null || current.getDeletedAt() != null || !"CUSTOMER".equals(current.getRole()))
            throw new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng.");
    }

    public LoyaltyService(PointTransactionRepository transactions, StoreVoucherRepository vouchers,
            SettingsService settings, NotificationService notifications, AuditTrail audit) {
        this.transactions = transactions;
        this.vouchers = vouchers;
        this.settings = settings;
        this.notifications = notifications;
        this.audit = audit;
    }

    public record Entry(Long id, String type, int points, String note, Long orderId, Instant createdAt) {}

    public record Summary(long balance, List<Entry> history, Long customerId) {}

    public record RedeemResult(String code, BigDecimal discountValue, int spentPoints, long balance, Instant expiresAt) {}

    @Transactional(readOnly = true)
    public long balance(Long customerId) {
        return transactions.balance(customerId);
    }

    @Transactional(readOnly = true)
    public Summary summary(User customer) {
        if (customer == null) throw new IllegalArgumentException("Customer required");
        return new Summary(balance(customer.getId()), transactions
            .findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId()).stream()
            .map(row -> new Entry(row.getId(), row.getTxnType(), row.getPoints(), row.getNote(),
                row.getOrderId(), row.getCreatedAt()))
            .toList(), customer.getId());
    }

    @Transactional
    public void awardForCompletedOrder(Order order) {
        if (order == null || order.getCustomer() == null || order.getId() == null || !"COMPLETED".equals(order.getStatus())) return;
        lockCustomer(order.getCustomer());
        int rate = settings.getInt("loyalty.pointsPer10000");
        int earned = order.getTotalAmount()
            .divide(BigDecimal.valueOf(10000), 0, RoundingMode.FLOOR)
            .intValueExact() * rate;
        if (earned <= 0) return;
        if (transactions.existsByOrderIdAndTxnType(order.getId(), "EARN")) return;
        var txn = new PointTransaction();
        txn.setCustomer(order.getCustomer());
        txn.setOrderId(order.getId());
        txn.setTxnType("EARN");
        txn.setPoints(earned);
        txn.setNote("Tích điểm đơn #" + order.getOrderCode());
        transactions.save(txn);
    }

    @Transactional
    public RedeemResult redeem(User customer) {
        return redeem(customer, UUID.randomUUID().toString());
    }

    @Transactional
    public RedeemResult redeem(User customer, String requestKey) {
        try { if (requestKey == null || !UUID.fromString(requestKey).toString().equals(requestKey)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_KEY", "Mã yêu cầu đổi điểm không hợp lệ."); }
        lockCustomer(customer);
        var previous = transactions.findByCustomerIdAndRequestKey(customer.getId(), requestKey);
        if (previous.isPresent()) {
            try { return mapper.readValue(previous.get().getRedemptionResult(), RedeemResult.class); }
            catch (Exception e) { throw new IllegalStateException("Cannot read redemption result", e); }
        }
        int cost = settings.getInt("loyalty.redeemPoints");
        int value = settings.getInt("loyalty.redeemValue");
        long balance = balance(customer.getId());
        if (balance < cost) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_POINTS", "Bạn không đủ điểm để đổi voucher.");
        }
        String code = nextCode();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(30));
        var voucher = new StoreVoucher();
        voucher.setCode(code);
        voucher.setOwnerCustomerId(customer.getId());
        voucher.setDiscountType("FIXED_AMOUNT");
        voucher.setDiscountValue(BigDecimal.valueOf(value));
        voucher.setMinOrderAmount(BigDecimal.ZERO);
        voucher.setDescription("Đổi " + cost + " điểm thưởng");
        voucher.setUsageLimit(1);
        voucher.setPerCustomerLimit(1);
        voucher.setActive(true);
        voucher.setStartsAt(Instant.now());
        voucher.setExpiresAt(expiresAt);
        vouchers.saveAndFlush(voucher);

        var txn = new PointTransaction();
        txn.setCustomer(customer);
        txn.setTxnType("REDEEM");
        txn.setPoints(-cost);
        txn.setNote("Đổi điểm lấy voucher " + code);
        RedeemResult result = new RedeemResult(code, voucher.getDiscountValue(), cost, balance - cost, expiresAt);
        txn.setRequestKey(requestKey);
        try { txn.setRedemptionResult(mapper.writeValueAsString(result)); }
        catch (Exception e) { throw new IllegalStateException("Cannot save redemption result", e); }
        transactions.saveAndFlush(txn);
        notifications.createVoucher(customer, "Đổi điểm thành công",
            "Bạn đã đổi " + cost + " điểm, mã " + code + ".");
        return result;
    }

    @Transactional
    public Summary adjust(User customer, int points, String note, AuditTrail.Actor actor) {
        if (actor == null || customer == null || points == 0
                || note == null || note.isBlank() || note.trim().length() > 500
                || !"CUSTOMER".equals(customer.getRole())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POINTS",
                "Thông tin điều chỉnh điểm không hợp lệ.");
        }
        lockCustomer(customer);
        var txn = new PointTransaction();
        txn.setCustomer(customer);
        txn.setTxnType("ADJUST");
        txn.setPoints(points);
        txn.setNote(note.trim());
        txn.setCreatedBy(actor.id());
        transactions.saveAndFlush(txn);
        audit.recordAs(actor, "POINTS_ADJUST", "POINT_TRANSACTION", txn.getId(),
            java.util.Map.of(), java.util.Map.of("customerId", customer.getId(), "points", points));
        return summary(customer);
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "DIEM-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
            if (!vouchers.existsByCodeIgnoreCase(code)) return code;
        }
        throw new ApiException(HttpStatus.CONFLICT, "VOUCHER_CODE_EXHAUSTED",
            "Không tạo được mã voucher, vui lòng thử lại.");
    }
}
