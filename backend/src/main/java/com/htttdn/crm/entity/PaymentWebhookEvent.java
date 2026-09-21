package com.htttdn.crm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = "event_key"),
        @UniqueConstraint(columnNames = {"order_id", "webhook_type", "gateway_reference"})
})
public class PaymentWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, length = 255)
    private String eventKey;

    @Column(name = "order_code")
    private Long orderCode;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "webhook_type", nullable = false, length = 30)
    private String webhookType;

    @Column(name = "gateway_reference", length = 255)
    private String gatewayReference;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    public PaymentWebhookEvent() {
    }

    public Long getId() { return id; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public Long getOrderCode() { return orderCode; }
    public void setOrderCode(Long orderCode) { this.orderCode = orderCode; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getWebhookType() { return webhookType; }
    public void setWebhookType(String webhookType) { this.webhookType = webhookType; }
    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String gatewayReference) { this.gatewayReference = gatewayReference; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
