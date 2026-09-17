package com.htttdn.crm.domain;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.*;
@Entity @Table(name="surveys") @Getter @Setter @NoArgsConstructor
public class Survey { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false) private String title; private String description; private String status="DRAFT"; private Instant startsAt; private Instant endsAt; private Instant createdAt=Instant.now(); @OneToMany(mappedBy="survey",cascade=CascadeType.ALL,orphanRemoval=true) private List<SurveyQuestion> questions=new ArrayList<>(); }
