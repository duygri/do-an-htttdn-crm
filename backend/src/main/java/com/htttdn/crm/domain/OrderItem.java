package com.htttdn.crm.domain;
import jakarta.persistence.*; import lombok.*; import java.math.BigDecimal;
@Entity @Table(name="order_items") @Getter @Setter @NoArgsConstructor
public class OrderItem { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne @JoinColumn(name="order_id",nullable=false) private Order order; @ManyToOne(optional=false) @JoinColumn(name="product_id") private Product product; private int quantity; @Column(nullable=false,precision=15,scale=2) private BigDecimal unitPrice; }
