package com.tss.grc.module.sharia.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Finding raised by the Internal Sharia Audit function during a
 * periodic review of an Islamic-banking product line, branch, or
 * back-office operation.
 *
 * <p>Lifecycle:
 * <pre>
 *   OPEN → REMEDIATION → VERIFIED → CLOSED
 * </pre>
 *
 * <p>HIGH-severity findings trigger AR-253 Board-Audit-Committee
 * escalation within 30 days per SAMA §5.7.
 */
@Entity
@Table(schema = "sharia", name = "sharia_audit_finding")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ShariaAuditFinding extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, e.g. {@code SAF-2026-008}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** Audit cycle id (typically annual; quarterly for HIGH-risk products). */
    @Column(name = "audit_cycle_label", length = 64)
    private String auditCycleLabel;

    /** Severity: {@code HIGH | MEDIUM | LOW}. */
    @Column(name = "severity", nullable = false, length = 12)
    private String severity = "MEDIUM";

    /** Status: {@code OPEN | REMEDIATION | VERIFIED | CLOSED}. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "title_en", nullable = false, length = 512)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 512)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "root_cause_en", columnDefinition = "NVARCHAR(MAX)")
    private String rootCauseEn;

    @Column(name = "root_cause_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rootCauseAr;

    @Column(name = "remediation_plan_en", columnDefinition = "NVARCHAR(MAX)")
    private String remediationPlanEn;

    @Column(name = "remediation_plan_ar", columnDefinition = "NVARCHAR(MAX)")
    private String remediationPlanAr;

    @Column(name = "linked_resolution_id")
    private Long linkedResolutionId;

    /** Product line under audit, e.g. {@code Personal Murabaha Financing}. */
    @Column(name = "product_line", length = 128)
    private String productLine;

    @Column(name = "raised_at")
    private LocalDateTime raisedAt;

    @Column(name = "remediation_due_at")
    private LocalDate remediationDueAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;
}
