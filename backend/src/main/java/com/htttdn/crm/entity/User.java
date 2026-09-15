package com.htttdn.crm.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
public class User { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String email; @Column(nullable=false) private String fullName; private String phone; private String role="CUSTOMER"; private boolean locked=false; private String preferences; private Instant createdAt=Instant.now(); }
