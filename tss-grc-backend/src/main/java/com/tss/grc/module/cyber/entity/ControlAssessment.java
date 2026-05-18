package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "CyberControlAssessment")
@Table(schema = "cyber", name = "control_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ControlAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "control_id", nullable = false)
    private Long controlId;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "assessor_name")
    private String assessorName;

    @Column(name = "reviewer_name")
    private String reviewerName;

    private String result;

    @Column(name = "maturity_score")
    private Integer maturityScore;

    @Column(name = "gap_identified")
    private Boolean gapIdentified;

    @Column(name = "gap_description", columnDefinition = "NVARCHAR(MAX)")
    private String gapDescription;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "assessor_comments", columnDefinition = "NVARCHAR(MAX)")
    private String assessorComments;

    @Column(name = "reviewer_comments", columnDefinition = "NVARCHAR(MAX)")
    private String reviewerComments;

    @Column(name = "evidence_count")
    private int evidenceCount;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "capa_id")
    private Long capaId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (externalId == null) externalId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
