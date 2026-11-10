package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Hóa đơn bán hàng, chụp lại thông tin tại thời điểm phát hành để không đổi theo đơn hàng sau này. */
@Entity @Table(name="sales_invoices")
public class SalesInvoice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="invoice_number",nullable=false,unique=true,length=80) private String invoiceNumber;
    @ManyToOne(optional=false) @JoinColumn(name="order_id",unique=true) private Order order;
    @ManyToOne(optional=false) @JoinColumn(name="customer_id") private User customer;
    @Column(name="issued_at",nullable=false) private Instant issuedAt=Instant.now();
    @Column(name="seller_name",nullable=false,length=150) private String sellerName;
    @Column(name="seller_address",length=1000) private String sellerAddress;
    @Column(name="seller_phone",length=40) private String sellerPhone;
    @Column(name="seller_tax_code",length=40) private String sellerTaxCode;
    @Column(name="buyer_name",nullable=false,length=150) private String buyerName;
    @Column(name="buyer_email",length=255) private String buyerEmail;
    @Column(name="buyer_phone",length=40) private String buyerPhone;
    @Column(name="delivery_address",length=1000) private String deliveryAddress;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal subtotal=BigDecimal.ZERO;
    @Column(name="discount_amount",nullable=false,precision=15,scale=2) private BigDecimal discountAmount=BigDecimal.ZERO;
    @Column(name="total_amount",nullable=false,precision=15,scale=2) private BigDecimal totalAmount=BigDecimal.ZERO;
    @Column(name="payment_method",length=30) private String paymentMethod;
    @Column(name="payment_status",length=30) private String paymentStatus;
    @Column(length=1000) private String note;
    @Column(name="created_by") private Long createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @OneToMany(mappedBy="invoice",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.EAGER)
    @OrderBy("id ASC") private List<SalesInvoiceItem> items=new ArrayList<>();

    public SalesInvoice(){}
    public Long getId(){return id;}
    public String getInvoiceNumber(){return invoiceNumber;} public void setInvoiceNumber(String v){invoiceNumber=v;}
    public Order getOrder(){return order;} public void setOrder(Order v){order=v;}
    public User getCustomer(){return customer;} public void setCustomer(User v){customer=v;}
    public Instant getIssuedAt(){return issuedAt;} public void setIssuedAt(Instant v){issuedAt=v;}
    public String getSellerName(){return sellerName;} public void setSellerName(String v){sellerName=v;}
    public String getSellerAddress(){return sellerAddress;} public void setSellerAddress(String v){sellerAddress=v;}
    public String getSellerPhone(){return sellerPhone;} public void setSellerPhone(String v){sellerPhone=v;}
    public String getSellerTaxCode(){return sellerTaxCode;} public void setSellerTaxCode(String v){sellerTaxCode=v;}
    public String getBuyerName(){return buyerName;} public void setBuyerName(String v){buyerName=v;}
    public String getBuyerEmail(){return buyerEmail;} public void setBuyerEmail(String v){buyerEmail=v;}
    public String getBuyerPhone(){return buyerPhone;} public void setBuyerPhone(String v){buyerPhone=v;}
    public String getDeliveryAddress(){return deliveryAddress;} public void setDeliveryAddress(String v){deliveryAddress=v;}
    public BigDecimal getSubtotal(){return subtotal;} public void setSubtotal(BigDecimal v){subtotal=v;}
    public BigDecimal getDiscountAmount(){return discountAmount;} public void setDiscountAmount(BigDecimal v){discountAmount=v;}
    public BigDecimal getTotalAmount(){return totalAmount;} public void setTotalAmount(BigDecimal v){totalAmount=v;}
    public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String v){paymentMethod=v;}
    public String getPaymentStatus(){return paymentStatus;} public void setPaymentStatus(String v){paymentStatus=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public List<SalesInvoiceItem> getItems(){return items;} public void setItems(List<SalesInvoiceItem> v){items=v;}
}
