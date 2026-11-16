package com.htttdn.crm.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Thanh toán công nợ cho một phiếu nhập hàng của nhà cung cấp. */
@Entity
@Table(name = "supplier_payments")
public class SupplierPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private SupplierReceipt receipt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    @Column(nullable = false, length = 30)
    private String method;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SupplierPayment() {}

    public Long getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { supplier = value; }
    public SupplierReceipt getReceipt() { return receipt; }
    public void setReceipt(SupplierReceipt value) { receipt = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public LocalDate getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDate value) { paidAt = value; }
    public String getMethod() { return method; }
    public void setMethod(String value) { method = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
