package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="order_refunds",uniqueConstraints=@UniqueConstraint(name="uq_order_refund_key",columnNames={"order_id","request_key"}))
public class OrderRefund {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="order_id",nullable=false) private Long orderId;
    @Column(name="reason_type",nullable=false,length=10) private String reasonType;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=10) private String status="PENDING";
    @Column(name="request_key",nullable=false,length=100) private String requestKey;
    @Column(length=1000) private String note;
    @Column(name="processing_note",length=1000) private String processingNote;
    @Column(name="bank_reference",length=120) private String bankReference;
    @Column(length=20) private String method="BANK_TRANSFER";
    @Column(name="evidence_url",length=500) private String evidenceUrl;
    @Column(name="requested_by") private Long requestedBy;
    @Column(name="processed_by") private Long processedBy;
    @Column(name="requested_at",nullable=false) private Instant requestedAt=Instant.now();
    @Column(name="processed_at") private Instant processedAt;
    public OrderRefund(){}
    public Long getId(){return id;} public Long getOrderId(){return orderId;} public void setOrderId(Long v){orderId=v;}
    public String getReasonType(){return reasonType;} public void setReasonType(String v){reasonType=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getRequestKey(){return requestKey;} public void setRequestKey(String v){requestKey=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public String getProcessingNote(){return processingNote;} public void setProcessingNote(String v){processingNote=v;}
    public String getBankReference(){return bankReference;} public void setBankReference(String v){bankReference=v;}
    public String getMethod(){return method;} public void setMethod(String v){method=v;}
    public String getEvidenceUrl(){return evidenceUrl;} public void setEvidenceUrl(String v){evidenceUrl=v;}
    public Long getRequestedBy(){return requestedBy;} public void setRequestedBy(Long v){requestedBy=v;}
    public Long getProcessedBy(){return processedBy;} public void setProcessedBy(Long v){processedBy=v;}
    public Instant getRequestedAt(){return requestedAt;} public Instant getProcessedAt(){return processedAt;} public void setProcessedAt(Instant v){processedAt=v;}
}
