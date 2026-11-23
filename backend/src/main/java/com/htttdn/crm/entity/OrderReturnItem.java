package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="order_return_items",uniqueConstraints=@UniqueConstraint(name="uq_return_item",columnNames={"order_id","order_item_id"}))
public class OrderReturnItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="order_id",nullable=false) private Long orderId;
    @Column(name="order_item_id",nullable=false) private Long orderItemId;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false,length=20) private String resolution="REFUND";
    @Column(name="restocked_at") private Instant restockedAt;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    public OrderReturnItem(){} public OrderReturnItem(Long orderId,Long orderItemId,int quantity,String resolution){this.orderId=orderId;this.orderItemId=orderItemId;this.quantity=quantity;this.resolution=resolution;}
    public Long getId(){return id;} public Long getOrderId(){return orderId;} public Long getOrderItemId(){return orderItemId;}
    public int getQuantity(){return quantity;} public String getResolution(){return resolution;}
    public Instant getRestockedAt(){return restockedAt;} public void setRestockedAt(Instant v){restockedAt=v;}
}
