package com.htttdn.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="sales_invoice_items")
public class SalesInvoiceItem {
    private Long variantId;
    public Long getVariantId(){return variantId;}
    public void setVariantId(Long v){variantId=v;}

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @JsonBackReference @ManyToOne(optional=false) @JoinColumn(name="invoice_id") private SalesInvoice invoice;
    @ManyToOne @JoinColumn(name="product_id") private Product product;
    @Column(name="product_name",nullable=false,length=150) private String productName;
    @Column(length=1000) private String size;
    @Column(length=1000) private String color;
    @Column(nullable=false) private int quantity;
    @Column(name="unit_price",nullable=false,precision=15,scale=2) private BigDecimal unitPrice;
    @Column(name="line_total",nullable=false,precision=15,scale=2) private BigDecimal lineTotal;

    public SalesInvoiceItem(){}
    public Long getId(){return id;}
    public SalesInvoice getInvoice(){return invoice;} public void setInvoice(SalesInvoice v){invoice=v;}
    public Product getProduct(){return product;} public void setProduct(Product v){product=v;}
    public String getProductName(){return productName;} public void setProductName(String v){productName=v;}
    public String getSize(){return size;} public void setSize(String v){size=v;}
    public String getColor(){return color;} public void setColor(String v){color=v;}
    public int getQuantity(){return quantity;} public void setQuantity(int v){quantity=v;}
    public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
    public BigDecimal getLineTotal(){return lineTotal;} public void setLineTotal(BigDecimal v){lineTotal=v;}
}
