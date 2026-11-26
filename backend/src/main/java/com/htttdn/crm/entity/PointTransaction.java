package com.htttdn.crm.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Sổ cái điểm thưởng của khách hàng. */
@Entity
@Table(name = "point_transactions")
public class PointTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "txn_type", nullable = false, length = 20)
    private String txnType;

    @Column(nullable = false)
    private int points;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "request_key", length = 36) private String requestKey;
    @Column(name = "redemption_result", columnDefinition = "text") private String redemptionResult;
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getRedemptionResult() { return redemptionResult; }
    public void setRedemptionResult(String value) { redemptionResult = value; }
    public PointTransaction() {}

    public Long getId() { return id; }
    public User getCustomer() { return customer; }
    public void setCustomer(User value) { customer = value; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long value) { orderId = value; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String value) { txnType = value; }
    public int getPoints() { return points; }
    public void setPoints(int value) { points = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
