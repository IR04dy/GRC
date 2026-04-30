package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "risk", name = "risk_assessments")
@Getter @Setter @NoArgsConstructor
public class RiskAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "assessor_id")
    private Long assessorId;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate = LocalDate.now();

    @Column(name = "likelihood", nullable = false)
    private Integer likelihood;

    @Column(name = "impact", nullable = false)
    private Integer impact;

    @Column(name = "score", insertable = false, updatable = false)
    private Integer score;

    @Column(name = "assessment_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String assessmentNotesEn;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
