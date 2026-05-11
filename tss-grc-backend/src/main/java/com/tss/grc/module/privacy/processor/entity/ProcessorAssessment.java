package com.tss.grc.module.privacy.processor.entity;

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

    @Column(name = "overall_outcome", length = 25) private String overallOutcome;
    @Column(name = "risk_score", precision = 4, scale = 1) private BigDecimal riskScore;
    @Column(name = "next_assessment_due") private LocalDate nextAssessmentDue;

    @Column(name = "assessed_at")     private LocalDateTime assessedAt;
    @Column(name = "assessed_by")     private Long          assessedBy;
    @Column(name = "assessor_name", length = 255) private String assessorName;
    @Column(name = "approved_at")     private LocalDateTime approvedAt;
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
