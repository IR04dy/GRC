package com.tss.grc.module.tprm.cyber.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cyber-specific risk assessment of a third party.
 *
 * <p>Status machine: {@code DRAFT → IN_REVIEW → APPROVED → RETIRED}.
 * See V53 migration for column commentary.
 */
@Entity
@Table(schema = "tprm", name = "third_party_cyber_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ThirdPartyCyberAssessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id",     length = 64)     private String externalId;
    @Column(name = "assessment_code", length = 40)     private String assessmentCode;

    @Column(name = "vendor_id")                          private Long vendorId;
    @Column(name = "vendor_name",      nullable = false, length = 255) private String vendorName;
    @Column(name = "vendor_country",   length = 3)      private String vendorCountry;

    @Column(name = "inherent_cyber_risk", nullable = false, length = 20)
    private String inherentCyberRisk;

    @Column(name = "scope_description_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String scopeDescriptionEn;
    @Column(name = "scope_description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String scopeDescriptionAr;

    @Column(name = "certifications",          columnDefinition = "NVARCHAR(MAX)") private String certifications;
    @Column(name = "certifications_expiry",   columnDefinition = "NVARCHAR(MAX)") private String certificationsExpiry;

    @Column(name = "external_posture_score")     private Integer externalPostureScore;
    @Column(name = "questionnaire_score")        private Integer questionnaireScore;
    @Column(name = "questionnaire_completed_at") private LocalDateTime questionnaireCompletedAt;

    @Column(name = "breach_history_24m",        nullable = false) private Short breachHistory24m = 0;
    @Column(name = "last_known_breach_date")    private LocalDate lastKnownBreachDate;
    @Column(name = "last_known_breach_summary", columnDefinition = "NVARCHAR(MAX)")
    private String lastKnownBreachSummary;

    @Column(name = "critical_vulns_open")       private Integer criticalVulnsOpen;
    @Column(name = "patch_sla_compliance_pct")  private Integer patchSlaCompliancePct;
    @Column(name = "mfa_enforced",        nullable = false) private Short mfaEnforced = 0;
    @Column(name = "pen_test_within_12m", nullable = false) private Short penTestWithin12m = 0;

    @Column(name = "residual_risk_tier", nullable = false, length = 20)
    private String residualRiskTier = "MODERATE";
    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "findings_en",       columnDefinition = "NVARCHAR(MAX)") private String findingsEn;
    @Column(name = "findings_ar",       columnDefinition = "NVARCHAR(MAX)") private String findingsAr;
    @Column(name = "treatment_plan_en", columnDefinition = "NVARCHAR(MAX)") private String treatmentPlanEn;
    @Column(name = "treatment_plan_ar", columnDefinition = "NVARCHAR(MAX)") private String treatmentPlanAr;
    @Column(name = "next_review_due")   private LocalDate nextReviewDue;

    @Column(name = "assessed_at")  private LocalDateTime assessedAt;
    @Column(name = "assessor_name", length = 255) private String assessorName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")  private LocalDateTime approvedAt;
    @Column(name = "approver_name", length = 255) private String approverName;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active",  nullable = false) private Short isActive  = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
