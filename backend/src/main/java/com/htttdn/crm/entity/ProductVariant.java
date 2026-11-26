package com.htttdn.crm.entity;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name="product_variants", uniqueConstraints=@UniqueConstraint(columnNames={"product_id","color","size"}))
public class ProductVariant {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private boolean active=true;
 public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
 @ManyToOne(optional=false) @JoinColumn(name="product_id") @JsonIgnore private Product product;
 @Column(nullable=false,length=100) private String color;
 @Column(nullable=false,length=100) private String size;
 @Column(nullable=false) private int stock;
 public Long getId(){return id;} public Product getProduct(){return product;} public void setProduct(Product v){product=v;}
 public String getColor(){return color;} public void setColor(String v){color=v;}
 public String getSize(){return size;} public void setSize(String v){size=v;}
 public int getStock(){return stock;} public void setStock(int v){stock=v;}
}
