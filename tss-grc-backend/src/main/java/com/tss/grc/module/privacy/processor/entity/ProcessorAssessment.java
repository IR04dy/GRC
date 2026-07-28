package com.tss.grc.module.privacy.processor.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PDPL Art. 28 processor due-diligence assessment.
 *
 * <p>Eight named controls (c1..c8) plus an overall outcome and risk score.
 * Status machine: {@code IN_PROGRESS → COMPLETED → APPROVED | REJECTED}.
 */
@Entity
@Table(schema = "privacy", name = "processor_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ProcessorAssessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id",     length = 64)     private String externalId;
    @Column(name = "assessment_code", length = 40)     private String assessmentCode;

    @Column(name = "processor_vendor_id") private Long processorVendorId;
    @Column(name = "processor_name", nullable = false, length = 255) private String processorName;
    @Column(name = "processor_country", length = 3) private String processorCountry;
    @Column(name = "processing_scope_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String processingScopeEn;
    @Column(name = "processing_scope_ar", columnDefinition = "NVARCHAR(MAX)") private String processingScopeAr;

    @Column(name = "assessment_type", nullable = false, length = 30) private String assessmentType;

    @Column(name = "c1_written_instructions", length = 25) private String c1WrittenInstructions;
    @Column(name = "c2_confidentiality",      length = 25) private String c2Confidentiality;
    @Column(name = "c3_security_measures",    length = 25) private String c3SecurityMeasures;
    @Column(name = "c4_sub_processor_auth",   length = 25) private String c4SubProcessorAuth;
    @Column(name = "c5_breach_notification",  length = 25) private String c5BreachNotification;
    @Column(name = "c6_dsr_assistance",       length = 25) private String c6DsrAssistance;
    @Column(name = "c7_deletion_or_return",   length = 25) private String c7DeletionOrReturn;
    @Column(name = "c8_audit_rights",         length = 25) private String c8AuditRights;

    @Column(name = "evidence_refs",       columnDefinition = "NVARCHAR(MAX)") private String evidenceRefs;
    @Column(name = "findings_en",         columnDefinition = "NVARCHAR(MAX)") private String findingsEn;
    @Column(name = "findings_ar",         columnDefinition = "NVARCHAR(MAX)") private String findingsAr;
    @Column(name = "recommendations_en",  columnDefinition = "NVARCHAR(MAX)") private String recommendationsEn;
    @Column(name = "recommendations_ar",  columnDefinition = "NVARCHAR(MAX)") private String recommendationsAr;

    // F2-22b(iv): the outcome and the score are DERIVED by scoreAndComplete() from the eight
    // c1..c8 control outcomes — FAIL anywhere ⇒ FAIL/8.0, otherwise 1.0 + 1.5 per condition.
    // Until this fix POST /api/privacy/processor-assessments saved the body wholesale, so a
    // caller could file a processor as PASS with a 1.0 risk score while every one of the eight
    // controls was still null: live-proven (201, status=APPROVED, outcome=PASS, c1=NULL).
    // That is the PDPL Art. 28 due-diligence record for a data processor, so the outcome has to
    // come from the assessment or it means nothing. Still serialised — the register renders both.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "overall_outcome", length = 25) private String overallOutcome;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "risk_score", precision = 4, scale = 1) private BigDecimal riskScore;
    @Column(name = "next_assessment_due") private LocalDate nextAssessmentDue;

    // F2-22b(iv): who assessed, who approved, and when — all stamped from the authenticated
    // principal by scoreAndComplete()/approve(). assessedBy and assessorName had NO setter
    // anywhere in the platform before this slice, which is why the register's
    // "assessed on <date> (<assessor>)" line always rendered "—".
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "assessed_at")     private LocalDateTime assessedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "assessed_by")     private Long          assessedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "assessor_name", length = 255) private String assessorName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")     private LocalDateTime approvedAt;
    // The companion of approvedAt, and the field that actually appears in the register.
    // approvedAt was made READ_ONLY by tranche (ii) while this one was left bindable — the same
    // miss as decidedByName in tranche (iii). A display name is easier to forge convincingly
    // than a user id, so the name is the more important half, not the lesser one.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approver_name", length = 255) private String approverName;

    @Column(nullable = false, length = 20) private String status = "IN_PROGRESS";

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
