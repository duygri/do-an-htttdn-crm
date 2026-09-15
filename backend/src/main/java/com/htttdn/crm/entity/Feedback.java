package com.htttdn.crm.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="feedback",uniqueConstraints=@UniqueConstraint(columnNames={"customer_id","product_id"})) @Getter @Setter @NoArgsConstructor
public class Feedback { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(optional=false) @JoinColumn(name="customer_id") private User customer; @ManyToOne(optional=false) @JoinColumn(name="product_id") private Product product; @Column(nullable=false) private int rating; @Column(length=4000) private String comment; private String status="NEW"; private String adminResponse; private Instant createdAt=Instant.now(); private Instant processedAt; }
