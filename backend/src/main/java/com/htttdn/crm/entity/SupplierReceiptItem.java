package com.htttdn.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="supplier_receipt_items")
public class SupplierReceiptItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @JsonBackReference @ManyToOne(optional=false) @JoinColumn(name="receipt_id") private SupplierReceipt receipt;
    @ManyToOne(optional=false) @JoinColumn(name="product_id") private Product product;
    @Column(nullable=false) private int quantity;
    @Column(name="unit_cost",nullable=false,precision=15,scale=2) private BigDecimal unitCost;
    @Column(name="line_total",nullable=false,precision=15,scale=2) private BigDecimal lineTotal;

    public SupplierReceiptItem(){}
    public SupplierReceiptItem(SupplierReceipt receipt,Product product,int quantity,BigDecimal unitCost){
        this.receipt=receipt; this.product=product; this.quantity=quantity; this.unitCost=unitCost;
        this.lineTotal=unitCost.multiply(BigDecimal.valueOf(quantity));
    }
    public Long getId(){return id;}
    public SupplierReceipt getReceipt(){return receipt;} public void setReceipt(SupplierReceipt v){receipt=v;}
    public Product getProduct(){return product;} public void setProduct(Product v){product=v;}
    public int getQuantity(){return quantity;} public void setQuantity(int v){quantity=v;}
    public BigDecimal getUnitCost(){return unitCost;} public void setUnitCost(BigDecimal v){unitCost=v;}
    public BigDecimal getLineTotal(){return lineTotal;} public void setLineTotal(BigDecimal v){lineTotal=v;}
}
