package com.tss.grc.module.fraud.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Single fraud case under investigation — Sprint 14.
 *
 * <p>State machine — TRIAGE is recorded as an event on the case (the
 * {@code triagedAt} timestamp plus a TRIAGE_DECISION timeline row) rather
 * than held as a separate status, because triage is a single-decision
 * step. The triage endpoint takes the case from DETECTED directly to the
 * next status:
 * <pre>
 *   DETECTED → INVESTIGATING → CONFIRMED      → CLOSED
 *                            → CLEARED        → CLOSED
 *                            → FALSE_POSITIVE → CLOSED
 *   DETECTED → FALSE_POSITIVE → CLOSED  (triage rules it out)
 * </pre>
 *
 * <p>Every transition is recorded in {@link FraudCaseUpdate} so the
 * audit trail and timeline UI can reconstruct what happened, when,
 * and by whom — matching SAMA Anti-Fraud Framework Art.8 (investigation
 * record-keeping) and ISO 37301 (compliance management) expectations.
 *
 * <p>{@code externalReference} is a human-friendly case number
 * generated on create (format: {@code FR-YYYY-MM-NNNN}).
 */
@Entity
@Table(schema = "fraud", name = "fraud_case")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class FraudCase extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_reference", nullable = false, length = 32)
    private String externalReference;

    @Column(name = "typology_id", nullable = false)
    private Long typologyId;

    /** Cached typology code at case-open time (audit-stable). */
    @Column(name = "typology_code_snapshot", nullable = false, length = 64)
    private String typologyCodeSnapshot;

    @Column(name = "subject_en", nullable = false, length = 256)
    private String subjectEn;

    @Column(name = "subject_ar", nullable = false, length = 256)
    private String subjectAr;

    @Column(name = "narrative_en", columnDefinition = "NVARCHAR(MAX)")
    private String narrativeEn;

    @Column(name = "narrative_ar", columnDefinition = "NVARCHAR(MAX)")
    private String narrativeAr;

    /** {@code DETECTED | INVESTIGATING | CONFIRMED | CLEARED | FALSE_POSITIVE | CLOSED}. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "DETECTED";

    /** {@code CRITICAL | HIGH | MEDIUM | LOW} — copied from typology, may be re-rated in triage. */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "MEDIUM";

    /** {@code DETECTION_SYSTEM | CUSTOMER_REPORT | INTERNAL_REPORT | AUDIT_FINDING | EXTERNAL_PARTY | OTHER}. */
    @Column(name = "detection_source", nullable = false, length = 32)
    private String detectionSource;

    /** Optional reference to a {@code DetectionSystem} (e.g. SIEM rule ID, fraud-monitoring alert ID). */
    @Column(name = "detection_reference", length = 128)
    private String detectionReference;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt = LocalDateTime.now();

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    /** Estimated loss at detection — finalised on confirmation. */
    @Column(name = "estimated_loss_sar", precision = 18, scale = 2)
    private BigDecimal estimatedLossSar;

    /** Confirmed loss after investigation closes. */
    @Column(name = "confirmed_loss_sar", precision = 18, scale = 2)
    private BigDecimal confirmedLossSar;

    @Column(name = "recovered_amount_sar", precision = 18, scale = 2)
    private BigDecimal recoveredAmountSar;

    /** Currency the loss is denominated in (defaults SAR; ISO-4217). */
    @Column(name = "loss_currency", nullable = false, length = 3)
    private String lossCurrency = "SAR";

    /** Number of affected customers (denormalised for dashboard speed). */
    @Column(name = "affected_customer_count", nullable = false)
    private Integer affectedCustomerCount = 0;

    /** Assigned investigator — {@code iam.user.id}. */
    @Column(name = "assigned_investigator_user_id")
    private Long assignedInvestigatorUserId;

    /** Set when status transitions to TRIAGE. */
    @Column(name = "triaged_at")
    private LocalDateTime triagedAt;

    @Column(name = "triaged_by_user_id")
    private Long triagedByUserId;

    /** Set when status transitions to INVESTIGATING. */
    @Column(name = "investigation_started_at")
    private LocalDateTime investigationStartedAt;

    /** Set when investigation reaches terminal CONFIRMED/CLEARED/FALSE_POSITIVE. */
    @Column(name = "outcome_set_at")
    private LocalDateTime outcomeSetAt;

    @Column(name = "outcome_set_by_user_id")
    private Long outcomeSetByUserId;

    /** Set when status transitions to CLOSED. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    /** Closure notes — visible on case detail page. */
    @Column(name = "closure_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String closureNotesEn;

    @Column(name = "closure_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String closureNotesAr;

    /** Whether a SAFIU STR (Suspicious Transaction Report) was filed. */
    @Column(name = "str_filed", nullable = false)
    private Boolean strFiled = false;

    /** SAFIU/GIB filing reference if {@link #strFiled} is true. */
    @Column(name = "str_reference", length = 100)
    private String strReference;

    @Column(name = "str_filed_at")
    private LocalDateTime strFiledAt;

    /** Whether law-enforcement was engaged (CID, MHRSD, etc.). */
    @Column(name = "law_enforcement_engaged", nullable = false)
    private Boolean lawEnforcementEngaged = false;

    @Column(name = "law_enforcement_reference", length = 100)
    private String lawEnforcementReference;
}
