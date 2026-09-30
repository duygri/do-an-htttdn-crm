package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.htttdn.crm.entity.*;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Set<String> CUSTOMER_CANCELLABLE_STATUSES = Set.of("PENDING_PAYMENT", "PENDING", "CONFIRMED", "PREPARING");
    private final OrderRepository orders;
    private final ProductRepository products;
    private final CartItemRepository carts;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentWebhookEventRepository webhookEvents;
    private final VoucherService vouchers;
    private final NotificationService notifications;
    private final PayosService payos;

    public OrderService(OrderRepository orders, ProductRepository products, CartItemRepository carts,
                        PaymentRepository payments, PaymentAttemptRepository attempts, PaymentWebhookEventRepository webhookEvents,
                        VoucherService vouchers, NotificationService notifications, PayosService payos) {
        this.orders = orders; this.products = products; this.carts = carts; this.payments = payments;
        this.attempts = attempts;
        this.webhookEvents = webhookEvents; this.vouchers = vouchers; this.notifications = notifications; this.payos = payos;
    }

    @Transactional
    public Checkout create(User customer, CheckoutRequest request) {
        if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "Vui lòng nhập địa chỉ giao hàng.");
        String method = request.paymentMethod() == null ? "PAYOS" : request.paymentMethod().toUpperCase(Locale.ROOT);
        if (!Set.of("PAYOS", "COD").contains(method)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD", "Phương thức thanh toán không hợp lệ.");
        List<RequestedItem> requested = request.items() != null ? request.items() : carts.findByCustomerIdOrderByIdAsc(customer.getId()).stream().map(i -> new RequestedItem(i.getProduct().getId(), i.getQuantity(), i.getSize(), i.getColor())).toList();
        if (requested.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "Giỏ hàng đang trống.");
        Map<CartLineKey, RequestedItem> grouped = new LinkedHashMap<>();
        for (RequestedItem line : requested) {
            if (line == null || line.productId() == null || line.quantity() < 1) throw invalidCart();
            var key = new CartLineKey(line.productId(), clean(line.size()), clean(line.color()));
            var previous = grouped.get(key);
            long quantity = (long) line.quantity() + (previous == null ? 0 : previous.quantity());
            if (quantity > Integer.MAX_VALUE) throw invalidCart();
            grouped.put(key, new RequestedItem(key.productId(), (int) quantity, key.size(), key.color()));
        }
        requested = new ArrayList<>(grouped.values());
        Instant now = Instant.now(); Order order = new Order(); order.setCustomer(customer); order.setOrderCode(nextCode()); order.setDeliveryAddress(request.deliveryAddress().trim()); order.setPaymentMethod(method); order.setCreatedAt(now); order.setUpdatedAt(now); order.setStatus("PENDING"); order.setPaymentStatus("COD".equals(method) ? "COD" : "PENDING"); order.setExpiresAt("COD".equals(method) ? null : now.plus(Duration.ofMinutes(15)));
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
        StoreVoucher applied = null;
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) { applied = vouchers.reserve(request.voucherCode(), subtotal, order); discount = order.getDiscountAmount(); }
        order.setDiscountAmount(discount); order.setTotalAmount(subtotal.subtract(discount).max(BigDecimal.ZERO));
        if ("PAYOS".equals(method) && order.getTotalAmount().signum() > 0) payos.requireConfigured();
        orders.save(order); if(applied!=null) vouchers.record(order,applied);
        // Consume only purchased variants; keep unselected lines and any remaining quantity.
        Map<CartLineKey, Integer> remainingPurchase = new HashMap<>();
        grouped.forEach((key, line) -> remainingPurchase.put(key, line.quantity()));
        for (CartItem cartItem : carts.findByCustomerIdOrderByIdAsc(customer.getId())) {
            var key = new CartLineKey(cartItem.getProduct().getId(), clean(cartItem.getSize()), clean(cartItem.getColor()));
            int purchased = remainingPurchase.getOrDefault(key, 0);
            if (purchased == 0) continue;
            int consumed = Math.min(purchased, cartItem.getQuantity());
            remainingPurchase.put(key, purchased - consumed);
            if (consumed == cartItem.getQuantity()) carts.delete(cartItem);
            else { cartItem.setQuantity(cartItem.getQuantity() - consumed); carts.save(cartItem); }
        }
        notifications.create(customer, "Đặt hàng thành công", "Đơn hàng #" + order.getOrderCode() + " đã được ghi nhận.", "ORDER_CREATED", order.getId());
        Payment payment = new Payment(); payment.setOrder(order); payment.setAmount(order.getTotalAmount()); payment.setExpiresAt(order.getExpiresAt());
        if (order.getTotalAmount().signum()==0) { payment.setStatus("PAID"); order.setPaymentStatus("PAID"); payments.save(payment); return new Checkout(view(order), null, null, null, null); }
        if ("COD".equals(method)) { payment.setStatus("PENDING"); payments.save(payment); return new Checkout(view(order), null, null, null, null); }
        PayosService.Link link = payos.createPaymentLink(order); payment.setPaymentLinkId(link.paymentLinkId()); payment.setCheckoutUrl(link.checkoutUrl()); payment.setQrCode(link.qrCode()); payments.save(payment);
        saveAttempt(order, order.getOrderCode(), link, order.getExpiresAt());
        return new Checkout(view(order), link.checkoutUrl(), link.paymentLinkId(), link.qrCode(), order.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Page<OrderView> history(User customer, int page, int size, String requestedTab, String requestedKeyword) {
        if (page < 0 || size < 1 || size > 50)
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ.");
        String tab = requestedTab == null || requestedTab.isBlank() ? "ALL" : requestedTab.trim().toUpperCase(Locale.ROOT);
        Set<String> tabs = Set.of("ALL", "TO_PAY", "TO_CONFIRM", "TO_SHIP", "TO_RECEIVE", "TO_CONFIRM_RECEIPT", "COMPLETED", "CANCELLED", "RETURN");
        if (!tabs.contains(tab)) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Bộ lọc đơn hàng không hợp lệ.");
        String keyword = requestedKeyword == null ? "" : requestedKeyword.trim().toLowerCase(Locale.ROOT);
        if (keyword.length() > 120) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Từ khóa tìm kiếm tối đa 120 ký tự.");

        Specification<Order> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customer").get("id"), customer.getId()));
            var cancelled = cb.equal(root.get("status"), "CANCELLED");
            var hasReturn = cb.notEqual(cb.coalesce(root.<String>get("returnStatus"), "NONE"), "NONE");
            var unpaidPayos = cb.and(cb.equal(cb.coalesce(root.<String>get("paymentMethod"), "COD"), "PAYOS"),
                cb.notEqual(cb.coalesce(root.<String>get("paymentStatus"), "PENDING"), "PAID"));
            var payableStatus = root.get("status").in("PENDING", "PENDING_PAYMENT", "CONFIRMED", "PREPARING");
            if (!"ALL".equals(tab) && !"CANCELLED".equals(tab)) predicates.add(cb.not(cancelled));
            if (!Set.of("ALL", "CANCELLED", "RETURN").contains(tab)) predicates.add(cb.not(hasReturn));
            switch (tab) {
                case "TO_PAY" -> predicates.add(cb.and(unpaidPayos, payableStatus));
                case "TO_CONFIRM" -> {
                    predicates.add(cb.and(root.get("status").in("PENDING", "PENDING_PAYMENT"), cb.not(unpaidPayos)));
                }
                case "TO_SHIP" -> predicates.add(cb.and(root.get("status").in("CONFIRMED", "PREPARING"), cb.not(unpaidPayos)));
                case "TO_RECEIVE" -> predicates.add(root.get("status").in("SHIPPED", "DELIVERING"));
                case "TO_CONFIRM_RECEIPT" -> predicates.add(cb.equal(root.get("status"), "DELIVERED"));
                case "COMPLETED" -> predicates.add(cb.and(cb.equal(root.get("status"), "COMPLETED"),
                    cb.or(cb.isNull(root.get("returnStatus")), cb.equal(root.get("returnStatus"), "NONE"))));
                case "CANCELLED" -> predicates.add(cb.equal(root.get("status"), "CANCELLED"));
                case "RETURN" -> predicates.add(hasReturn);
                default -> { }
            }
            if (!keyword.isBlank()) {
                String pattern = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var itemQuery = query.subquery(Long.class);
                var item = itemQuery.from(OrderItem.class);
                itemQuery.select(item.get("order").get("id")).where(
                    cb.equal(item.get("order").get("id"), root.get("id")),
                    cb.like(cb.lower(item.get("product").get("name")), pattern, '\\'));
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("orderCode").as(String.class)), pattern, '\\'),
                    cb.exists(itemQuery)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return orders.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(this::view);
    }
    @Transactional(readOnly = true) public OrderView detail(User customer, Long id) { return view(owned(customer, id)); }

    @Transactional(readOnly = true) public PaymentView paymentDetails(User customer, Long id) {
        Order order = owned(customer, id);
        if (!"PAYOS".equals(order.getPaymentMethod())) throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Đơn hàng không dùng PayOS.");
        Payment payment = payments.findByOrderId(order.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy thông tin thanh toán."));
        return paymentView(order, payment);
    }

    private PaymentView paymentView(Order order, Payment payment) {
        boolean expired = order.getExpiresAt() != null && !order.getExpiresAt().isAfter(Instant.now());
        boolean available = "PENDING".equals(order.getPaymentStatus()) && !"CANCELLED".equals(order.getStatus()) && !expired;
        String status = "CANCELLED".equals(order.getStatus()) && "PENDING".equals(order.getPaymentStatus()) ? "CANCELLED" :
            expired && "PENDING".equals(order.getPaymentStatus()) ? "EXPIRED" : order.getPaymentStatus();
        return new PaymentView(order.getId(), order.getOrderCode(), status, order.getTotalAmount(), order.getExpiresAt(),
            available ? payment.getQrCode() : null, available ? payment.getCheckoutUrl() : null,
            !"PAID".equals(order.getPaymentStatus()) && !Set.of("CANCELLED", "SHIPPED", "DELIVERING", "DELIVERED", "COMPLETED").contains(order.getStatus()));
    }

    @Transactional public PaymentView syncPayment(User customer, Long id) {
        Order order = orders.findForStatusUpdate(id).filter(o -> Objects.equals(o.getCustomer().getId(), customer.getId()))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng."));
        if (!"PAYOS".equals(order.getPaymentMethod())) throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Đơn hàng không dùng PayOS.");
        Payment payment = payments.findByOrderId(id).orElseThrow();
        if (!"PAID".equals(order.getPaymentStatus()) && !"CANCELLED".equals(order.getStatus()) && payment.getPaymentLinkId() != null) {
            PaymentAttempt attempt = attempts.findFirstByOrderIdOrderByIdDesc(id).orElse(null);
            if (attempt != null) {
                PayosService.ProviderPayment result = payos.getPayment(attempt.getPaymentLinkId());
                validateProvider(attempt, order, result);
                applyProviderStatus(order, payment, attempt, result.status(), result.amountPaid());
            }
        }
        return paymentView(order, payment);
    }

    @Transactional public PaymentView retryPayment(User customer, Long id) {
        Order order = orders.findForStatusUpdate(id).filter(o -> Objects.equals(o.getCustomer().getId(), customer.getId()))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng."));
        if (!"PAYOS".equals(order.getPaymentMethod()) || order.getTotalAmount().signum() <= 0)
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_RETRYABLE", "Đơn hàng không cần thanh toán qua PayOS.");
        if ("CANCELLED".equals(order.getStatus()) || Set.of("SHIPPED", "DELIVERING", "DELIVERED", "COMPLETED").contains(order.getStatus()) || "PAID".equals(order.getPaymentStatus()))
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_RETRYABLE", "Đơn hàng không thể thanh toán lại.");
        Payment payment = payments.findByOrderId(id).orElseThrow();
        PaymentAttempt previous = attempts.findFirstByOrderIdOrderByIdDesc(id).orElse(null);
        if (previous != null && payment.getPaymentLinkId() != null && previous.getPaymentLinkId() != null) {
            PayosService.ProviderPayment current = payos.getPayment(previous.getPaymentLinkId());
            validateProvider(previous, order, current);
            applyProviderStatus(order, payment, previous, current.status(), current.amountPaid());
            if ("PAID".equals(order.getPaymentStatus())) return paymentView(order, payment);
            if ("PENDING".equals(order.getPaymentStatus()) && order.getExpiresAt() != null && order.getExpiresAt().isAfter(Instant.now()))
                return paymentView(order, payment);
            if ("PENDING".equalsIgnoreCase(current.status()) || "PROCESSING".equalsIgnoreCase(current.status())) {
                payos.cancelPaymentLink(previous.getPaymentLinkId());
                previous.setStatus("CANCELLED"); previous.setUpdatedAt(Instant.now()); attempts.save(previous);
            }
        }
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        order.setExpiresAt(expiresAt);
        long providerCode = nextProviderCode();
        PayosService.Link link = payos.createPaymentLink(order, providerCode);
        payment.setPaymentLinkId(link.paymentLinkId()); payment.setCheckoutUrl(link.checkoutUrl()); payment.setQrCode(link.qrCode());
        payment.setExpiresAt(expiresAt); payment.setStatus("PENDING"); payment.setUpdatedAt(Instant.now());
        order.setPaymentStatus("PENDING"); order.setUpdatedAt(Instant.now());
        payments.save(payment); orders.save(order); saveAttempt(order, providerCode, link, expiresAt);
        return paymentView(order, payment);
    }

    private void saveAttempt(Order order, long providerCode, PayosService.Link link, Instant expiresAt) {
        PaymentAttempt attempt = new PaymentAttempt(); attempt.setOrder(order); attempt.setProviderOrderCode(providerCode);
        attempt.setPaymentLinkId(link.paymentLinkId()); attempt.setCheckoutUrl(link.checkoutUrl()); attempt.setQrCode(link.qrCode());
        attempt.setExpiresAt(expiresAt); attempts.save(attempt);
    }

    private long nextProviderCode() {
        long code = 100000000000L + java.util.concurrent.ThreadLocalRandom.current().nextLong(89999999999L);
        while (orders.findByOrderCode(code).isPresent() || attempts.existsByProviderOrderCode(code)) code++;
        return code;
    }

    private void validateProvider(PaymentAttempt attempt, Order order, PayosService.ProviderPayment result) {
        if (result.orderCode() != attempt.getProviderOrderCode() || result.amount() != order.getTotalAmount().longValueExact() ||
            (!result.paymentLinkId().isBlank() && !result.paymentLinkId().equals(attempt.getPaymentLinkId())))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "PayOS trả thông tin giao dịch không khớp.");
    }

    private void applyProviderStatus(Order order, Payment payment, PaymentAttempt attempt, String status, long amountPaid) {
        String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if ("PAID".equals(normalized)) {
            if (amountPaid < order.getTotalAmount().longValueExact())
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Số tiền thanh toán không khớp.");
            attempt.setStatus("PAID");
            if (!"PAID".equals(order.getPaymentStatus()) && !"CANCELLED".equals(order.getStatus())) {
                order.setPaymentStatus("PAID"); payment.setStatus("PAID");
                notifications.create(order.getCustomer(), "Thanh toán thành công", "Đơn hàng #" + order.getOrderCode() + " đã thanh toán thành công.", "PAYMENT_CONFIRMED", order.getId());
            }
        } else if (Set.of("CANCELLED", "EXPIRED").contains(normalized)) {
            attempt.setStatus(normalized);
            if (!"PAID".equals(order.getPaymentStatus())) {
                order.setPaymentStatus(normalized); payment.setStatus(normalized);
            }
        }
        attempt.setUpdatedAt(Instant.now()); payment.setUpdatedAt(Instant.now()); order.setUpdatedAt(Instant.now());
        attempts.save(attempt); payments.save(payment); orders.save(order);
    }

    @Transactional public OrderView cancel(User customer, Long id, String reason) {
        Order order = orders.findForStatusUpdate(id).filter(o -> Objects.equals(o.getCustomer().getId(), customer.getId())).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Không tìm thấy đơn hàng.")); if ("CANCELLED".equals(order.getStatus())) return view(order); if (!CUSTOMER_CANCELLABLE_STATUSES.contains(order.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "ORDER_CANNOT_CANCEL", "Đơn hàng đã chuyển sang giao hàng hoặc đã hoàn tất nên không thể hủy.");
        if ("PAYOS".equals(order.getPaymentMethod()) && !"PAID".equals(order.getPaymentStatus())) {
            Payment payment = payments.findByOrderId(order.getId()).orElse(null);
            PaymentAttempt latest = attempts.findFirstByOrderIdOrderByIdDesc(order.getId()).orElse(null);
            if (payment != null && latest != null && latest.getPaymentLinkId() != null) {
                PayosService.ProviderPayment current = payos.getPayment(latest.getPaymentLinkId());
                validateProvider(latest, order, current);
                applyProviderStatus(order, payment, latest, current.status(), current.amountPaid());
                if ("PAID".equals(order.getPaymentStatus())) return view(order);
                if ("PENDING".equalsIgnoreCase(current.status()) || "PROCESSING".equalsIgnoreCase(current.status())) {
                    payos.cancelPaymentLink(latest.getPaymentLinkId());
                    latest.setStatus("CANCELLED"); latest.setUpdatedAt(Instant.now()); attempts.save(latest);
                }
            }
        }
        order.setStatus("CANCELLED"); order.setPaymentStatus("CANCELLED"); order.setCancelReason(clean(reason) == null ? "Khách hàng yêu cầu hủy đơn." : clean(reason)); payments.findByOrderId(order.getId()).ifPresent(payment -> { payment.setStatus("CANCELLED"); payments.save(payment); }); releaseStock(order); vouchers.release(order); order.setUpdatedAt(Instant.now()); orders.save(order); notifications.create(customer, "Đã hủy đơn hàng", "Đơn hàng #" + order.getOrderCode() + " đã được hủy.", "ORDER_CANCELLED", order.getId()); return view(order);
    }

    private Order lockedOwned(User customer, Long id) {
        return orders.findForStatusUpdate(id).filter(order -> Objects.equals(order.getCustomer().getId(), customer.getId()))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng."));
    }

    @Transactional public OrderView confirmReceipt(User customer, Long id) {
        Order order = lockedOwned(customer, id);
        if ("COMPLETED".equals(order.getStatus())) return view(order);
        if (!"DELIVERED".equals(order.getStatus()) || (order.getReturnStatus() != null && !"NONE".equals(order.getReturnStatus())))
            throw new ApiException(HttpStatus.CONFLICT, "RECEIPT_NOT_ALLOWED", "Chỉ xác nhận đơn đã giao và chưa có yêu cầu đổi trả. Vui lòng tải lại đơn hàng.");
        order.setStatus("COMPLETED"); order.setUpdatedAt(Instant.now()); orders.save(order);
        notifications.create(customer, "Đơn hàng hoàn thành", "Bạn đã xác nhận nhận hàng cho đơn #" + order.getOrderCode() + ".", "ORDER_COMPLETED", order.getId());
        return view(order);
    }

    @Transactional public OrderView requestReturn(User customer, Long id, String reason) {
        Order order = lockedOwned(customer, id); if (!Set.of("DELIVERED", "COMPLETED").contains(order.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "RETURN_NOT_ALLOWED", "Chỉ có thể yêu cầu đổi trả sau khi đã nhận hàng."); if (order.getReturnStatus() != null && !"NONE".equals(order.getReturnStatus())) throw new ApiException(HttpStatus.CONFLICT, "RETURN_ALREADY_REQUESTED", "Đơn hàng đã có yêu cầu đổi trả.");
        order.setReturnStatus("REQUESTED"); order.setReturnReason(reason.trim()); order.setReturnRequestedAt(Instant.now()); order.setUpdatedAt(Instant.now()); orders.save(order); notifications.create(customer, "Đã tiếp nhận yêu cầu đổi trả", "Yêu cầu đổi trả đơn #" + order.getOrderCode() + " đang chờ xử lý.", "RETURN_REQUESTED", order.getId()); return view(order);
    }

    @Transactional public boolean webhook(JsonNode payload, String signature) {
        JsonNode data = payload == null ? null : payload.path("data"); if (data == null || data.isMissingNode()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Webhook không có dữ liệu thanh toán."); if (!payos.verifyWebhook(data, signature)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Chữ ký webhook không hợp lệ.");
        long code = data.path("orderCode").asLong(0); String providerCode = data.path("code").asText(payload.path("code").asText("")); boolean success = payload.path("success").asBoolean(false);
        PaymentAttempt attempt = attempts.findByProviderOrderCode(code).orElse(null);
        Order order = attempt == null ? orders.findByOrderCode(code).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Không tìm thấy đơn hàng tương ứng.")) : attempt.getOrder();
        BigDecimal amount = BigDecimal.valueOf(data.path("amount").asLong(-1)); if (code == 0 || amount.compareTo(order.getTotalAmount()) != 0) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Thông tin đơn hàng hoặc số tiền không khớp.");
        order = orders.findForStatusUpdate(order.getId()).orElseThrow();
        if (attempt != null && !data.path("paymentLinkId").asText("").isBlank() && !data.path("paymentLinkId").asText().equals(attempt.getPaymentLinkId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Mã giao dịch không khớp.");
        if ("PAID".equals(order.getPaymentStatus()) || !"PAYOS".equals(order.getPaymentMethod())) return true;
        if ("CANCELLED".equals(order.getStatus())) {
            if (success && "00".equals(providerCode)) {
                Payment canceledPayment = payments.findByOrderId(order.getId()).orElseThrow();
                canceledPayment.setStatus("PAID"); canceledPayment.setUpdatedAt(Instant.now());
                order.setPaymentStatus("PAID"); order.setUpdatedAt(Instant.now());
                if (attempt != null) { attempt.setStatus("PAID"); attempt.setUpdatedAt(Instant.now()); attempts.save(attempt); }
                payments.save(canceledPayment); orders.save(order);
                notifications.create(order.getCustomer(), "Thanh toán sau khi hủy đơn", "PayOS báo đã thanh toán đơn #" + order.getOrderCode() + " sau khi đơn bị hủy. Vui lòng liên hệ cửa hàng để xử lý hoàn tiền.", "PAYMENT_AFTER_CANCEL", order.getId());
                log.error("PayOS payment succeeded after cancellation for order {}", order.getId());
            }
            return true;
        }
        String eventKey = data.path("paymentLinkId").asText("") + ":" + code + ":" + data.path("transactionDateTime").asText("") + ":" + providerCode; if (webhookEvents.existsByEventKey(eventKey)) return true; PaymentWebhookEvent event = new PaymentWebhookEvent(); event.setEventKey(eventKey); event.setOrderCode(code); webhookEvents.save(event);
        Payment payment = payments.findByOrderId(order.getId()).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK", "Không tìm thấy giao dịch."));
        if (success && "00".equals(providerCode)) {
            if (attempt != null && payment.getPaymentLinkId() != null && !payment.getPaymentLinkId().equals(attempt.getPaymentLinkId())) {
                try { payos.cancelPaymentLink(payment.getPaymentLinkId()); }
                catch (ApiException ex) { log.error("Could not cancel newer PayOS link for already-paid order {}", order.getId(), ex); }
            }
            payment.setStatus("PAID"); order.setPaymentStatus("PAID");
            if (attempt != null) { attempt.setStatus("PAID"); attempt.setUpdatedAt(Instant.now()); attempts.save(attempt); }
            notifications.create(order.getCustomer(), "Thanh toán thành công", "Đơn hàng #" + order.getOrderCode() + " đã thanh toán thành công. Trạng thái xử lý đơn do cửa hàng cập nhật.", "PAYMENT_CONFIRMED", order.getId());
        } else {
            String desc = data.path("desc").asText("").toLowerCase(Locale.ROOT); String failedStatus = desc.contains("expire") ? "EXPIRED" : "CANCELLED";
            if (attempt != null) { attempt.setStatus(failedStatus); attempt.setUpdatedAt(Instant.now()); attempts.save(attempt); }
            if (attempt == null || Objects.equals(payment.getPaymentLinkId(), attempt.getPaymentLinkId())) {
                payment.setStatus(failedStatus); order.setPaymentStatus(failedStatus);
                notifications.create(order.getCustomer(), "Thanh toán chưa hoàn tất", "Đơn hàng #" + order.getOrderCode() + " chưa thanh toán thành công. Bạn có thể tạo mã mới hoặc hủy đơn.", "PAYMENT_FAILED", order.getId());
            }
        }
        payment.setUpdatedAt(Instant.now()); order.setUpdatedAt(Instant.now()); payments.save(payment); orders.save(order); return false;
    }

    @Scheduled(fixedDelayString = "${app.payment-cleanup-ms:300000}") @Transactional public void cleanupExpired() {
        for (Order candidate : orders.findExpiredPending(Instant.now())) {
            Order order = orders.findForStatusUpdate(candidate.getId()).orElseThrow();
            if (!"PAYOS".equals(order.getPaymentMethod()) || !"PENDING".equals(order.getPaymentStatus()) || "CANCELLED".equals(order.getStatus()) || order.getExpiresAt() == null || !order.getExpiresAt().isBefore(Instant.now())) continue;
            payments.findByOrderId(order.getId()).ifPresent(p -> { p.setStatus("EXPIRED"); p.setUpdatedAt(Instant.now()); payments.save(p); });
            attempts.findFirstByOrderIdOrderByIdDesc(order.getId()).ifPresent(a -> { if ("PENDING".equals(a.getStatus())) { a.setStatus("EXPIRED"); a.setUpdatedAt(Instant.now()); attempts.save(a); } });
            order.setPaymentStatus("EXPIRED"); order.setUpdatedAt(Instant.now()); orders.save(order);
            notifications.create(order.getCustomer(), "Hết hạn thanh toán", "Liên kết thanh toán đơn #" + order.getOrderCode() + " đã hết hạn. Bạn có thể tạo mã mới hoặc hủy đơn.", "PAYMENT_EXPIRED", order.getId());
        }
    }

    private Order owned(User customer, Long id) { return orders.findByIdAndCustomerId(id, customer.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.")); }

    private void validateVariant(Product product, String size, String color) { if (!hasOption(product.getSizes(), size) || !hasOption(product.getColors(), color)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VARIANT", "Size hoặc màu sản phẩm không hợp lệ."); }
    private boolean hasOption(String csv, String value) { return value == null || value.isBlank() || Arrays.stream(String.valueOf(csv == null ? "" : csv).split(",")).map(String::trim).anyMatch(value.trim()::equalsIgnoreCase); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void releaseStock(Order order) { if (order.isStockReleased()) return; for (OrderItem item : order.getItems()) products.findById(item.getProduct().getId()).ifPresent(p -> { p.setStock(p.getStock() + item.getQuantity()); p.setUpdatedAt(Instant.now()); products.save(p); }); order.setStockReleased(true); }
    private ApiException invalidCart() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "Sản phẩm trong giỏ không còn hợp lệ."); }
    private long nextCode() { long code = 100000000000L + (System.currentTimeMillis() % 89999999999L); while (orders.findByOrderCode(code).isPresent()) code++; return code; }

    public OrderView view(Order o) { List<OrderLineView> lines = o.getItems().stream().map(i -> new OrderLineView(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getImageUrl(), i.getQuantity(), i.getUnitPrice(), i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())), i.getSize(), i.getColor())).toList(); return new OrderView(o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentStatus(), o.getPaymentMethod(), o.getDeliveryAddress(), o.getTrackingCode(), o.getCancelReason(), o.getReturnStatus(), o.getReturnReason(), o.getVoucherCode(), o.getDiscountAmount(), o.getTotalAmount(), o.getCreatedAt(), o.getUpdatedAt(), lines); }

    public record RequestedItem(Long productId, int quantity, String size, String color) {}
    private record CartLineKey(Long productId, String size, String color) {}
    public record CheckoutRequest(String deliveryAddress, String paymentMethod, String voucherCode, List<RequestedItem> items) {}
    public record Checkout(OrderView order, String paymentUrl, String paymentLinkId, String qrCode, Instant expiresAt) {}
    public record PaymentView(Long orderId, Long orderCode, String paymentStatus, BigDecimal amount, Instant expiresAt, String qrCode, String paymentUrl, boolean retryable) {}
    public record OrderView(Long id, Long orderCode, String status, String paymentStatus, String paymentMethod, String deliveryAddress, String trackingCode, String cancelReason, String returnStatus, String returnReason, String voucherCode, BigDecimal discountAmount, BigDecimal totalAmount, Instant createdAt, Instant updatedAt, List<OrderLineView> items) {}
    public record OrderLineView(Long productId, String name, String imageUrl, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, String size, String color) {}
}
