package com.htttdn.crm.entity;

import jakarta.persistence.*;

@Entity @Table(name="shipping_carriers")
public class ShippingCarrier {
    @Id @Column(name="code",length=30) private String code;
    @Column(nullable=false,length=120) private String name;
    @Column(name="tracking_url_template",length=300) private String trackingUrlTemplate;
    @Column(nullable=false) private boolean active=true;
    @Column(name="sort_order",nullable=false) private int sortOrder;
    public ShippingCarrier(){}
    public ShippingCarrier(String code,String name,String trackingUrlTemplate,int sortOrder){this.code=code;this.name=name;this.trackingUrlTemplate=trackingUrlTemplate;this.sortOrder=sortOrder;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getTrackingUrlTemplate(){return trackingUrlTemplate;} public void setTrackingUrlTemplate(String v){trackingUrlTemplate=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;}
}
