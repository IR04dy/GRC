package com.tss.grc.module.tprm.outsourcing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — A single due-diligence assessment of an outsourcing
 * arrangement. Multiple records per arrangement are allowed (e.g.
 * onboarding, annual refresh, post-incident re-assessment).
 *
 * <p>Six sub-scores (0-100); composite is the simple mean. The
 * service layer computes the composite — never accept it from the
 * client.
 */
@Entity
@Table(schema = "tprm", name = "outsourcing_due_diligence")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OutsourcingDueDiligence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "arrangement_id", nullable = false)
    private Long arrangementId;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "assessor_user_id", nullable = false)
    private Long assessorUserId;

    @Column(name = "financial_health_score", nullable = false)
    private Integer financialHealthScore;

    @Column(name = "operational_capability_score", nullable = false)
    private Integer operationalCapabilityScore;

    @Column(name = "cybersecurity_score", nullable = false)
    private Integer cybersecurityScore;

    @Column(name = "bcm_score", nullable = false)
    private Integer bcmScore;

    @Column(name = "pdpl_compliance_score", nullable = false)
    private Integer pdplComplianceScore;

    @Column(name = "reputation_score", nullable = false)
    private Integer reputationScore;

    @Column(name = "composite_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal compositeScore;

    /** APPROVE | CONDITIONAL | REJECT. */
    @Column(name = "recommendation", nullable = false, length = 20)
    private String recommendation;

    @Column(name = "rationale_en", nullable = false, length = 2000)
    private String rationaleEn;

    @Column(name = "rationale_ar", nullable = false, length = 2000)
    private String rationaleAr;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
