package com.htttdn.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.OrderItem;
import com.htttdn.crm.entity.Payment;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.CartItemRepository;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.repository.PaymentRepository;
import com.htttdn.crm.repository.PaymentWebhookEventRepository;
import com.htttdn.crm.repository.ProductRepository;
import com.htttdn.crm.repository.StoreVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private OrderRepository orders;
    @Mock private ProductRepository products;
    @Mock private CartItemRepository carts;
    @Mock private PaymentRepository payments;
    @Mock private PaymentWebhookEventRepository webhookEvents;
    @Mock private StoreVoucherRepository vouchers;
    @Mock private NotificationService notifications;
    @Mock private PayosService payos;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, products, carts, payments, webhookEvents, vouchers, notifications, payos);
    }

    @Test
    void successfulWebhookConfirmsOrderAndPayment() throws Exception {
        Order order = order(101L, 123456L, "PENDING_PAYMENT", BigDecimal.valueOf(699000));
        Payment payment = payment(order);
        JsonNode payload = mapper.readTree("""
                {"success":true,"data":{"orderCode":123456,"amount":699000,"code":"00",
                "paymentLinkId":"pl-123","transactionDateTime":"2026-09-21T08:00:00Z"}}
                """);
        when(payos.verifyWebhook(payload.path("data"), "signature")).thenReturn(true);
        when(orders.findByOrderCodeForUpdate(123456L)).thenReturn(Optional.of(order));
        when(payments.findByOrderId(101L)).thenReturn(Optional.of(payment));

        boolean alreadyProcessed = service.webhook(payload, "signature");

        assertFalse(alreadyProcessed);
        assertEquals("CONFIRMED", order.getStatus());
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("PAID", payment.getStatus());
        assertEquals("00", payment.getResponseCode());
        verify(webhookEvents).save(any());
    }

    @Test
    void failedWebhookReleasesReservedStockOnce() throws Exception {
        Order order = order(102L, 123457L, "PENDING_PAYMENT", BigDecimal.valueOf(699000));
        Product product = product(7L, 2);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(699000));
        order.getItems().add(item);
        Payment payment = payment(order);
        JsonNode payload = mapper.readTree("""
                {"success":false,"data":{"orderCode":123457,"amount":699000,"code":"01",
                "desc":"Payment expired","paymentLinkId":"pl-124","transactionDateTime":"2026-09-21T08:01:00Z"}}
                """);
        when(payos.verifyWebhook(payload.path("data"), "signature")).thenReturn(true);
        when(orders.findByOrderCodeForUpdate(123457L)).thenReturn(Optional.of(order));
        when(payments.findByOrderId(102L)).thenReturn(Optional.of(payment));
        when(products.findById(7L)).thenReturn(Optional.of(product));

        service.webhook(payload, "signature");

        assertEquals("CANCELLED", order.getStatus());
        assertEquals("EXPIRED", order.getPaymentStatus());
        assertEquals("EXPIRED", payment.getStatus());
        assertEquals(3, product.getStock());
        assertTrue(order.isStockReleased());
    }

    @Test
    void invalidSignatureUnknownOrderAndAmountMismatchAreRejected() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"success":true,"data":{"orderCode":123458,"amount":699000,"code":"00"}}
                """);
        when(payos.verifyWebhook(payload.path("data"), "bad")).thenReturn(false);
        assertThrows(ApiException.class, () -> service.webhook(payload, "bad"));

        when(payos.verifyWebhook(payload.path("data"), "good")).thenReturn(true);
        when(orders.findByOrderCodeForUpdate(123458L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.webhook(payload, "good"));

        Order order = order(103L, 123458L, "PENDING_PAYMENT", BigDecimal.valueOf(700000));
        when(orders.findByOrderCodeForUpdate(123458L)).thenReturn(Optional.of(order));
        assertThrows(ApiException.class, () -> service.webhook(payload, "good"));
        verify(payments, never()).save(any());
    }

    @Test
    void repeatedWebhookIsAcknowledgedWithoutUpdatingPaymentAgain() throws Exception {
        Order order = order(104L, 123459L, "CONFIRMED", BigDecimal.valueOf(699000));
        Payment payment = payment(order);
        JsonNode payload = mapper.readTree("""
                {"success":true,"data":{"orderCode":123459,"amount":699000,"code":"00",
                "paymentLinkId":"pl-125","transactionDateTime":"2026-09-21T08:02:00Z"}}
                """);
        when(payos.verifyWebhook(payload.path("data"), "signature")).thenReturn(true);
        when(orders.findByOrderCodeForUpdate(123459L)).thenReturn(Optional.of(order));
        when(webhookEvents.existsByEventKey(any())).thenReturn(true);

        assertTrue(service.webhook(payload, "signature"));
        verify(payments, never()).findByOrderId(anyLong());
        verify(payments, never()).save(any());
        assertEquals("PENDING", payment.getStatus());
    }

    @Test
    void insufficientStockDoesNotCreatePayableOrder() {
        User customer = new User();
        Product product = product(8L, 1);
        when(orders.findByOrderCode(anyLong())).thenReturn(Optional.empty());
        when(products.findLockedById(8L)).thenReturn(Optional.of(product));

        assertThrows(ApiException.class, () -> service.create(
                customer,
                new OrderService.CheckoutRequest("Số 1, Đường A", "PAYOS", null,
                        List.of(new OrderService.RequestedItem(8L, 2, "M", "Đen")))));
        verify(orders, never()).save(any());
        verify(payos, never()).createPaymentLink(any());
    }

    @Test
    void expiredPendingPaymentIsCleanedUpAndStockReleased() {
        Order order = order(105L, 123460L, "PENDING_PAYMENT", BigDecimal.valueOf(699000));
        order.setExpiresAt(Instant.now().minusSeconds(60));
        Product product = product(9L, 2);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(699000));
        order.getItems().add(item);
        Payment payment = payment(order);
        when(orders.findExpiredPending(any())).thenReturn(List.of(order));
        when(payments.findByOrderId(105L)).thenReturn(Optional.of(payment));
        when(products.findById(9L)).thenReturn(Optional.of(product));

        service.cleanupExpired();

        assertEquals("CANCELLED", order.getStatus());
        assertEquals("EXPIRED", order.getPaymentStatus());
        assertEquals("EXPIRED", payment.getStatus());
        assertTrue(order.isStockReleased());
        assertEquals(3, product.getStock());
    }

    private Order order(Long id, Long orderCode, String status, BigDecimal total) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setOrderCode(orderCode);
        order.setStatus(status);
        order.setPaymentStatus("PENDING");
        order.setTotalAmount(total);
        User customer = new User();
        customer.setFullName("Khách hàng thử nghiệm");
        order.setCustomer(customer);
        return order;
    }

    private Payment payment(Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus("PENDING");
        return payment;
    }

    private Product product(Long id, int stock) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setName("Áo thử nghiệm");
        product.setPrice(BigDecimal.valueOf(699000));
        product.setStock(stock);
        product.setSizes("S,M,L");
        product.setColors("Đen,Be");
        return product;
    }
}
