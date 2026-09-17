package com.htttdn.crm.entity;
import jakarta.persistence.*; import lombok.*; import java.math.BigDecimal; import java.time.Instant;
@Entity @Table(name="products") @Getter @Setter @NoArgsConstructor
public class Product { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false) private String name; private String category; @Column(length=4000) private String description; @Column(nullable=false,precision=15,scale=2) private BigDecimal price; private int stock; private String imageUrl; private boolean active=true; private Instant createdAt=Instant.now(); private Instant updatedAt=Instant.now(); }
