package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="shipments")
public class Shipment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="order_id",nullable=false,unique=true) private Long orderId;
    @Column(name="carrier_code",nullable=false,length=30) private String carrierCode;
    @Column(name="tracking_code",nullable=false,length=120) private String trackingCode;
    @Column(nullable=false,length=30) private String status="CREATED";
    @Column(length=1000) private String note;
    @Column(name="shipped_at") private Instant shippedAt;
    @Column(name="delivered_at") private Instant deliveredAt;
    @Column(name="created_by") private Long createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    public Shipment(){}
    public Long getId(){return id;} public Long getOrderId(){return orderId;} public void setOrderId(Long v){orderId=v;}
    public String getCarrierCode(){return carrierCode;} public void setCarrierCode(String v){carrierCode=v;}
    public String getTrackingCode(){return trackingCode;} public void setTrackingCode(String v){trackingCode=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public Instant getShippedAt(){return shippedAt;} public void setShippedAt(Instant v){shippedAt=v;}
    public Instant getDeliveredAt(){return deliveredAt;} public void setDeliveredAt(Instant v){deliveredAt=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
