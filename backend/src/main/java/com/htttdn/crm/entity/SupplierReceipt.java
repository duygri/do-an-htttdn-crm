package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Phiếu nhập hàng theo hóa đơn nhà cung cấp. Chỉ ghi nhận chi phí, không thay đổi tồn kho. */
@Entity @Table(name="supplier_receipts")
public class SupplierReceipt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="supplier_id") private Supplier supplier;
    @Column(name="invoice_number",nullable=false,length=80) private String invoiceNumber;
    @Column(name="invoice_date",nullable=false) private LocalDate invoiceDate;
    @Column(name="total_amount",nullable=false,precision=15,scale=2) private BigDecimal totalAmount=BigDecimal.ZERO;
    @Column(length=1000) private String note;
    @Column(name="created_by") private Long createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @OneToMany(mappedBy="receipt",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.EAGER)
    @OrderBy("id ASC") private List<SupplierReceiptItem> items=new ArrayList<>();

    public SupplierReceipt(){}
    public Long getId(){return id;}
    public Supplier getSupplier(){return supplier;} public void setSupplier(Supplier v){supplier=v;}
    public String getInvoiceNumber(){return invoiceNumber;} public void setInvoiceNumber(String v){invoiceNumber=v;}
    public LocalDate getInvoiceDate(){return invoiceDate;} public void setInvoiceDate(LocalDate v){invoiceDate=v;}
    public BigDecimal getTotalAmount(){return totalAmount;} public void setTotalAmount(BigDecimal v){totalAmount=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public List<SupplierReceiptItem> getItems(){return items;} public void setItems(List<SupplierReceiptItem> v){items=v;}
}
