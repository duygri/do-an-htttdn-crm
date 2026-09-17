package com.htttdn.crm.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.*; import com.fasterxml.jackson.annotation.JsonManagedReference;
@Entity @Table(name="surveys") @Getter @Setter @NoArgsConstructor
public class Survey { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false) private String title; private String description; private String status="DRAFT"; private Instant startsAt; private Instant endsAt; private Instant createdAt=Instant.now(); @JsonManagedReference @OneToMany(mappedBy="survey",cascade=CascadeType.ALL,orphanRemoval=true) private List<SurveyQuestion> questions=new ArrayList<>(); }
