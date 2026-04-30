package com.tss.grc.module.scm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "ScmVendorAssessment")
@Table(schema = "scm", name = "vendor_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class VendorAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "assessment_type", length = 50)
    private String assessmentType = "INITIAL";

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "next_assessment_date")
    private LocalDate nextAssessmentDate;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "risk_rating", length = 20)
    private String riskRating;

    @Column(name = "status", length = 30)
    private String status = "DRAFT";

    @Column(name = "assessor_id")
    private Long assessorId;

    @Column(name = "findings", columnDefinition = "NVARCHAR(MAX)")
    private String findings;

    @Column(name = "recommendations", columnDefinition = "NVARCHAR(MAX)")
    private String recommendations;

    @Column(name = "escalated_to_risk", nullable = false)
    private Boolean escalatedToRisk = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
