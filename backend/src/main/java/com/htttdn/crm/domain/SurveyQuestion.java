package com.htttdn.crm.domain;
import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="survey_questions") @Getter @Setter @NoArgsConstructor
public class SurveyQuestion { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne @JoinColumn(name="survey_id",nullable=false) private Survey survey; @Column(nullable=false,length=1000) private String text; private String type="TEXT"; private int displayOrder; private String optionsJson; }
