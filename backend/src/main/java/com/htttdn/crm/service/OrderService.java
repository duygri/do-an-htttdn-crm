package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class OrderService {
    private static final Set<String> CUSTOMER_CANCELLABLE_STATUSES = Set.of("PENDING_PAYMENT", "PENDING", "CONFIRMED", "PREPARING");
    private final OrderRepository orders;
    private final ProductRepository products;
    private final CartItemRepository carts;
    private final PaymentRepository payments;
    private final PaymentWebhookEventRepository webhookEvents;
    private final StoreVoucherRepository vouchers;
    private final NotificationService notifications;
    private final PayosService payos;

    public OrderService(OrderRepository orders, ProductRepository products, CartItemRepository carts,
                        PaymentRepository payments, PaymentWebhookEventRepository webhookEvents,
                        StoreVoucherRepository vouchers, NotificationService notifications, PayosService payos) {
        this.orders = orders; this.products = products; this.carts = carts; this.payments = payments;
        this.webhookEvents = webhookEvents; this.vouchers = vouchers; this.notifications = notifications; this.payos = payos;
    }

    @Transactional
    public Checkout create(User customer, CheckoutRequest request) {
        if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "Vui lòng nhập địa chỉ giao hàng.");
        String method = request.paymentMethod() == null ? "PAYOS" : request.paymentMethod().toUpperCase(Locale.ROOT);
        if (!Set.of("PAYOS", "COD").contains(method)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD", "Phương thức thanh toán không hợp lệ.");
        List<RequestedItem> requested = request.items() != null && !request.items().isEmpty() ? request.items() : carts.findByCustomerIdOrderByIdAsc(customer.getId()).stream().map(i -> new RequestedItem(i.getProduct().getId(), i.getQuantity(), i.getSize(), i.getColor())).toList();
        if (requested.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "Giỏ hàng đang trống.");
        Instant now = Instant.now(); Order order = new Order(); order.setCustomer(customer); order.setOrderCode(nextCode()); order.setDeliveryAddress(request.deliveryAddress().trim()); order.setPaymentMethod(method); order.setCreatedAt(now); order.setUpdatedAt(now); order.setStatus("COD".equals(method) ? "CONFIRMED" : "PENDING_PAYMENT"); order.setPaymentStatus("COD".equals(method) ? "COD" : "PENDING"); order.setExpiresAt("COD".equals(method) ? null : now.plus(Duration.ofMinutes(15)));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (RequestedItem line : requested) {
            if (line == null || line.productId() == null || line.quantity() < 1) throw invalidCart();
            Product product = products.findLockedById(line.productId()).filter(Product::isActive).orElseThrow(this::invalidCart); validateVariant(product, line.size(), line.color());
            if (line.quantity() > product.getStock()) throw new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK", "Sản phẩm " + product.getName() + " không đủ số lượng.");
            product.setStock(product.getStock() - line.quantity()); product.setUpdatedAt(now); products.save(product);
            OrderItem item = new OrderItem(); item.setOrder(order); item.setProduct(product); item.setQuantity(line.quantity()); item.setSize(clean(line.size())); item.setColor(clean(line.color())); item.setUnitPrice(CartService.price(product)); order.getItems().add(item);
            subtotal = subtotal.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        BigDecimal discount = BigDecimal.ZERO;
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) { StoreVoucher voucher = findVoucher(request.voucherCode()); validateVoucher(voucher, subtotal); discount = voucherDiscount(voucher, subtotal); voucher.setUsedCount(voucher.getUsedCount() + 1); vouchers.save(voucher); order.setVoucherCode(voucher.getCode()); }
        order.setDiscountAmount(discount); order.setTotalAmount(subtotal.subtract(discount).max(BigDecimal.ZERO)); orders.save(order); carts.deleteByCustomerId(customer.getId());
        notifications.create(customer, "Đặt hàng thành công", "Đơn hàng #" + order.getOrderCode() + " đã được ghi nhận.", "ORDER_CREATED", order.getId());
        Payment payment = new Payment(); payment.setOrder(order); payment.setAmount(order.getTotalAmount()); payment.setPaymentMethod(method); payment.setExpiresAt(order.getExpiresAt());
        if ("COD".equals(method)) { payment.setStatus("COD"); payment.setResponseCode("COD"); payments.save(payment); return new Checkout(view(order), null, null); }
        PayosService.Link link = payos.createPaymentLink(order); payment.setPaymentLinkId(link.paymentLinkId()); payment.setCheckoutUrl(link.checkoutUrl()); payments.save(payment); return new Checkout(view(order), link.checkoutUrl(), link.paymentLinkId());
    }

    @Transactional(readOnly = true) public Page<OrderView> history(User customer, int page, int size) { if (page < 0 || size < 1 || size > 50) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ."); return orders.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), PageRequest.of(page, size)).map(this::view); }
    @Transactional(readOnly = true) public OrderView detail(User customer, Long id) { return view(owned(customer, id)); }

    @Transactional public OrderView cancel(User customer, Long id, String reason) {
        Order order = owned(customer, id); if (!CUSTOMER_CANCELLABLE_STATUSES.contains(order.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "ORDER_CANNOT_CANCEL", "Đơn hàng đã chuyển sang giao hàng hoặc đã hoàn tất nên không thể hủy.");
        order.setStatus("CANCELLED"); order.setPaymentStatus("CANCELLED"); order.setCancelReason(clean(reason) == null ? "Khách hàng yêu cầu hủy đơn." : clean(reason)); payments.findByOrderId(order.getId()).ifPresent(payment -> { payment.setStatus("CANCELLED"); payments.save(payment); }); releaseStock(order); order.setUpdatedAt(Instant.now()); orders.save(order); notifications.create(customer, "Đã hủy đơn hàng", "Đơn hàng #" + order.getOrderCode() + " đã được hủy.", "ORDER_CANCELLED", order.getId()); return view(order);
    }

    @Transactional public OrderView requestReturn(User customer, Long id, String reason) {
        Order order = owned(customer, id); if (!Set.of("DELIVERED", "COMPLETED").contains(order.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "RETURN_NOT_ALLOWED", "Chỉ có thể yêu cầu đổi trả sau khi đã nhận hàng."); if (!"NONE".equals(order.getReturnStatus())) throw new ApiException(HttpStatus.CONFLICT, "RETURN_ALREADY_REQUESTED", "Đơn hàng đã có yêu cầu đổi trả.");
        order.setReturnStatus("REQUESTED"); order.setReturnReason(reason.trim()); order.setReturnRequestedAt(Instant.now()); order.setUpdatedAt(Instant.now()); orders.save(order); notifications.create(customer, "Đã tiếp nhận yêu cầu đổi trả", "Yêu cầu đổi trả đơn #" + order.getOrderCode() + " đang chờ xử lý.", "RETURN_REQUESTED", order.getId()); return view(order);
    }

    @Transactional public boolean webhook(JsonNode payload, String signature) {
        JsonNode data = payload == null ? null : payload.path("data");
        if (data == null || data.isMissingNode()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Webhook không có dữ liệu thanh toán.");
        }
        if (!payos.verifyWebhook(data, signature)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Chữ ký webhook không hợp lệ.");
        }

        long code = data.path("orderCode").asLong(0);
        String providerCode = data.path("code").asText(payload.path("code").asText(""));
        boolean success = payload.path("success").asBoolean(false);
        Order order = orders.findByOrderCodeForUpdate(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Không tìm thấy đơn hàng tương ứng."));
        BigDecimal amount = BigDecimal.valueOf(data.path("amount").asDouble(-1));
        if (code == 0 || amount.compareTo(order.getTotalAmount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Thông tin đơn hàng hoặc số tiền không khớp.");
        }

        String gatewayReference = firstText(data, "reference", "transactionDateTime", "paymentLinkId");
        String eventType = success && "00".equals(providerCode) ? "SUCCESS" : "FAILED";
        String eventKey = data.path("paymentLinkId").asText("") + ":" + code + ":"
                + data.path("transactionDateTime").asText("") + ":" + providerCode;
        if (webhookEvents.existsByEventKey(eventKey)
                || webhookEvents.existsByOrderIdAndWebhookTypeAndGatewayReference(order.getId(), eventType, gatewayReference == null ? eventKey : gatewayReference)) {
            return true;
        }

        Payment payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Không tìm thấy giao dịch."));
        payment.setGatewayTxnRef(data.path("transactionDateTime").asText(null));
        payment.setGatewayTransactionNo(data.path("reference").asText(data.path("transactionId").asText(null)));
        payment.setResponseCode(providerCode);
        if (success && "00".equals(providerCode)) {
            payment.setStatus("PAID");
            payment.setPaidAt(Instant.now());
            order.setPaymentStatus("PAID");
            order.setStatus("CONFIRMED");
            notifications.create(order.getCustomer(), "Thanh toán thành công", "Đơn hàng #" + order.getOrderCode() + " đã được xác nhận.", "PAYMENT_CONFIRMED", order.getId());
        } else {
            String desc = data.path("desc").asText("").toLowerCase(Locale.ROOT);
            payment.setStatus(desc.contains("expire") ? "EXPIRED" : "CANCELLED");
            order.setPaymentStatus(payment.getStatus());
            order.setStatus("CANCELLED");
            releaseStock(order);
            notifications.create(order.getCustomer(), "Thanh toán chưa hoàn tất", "Đơn hàng #" + order.getOrderCode() + " đã được hủy do thanh toán không thành công.", "PAYMENT_FAILED", order.getId());
        }

        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setEventKey(eventKey);
        event.setOrderCode(code);
        event.setOrderId(order.getId());
        event.setWebhookType(eventType);
        event.setGatewayReference(gatewayReference == null ? eventKey : gatewayReference);
        webhookEvents.save(event);

        payment.setUpdatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        payments.save(payment);
        orders.save(order);
        return false;
    }

    @Scheduled(fixedDelayString = "${app.payment-cleanup-ms:300000}") @Transactional public void cleanupExpired() { for (Order order : orders.findExpiredPending(Instant.now())) { if (!"PENDING_PAYMENT".equals(order.getStatus())) continue; payments.findByOrderId(order.getId()).ifPresent(p -> { p.setStatus("EXPIRED"); p.setUpdatedAt(Instant.now()); payments.save(p); }); order.setPaymentStatus("EXPIRED"); order.setStatus("CANCELLED"); releaseStock(order); order.setUpdatedAt(Instant.now()); orders.save(order); notifications.create(order.getCustomer(), "Đơn hàng hết hạn thanh toán", "Đơn hàng #" + order.getOrderCode() + " đã được hủy.", "ORDER_EXPIRED", order.getId()); } }

    private Order owned(User customer, Long id) { return orders.findByIdAndCustomerId(id, customer.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.")); }
    private StoreVoucher findVoucher(String code) { return vouchers.findByCodeIgnoreCase(code.trim()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VOUCHER_NOT_FOUND", "Không tìm thấy mã giảm giá.")); }
    private void validateVoucher(StoreVoucher voucher, BigDecimal amount) { Instant now = Instant.now(); if (!voucher.isActive() || (voucher.getStartsAt() != null && now.isBefore(voucher.getStartsAt())) || (voucher.getExpiresAt() != null && !now.isBefore(voucher.getExpiresAt())) || (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit())) throw new ApiException(HttpStatus.BAD_REQUEST, "VOUCHER_INVALID", "Mã giảm giá không còn hiệu lực."); if (amount.compareTo(voucher.getMinOrderAmount()) < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "VOUCHER_MINIMUM_NOT_MET", "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này."); }
    private BigDecimal voucherDiscount(StoreVoucher voucher, BigDecimal amount) { return "FIXED_AMOUNT".equalsIgnoreCase(voucher.getDiscountType()) ? voucher.getDiscountValue().min(amount) : amount.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).min(amount); }
    private void validateVariant(Product product, String size, String color) { if (!hasOption(product.getSizes(), size) || !hasOption(product.getColors(), color)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VARIANT", "Size hoặc màu sản phẩm không hợp lệ."); }
    private boolean hasOption(String csv, String value) { return value == null || value.isBlank() || Arrays.stream(String.valueOf(csv == null ? "" : csv).split(",")).map(String::trim).anyMatch(value.trim()::equalsIgnoreCase); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String firstText(JsonNode data, String... fields) { for (String field : fields) { String value = data.path(field).asText(""); if (!value.isBlank()) return value; } return null; }
    private void releaseStock(Order order) { if (order.isStockReleased()) return; for (OrderItem item : order.getItems()) products.findById(item.getProduct().getId()).ifPresent(p -> { p.setStock(p.getStock() + item.getQuantity()); p.setUpdatedAt(Instant.now()); products.save(p); }); order.setStockReleased(true); }
    private ApiException invalidCart() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "Sản phẩm trong giỏ không còn hợp lệ."); }
    private long nextCode() { long code = 100000000000L + (System.currentTimeMillis() % 89999999999L); while (orders.findByOrderCode(code).isPresent()) code++; return code; }

    public OrderView view(Order o) { List<OrderLineView> lines = o.getItems().stream().map(i -> new OrderLineView(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getImageUrl(), i.getQuantity(), i.getUnitPrice(), i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())), i.getSize(), i.getColor())).toList(); return new OrderView(o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentStatus(), o.getPaymentMethod(), o.getDeliveryAddress(), o.getTrackingCode(), o.getCancelReason(), o.getReturnStatus(), o.getReturnReason(), o.getVoucherCode(), o.getDiscountAmount(), o.getTotalAmount(), o.getCreatedAt(), o.getUpdatedAt(), lines); }

    public record RequestedItem(Long productId, int quantity, String size, String color) {}
    public record CheckoutRequest(String deliveryAddress, String paymentMethod, String voucherCode, List<RequestedItem> items) {}
    public record Checkout(OrderView order, String paymentUrl, String paymentLinkId) {}
    public record OrderView(Long id, Long orderCode, String status, String paymentStatus, String paymentMethod, String deliveryAddress, String trackingCode, String cancelReason, String returnStatus, String returnReason, String voucherCode, BigDecimal discountAmount, BigDecimal totalAmount, Instant createdAt, Instant updatedAt, List<OrderLineView> items) {}
    public record OrderLineView(Long productId, String name, String imageUrl, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, String size, String color) {}
}
