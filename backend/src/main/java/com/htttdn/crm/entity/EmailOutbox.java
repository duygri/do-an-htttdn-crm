package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="email_outbox")
public class EmailOutbox {
    public enum Status { PENDING, PROCESSING, ACCEPTED, FAILED, UNKNOWN }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,unique=true,length=180) public String eventKey;
    @Column(nullable=false,length=320) public String recipient;
    @Column(nullable=false,length=250) public String subject;
    @Column(nullable=false,columnDefinition="text") public String body;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public Status status=Status.PENDING;
    @Column(nullable=false) public int attempts;
    @Column(nullable=false) public Instant createdAt=Instant.now();
    @Column(nullable=false) public Instant updatedAt=Instant.now();
    @Column(nullable=false) public Instant nextAttemptAt=Instant.now();
    public Instant leaseUntil;
    @Column(length=36) public String claimToken;
    @Column(length=80) public String reason;
}
