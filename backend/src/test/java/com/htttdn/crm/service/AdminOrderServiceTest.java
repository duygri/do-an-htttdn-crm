package com.htttdn.crm.service;

import com.htttdn.crm.dto.admin.AdminDtos.StatusRequest;
import com.htttdn.crm.entity.Order;
import com.htttdn.crm.entity.Payment;
import com.htttdn.crm.entity.User;
import com.htttdn.crm.exception.ApiException;
import com.htttdn.crm.repository.OrderRepository;
import com.htttdn.crm.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminOrderServiceTest {
    @Test void shippedOrdersCannotBeCancelledAndStockIsNotTouched() {
        for(String state : new String[]{"SHIPPED","DELIVERING"}) {
            order(state);
            assertThrows(ApiException.class,()->service.updateStatus(9L,new StatusRequest("CANCELLED","Test")));
        }
        verifyNoInteractions(stock,notifications);
    }
    @Test void adminCannotCompleteCancelDeliveredOrSkipShipping() {
        for (String[] pair : new String[][]{{"DELIVERED","COMPLETED"},{"DELIVERED","CANCELLED"},{"CONFIRMED","DELIVERED"},{"PREPARING","DELIVERED"},{"COMPLETED","COMPLETED"}}) {
            Order order = order(pair[0]);
            assertThrows(ApiException.class, () -> service.updateStatus(9L,new StatusRequest(pair[1],"Lý do")));
            assertEquals(pair[0],order.getStatus());
        }
        Order shipped = order("SHIPPED");
        service.updateStatus(9L,new StatusRequest("DELIVERED",null));
        assertEquals("DELIVERED",shipped.getStatus());
    }
    @Test void acceptsPreparingAndDeliveringTransitions() {
        Order preparing = order("CONFIRMED");
        service.updateStatus(9L, new StatusRequest("PREPARING", null));
        assertEquals("PREPARING", preparing.getStatus());
        verify(notifications).create(eq(preparing.getCustomer()), eq("Cập nhật đơn hàng"), contains("Đang xử lý"), eq("ORDER_STATUS"), isNull());
        service.updateStatus(9L, new StatusRequest("SHIPPED", null));
        assertEquals("SHIPPED", preparing.getStatus());
        service.updateStatus(9L, new StatusRequest("DELIVERED", null));
        assertEquals("DELIVERED", preparing.getStatus());

        Order delivering = order("CONFIRMED");
        service.updateStatus(9L, new StatusRequest("DELIVERING", null));
        assertEquals("DELIVERING", delivering.getStatus());
        verify(notifications).create(eq(delivering.getCustomer()), eq("Cập nhật đơn hàng"), contains("Đang giao"), eq("ORDER_STATUS"), isNull());
    }

    @Test void preparingOrdersCanStillBeCancelled() {
        Order order = order("PREPARING");
        service.updateStatus(9L, new StatusRequest("CANCELLED", "Khách đổi ý"));
        assertEquals("CANCELLED", order.getStatus());
        verify(notifications).create(eq(order.getCustomer()), eq("Đơn hàng đã bị hủy"), contains("Khách đổi ý"), eq("ORDER_CANCELLED"), isNull());
    }

    final OrderRepository orders = mock(OrderRepository.class);
    final NotificationService notifications = mock(NotificationService.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final StockService stock = mock(StockService.class);
    final AdminOrderService service = new AdminOrderService(orders, notifications, mock(VoucherService.class), payments, stock, mock(RefundService.class));

    Order order(String status) {
        Order order = new Order(); order.setOrderCode(9009L); order.setCustomer(new User()); order.setStatus(status);
        when(orders.findForStatusUpdate(9L)).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        return order;
    }

    @Test void rejectsMissingBlankAndLongReasonsWithoutMutation() {
        for (String reason : new String[]{null, "  ", "x".repeat(1001)}) {
            ApiException e = assertThrows(ApiException.class, () -> service.updateStatus(9L, new StatusRequest("CANCELLED", reason)));
            assertEquals(400, e.status().value());
        }
        verifyNoInteractions(orders, notifications);
    }

    @Test void cancellingPersistsTrimmedReasonAndNotifiesOwnerOnce() {
        Order order = order("CONFIRMED");
        service.updateStatus(9L, new StatusRequest("CANCELLED", "  Hết hàng  "));
        service.updateStatus(9L, new StatusRequest("CANCELLED", "Hết hàng"));
        assertEquals("CANCELLED", order.getStatus()); assertEquals("Hết hàng", order.getCancelReason());
        verify(notifications, times(1)).create(order.getCustomer(), "Đơn hàng đã bị hủy", "Đơn hàng #9009 đã bị hủy. Lý do: Hết hàng", "ORDER_CANCELLED", order.getId());
        verify(orders, times(1)).save(order);
        verify(stock,times(1)).release(order);
    }

    @Test void cancellingUnpaidPayosStopsPaymentWithoutChangingAlreadyPaidOrders() {
        Order pending = order("PENDING"); pending.setPaymentMethod("PAYOS"); pending.setPaymentStatus("PENDING");
        Payment payment = new Payment(); when(payments.findByOrderId(pending.getId())).thenReturn(Optional.of(payment));
        service.updateStatus(9L, new StatusRequest("CANCELLED", "Hết hàng"));
        assertEquals("CANCELLED", pending.getPaymentStatus()); assertEquals("CANCELLED", payment.getStatus());
        verify(payments).save(payment);
        reset(payments);
        Order paid = order("PENDING"); paid.setPaymentMethod("PAYOS"); paid.setPaymentStatus("PAID");
        service.updateStatus(9L, new StatusRequest("CANCELLED", "Hết hàng"));
        assertEquals("PAID", paid.getPaymentStatus()); verifyNoInteractions(payments);
    }

    @Test void supplementsLegacyOrderAndRejectsOverwrite() {
        Order order = order("CANCELLED");
        service.updateStatus(9L, new StatusRequest("CANCELLED", "Không giao được"));
        ApiException e = assertThrows(ApiException.class, () -> service.updateStatus(9L, new StatusRequest("CANCELLED", "Lý do khác")));
        assertEquals(409, e.status().value()); assertEquals("Không giao được", order.getCancelReason());
        verify(notifications, times(1)).create(any(), anyString(), anyString(), eq("ORDER_CANCELLED"), isNull());
    }

    @Test void otherStatusesDoNotRequireReasonAndNotifyCustomer() {
        Order order = order("CONFIRMED");
        service.updateStatus(9L, new StatusRequest("SHIPPED", null));
        assertEquals("SHIPPED", order.getStatus()); verify(notifications).create(any(),anyString(),contains("Đang giao"),eq("ORDER_STATUS"),isNull());
    }
    @Test void rejectsBackwardAndTerminalTransitionsWithoutSideEffects() {
        for (String[] pair : new String[][]{{"CANCELLED","CONFIRMED"},{"CANCELLED","PENDING_PAYMENT"},{"COMPLETED","SHIPPED"},{"COMPLETED","CANCELLED"},{"SHIPPED","CONFIRMED"},{"DELIVERED","SHIPPED"},{"CONFIRMED","PENDING_PAYMENT"}}) {
            reset(orders, notifications);
            Order order = order(pair[0]);
            ApiException error = assertThrows(ApiException.class, () -> service.updateStatus(9L, new StatusRequest(pair[1], "Lý do thử")));
            assertEquals(409, error.status().value());
            assertEquals(pair[0], order.getStatus());
            verify(orders, never()).save(any()); verifyNoInteractions(notifications);
        }
    }
    @Test void advancesLegacyPendingAndTreatsRepeatedStatusAsNoOp() {
        Order order = order("PENDING");
        service.updateStatus(9L, new StatusRequest("CONFIRMED", null));
        service.updateStatus(9L, new StatusRequest("CONFIRMED", null));
        assertEquals("CONFIRMED", order.getStatus());
        verify(orders, times(1)).save(order); verify(notifications,times(1)).create(any(),anyString(),anyString(),eq("ORDER_STATUS"),isNull());
    }
    @Test void cannotSkipConfirmationOrConfirmUnpaidOrder() {
        for (String[] pair : new String[][]{{"PENDING","SHIPPED"},{"PENDING","COMPLETED"},{"PENDING_PAYMENT","CONFIRMED"},{"PENDING_PAYMENT","PENDING"}}) {
            reset(orders, notifications); Order order = order(pair[0]);
            assertThrows(ApiException.class, () -> service.updateStatus(9L,new StatusRequest(pair[1], null)));
            assertEquals(pair[0],order.getStatus()); verify(orders,never()).save(any());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PENDING", "EXPIRED", "FAILED", "CANCELLED", "COD", ""})
    void unpaidPayosCannotEnterOrAdvanceFulfillment(String paymentStatus) {
        for (String[] transition : new String[][]{{"PENDING", "CONFIRMED"},
                {"CONFIRMED", "PREPARING"}, {"CONFIRMED", "SHIPPED"}, {"CONFIRMED", "DELIVERING"},
                {"PREPARING", "SHIPPED"}, {"PREPARING", "DELIVERING"},
                {"SHIPPED", "DELIVERED"}, {"DELIVERING", "DELIVERED"}}) {
            Order order = payosOrder(transition[0], paymentStatus);
            var updatedAt = order.getUpdatedAt();

            ApiException error = assertThrows(ApiException.class,
                () -> service.updateStatus(9L, new StatusRequest(transition[1], null)),
                transition[0] + " -> " + transition[1]);

            assertEquals(409, error.status().value());
            assertEquals("PAYMENT_NOT_PAID", error.code());
            assertEquals(transition[0], order.getStatus());
            assertEquals(paymentStatus, order.getPaymentStatus());
            assertEquals(updatedAt, order.getUpdatedAt());
        }
        verify(orders, never()).save(any());
        verifyNoInteractions(payments, stock, notifications);
    }

    @ParameterizedTest
    @CsvSource({"PENDING_PAYMENT, CONFIRMED", "PENDING_PAYMENT, PENDING",
        "PENDING_PAYMENT, SHIPPED", "PENDING_PAYMENT, DELIVERED",
        "PENDING, SHIPPED", "CONFIRMED, DELIVERED", "SHIPPED, CONFIRMED",
        "CANCELLED, CONFIRMED", "DELIVERED, COMPLETED", "COMPLETED, SHIPPED"})
    void payosPaymentDoesNotOverrideTransitionRules(String current, String next) {
        for (String paymentStatus : new String[]{"PENDING", "PAID"}) {
            Order order = payosOrder(current, paymentStatus);
            ApiException error = assertThrows(ApiException.class,
                () -> service.updateStatus(9L, new StatusRequest(next, null)));
            assertEquals("INVALID_ORDER_TRANSITION", error.code());
            assertEquals(current, order.getStatus());
        }
        verify(orders, never()).save(any());
        verifyNoInteractions(payments, stock, notifications);
    }

    @ParameterizedTest
    @CsvSource({"PAYOS, PAID", "COD, COD", "COD, PENDING"})
    void paidPayosAndUncollectedCodCanCompleteFulfillment(String method, String paymentStatus) {
        Order order = order("PENDING");
        order.setPaymentMethod(method);
        order.setPaymentStatus(paymentStatus);
        order.setTotalAmount(new BigDecimal("100000"));
        for (String next : new String[]{"CONFIRMED", "PREPARING", "SHIPPED", "DELIVERED"}) {
            assertSame(order, service.updateStatus(9L, new StatusRequest(next, null)));
            assertEquals(next, order.getStatus());
            assertEquals(paymentStatus, order.getPaymentStatus());
        }
        verify(orders, times(4)).save(order);
        verifyNoInteractions(payments, stock);
    }

    @Test void zeroValuePayosUsesCheckoutPaidEvidenceWithoutPaidTimestampOrProviderLink() {
        Order order = payosOrder("PENDING", "PAID");
        order.setTotalAmount(BigDecimal.ZERO);
        assertNull(order.paidAt);
        for (String next : new String[]{"CONFIRMED", "PREPARING", "DELIVERING", "DELIVERED"}) {
            service.updateStatus(9L, new StatusRequest(next, null));
            assertEquals(next, order.getStatus());
        }
        assertEquals("PAID", order.getPaymentStatus());
        verifyNoInteractions(payments, stock);
    }

    @Test void zeroTotalAloneIsNotPaidEvidence() {
        Order order = payosOrder("PENDING", "PENDING");
        order.setTotalAmount(BigDecimal.ZERO);
        ApiException error = assertThrows(ApiException.class,
            () -> service.updateStatus(9L, new StatusRequest("CONFIRMED", null)));
        assertEquals("PAYMENT_NOT_PAID", error.code());
        assertEquals("PENDING", order.getStatus());
        verify(orders, never()).save(any());
        verifyNoInteractions(payments, stock, notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PAID"})
    void reviewFlagBlocksPayosFulfillmentEvenWhenPaid(String paymentStatus) {
        for (String[] transition : new String[][]{{"PENDING", "CONFIRMED"},
                {"CONFIRMED", "PREPARING"}, {"PREPARING", "SHIPPED"},
                {"CONFIRMED", "DELIVERING"}, {"SHIPPED", "DELIVERED"}}) {
            Order order = payosOrder(transition[0], paymentStatus);
            order.paymentReviewRequired = true;
            ApiException error = assertThrows(ApiException.class,
                () -> service.updateStatus(9L, new StatusRequest(transition[1], null)));
            assertEquals(409, error.status().value());
            assertEquals("PAYMENT_NEEDS_REVIEW", error.code());
            assertEquals(transition[0], order.getStatus());
        }
        verify(orders, never()).save(any());
        verifyNoInteractions(payments, stock, notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PENDING", "CONFIRMED", "PREPARING", "SHIPPED", "DELIVERED"})
    void repeatedPayosStatusRemainsNoOpEvenWhenUnpaidAndUnderReview(String status) {
        Order order = payosOrder(status, "PENDING");
        order.paymentReviewRequired = true;
        var updatedAt = order.getUpdatedAt();
        assertSame(order, service.updateStatus(9L, new StatusRequest(status, null)));
        assertEquals(updatedAt, order.getUpdatedAt());
        verify(orders, never()).save(any());
        verifyNoInteractions(payments, stock, notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PENDING", "CONFIRMED", "PREPARING"})
    void unpaidPayosCancellationRemainsIdempotent(String status) {
        Order order = payosOrder(status, "PENDING");
        Payment payment = new Payment();
        when(payments.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        service.updateStatus(9L, new StatusRequest("CANCELLED", "Khách đổi ý"));
        service.updateStatus(9L, new StatusRequest("CANCELLED", "Khách đổi ý"));
        assertEquals("CANCELLED", order.getStatus());
        assertEquals("CANCELLED", order.getPaymentStatus());
        assertEquals("CANCELLED", payment.getStatus());
        verify(orders).save(order);
        verify(payments).save(payment);
        verify(stock).release(order);
        verify(notifications).create(any(), anyString(), anyString(), eq("ORDER_CANCELLED"), isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PAID"})
    void reviewFlagStillBlocksPayosCancellation(String paymentStatus) {
        Order order = payosOrder("CONFIRMED", paymentStatus);
        order.paymentReviewRequired = true;
        ApiException error = assertThrows(ApiException.class,
            () -> service.updateStatus(9L, new StatusRequest("CANCELLED", "Khách đổi ý")));
        assertEquals("PAYMENT_NEEDS_REVIEW", error.code());
        assertEquals("CONFIRMED", order.getStatus());
        assertEquals(paymentStatus, order.getPaymentStatus());
        verify(orders, never()).save(any());
        verifyNoInteractions(payments, stock, notifications);
    }

    Order payosOrder(String status, String paymentStatus) {
        Order order = order(status);
        order.setPaymentMethod("PAYOS");
        order.setPaymentStatus(paymentStatus);
        order.setTotalAmount(new BigDecimal("100000"));
        return order;
    }
}
