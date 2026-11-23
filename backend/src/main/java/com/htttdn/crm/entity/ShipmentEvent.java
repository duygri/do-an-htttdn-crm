package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="shipment_events")
public class ShipmentEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="shipment_id",nullable=false) private Long shipmentId;
    @Column(nullable=false,length=30) private String status;
    @Column(length=1000) private String note;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt=Instant.now();
    @Column(name="created_by") private Long createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    public ShipmentEvent(){} public ShipmentEvent(Long shipmentId,String status,String note,Long createdBy){this.shipmentId=shipmentId;this.status=status;this.note=note;this.createdBy=createdBy;}
    public Long getId(){return id;} public Long getShipmentId(){return shipmentId;} public String getStatus(){return status;}
    public String getNote(){return note;} public Instant getOccurredAt(){return occurredAt;} public Long getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;}
}
