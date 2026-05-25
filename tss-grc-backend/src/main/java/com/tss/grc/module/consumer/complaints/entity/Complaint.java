package com.tss.grc.module.consumer.complaints.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One customer complaint — Sprint 13 (SAMA Banking Consumer
 * Protection Principles 2021 + CMA Investor Complaints Regulation).
 *
 * <p>The {@link #submissionToken} is the credential the public
 * (anonymous) submitter uses on
 * {@code GET /api/v1/complaints/public/track/{token}} — they receive
 * it on submission and the bank cannot recover it for them. The
 * tracking endpoint returns a sanitised view (no PII).
 *
 * <h3>Status machine</h3>
 * <pre>
 *   RECEIVED            → ACKNOWLEDGED | REJECTED
 *   ACKNOWLEDGED        → UNDER_INVESTIGATION | RESOLVED | REJECTED
 *   UNDER_INVESTIGATION → AWAITING_CUSTOMER | RESOLVED | ESCALATED_TO_SAMA
 *   AWAITING_CUSTOMER   → UNDER_INVESTIGATION | RESOLVED
 *   RESOLVED            → CLOSED | REOPENED
 *   CLOSED              → REOPENED
 *   REOPENED            → UNDER_INVESTIGATION
 *   ESCALATED_TO_SAMA   → CLOSED | RESOLVED
 *   REJECTED            (terminal)
 * </pre>
 *
 * <p>SLA-clock fields {@link #ackSlaDueOn} (5 working days) and
 * {@link #resolutionSlaDueOn} (20 working days) are computed by
 * {@code ComplaintService} on submission via
 * {@code SaudiWorkdayUtil.addBusinessDays(...)} — Friday + Saturday
 * are weekends in KSA, so the JDK default DayOfWeek.SATURDAY/SUNDAY
 * convention is wrong.
 */
@Entity
@Table(schema = "consumer", name = "complaint")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Complaint extends BaseEntity {

    /** Format: CMP-YYYY-NNNNNN — unique per tenant. */
    @Column(name = "complaint_no", nullable = false, length = 32)
    private String complaintNo;

    /** Opaque per-row token returned to the anonymous public submitter. */
    @Column(name = "submission_token", nullable = false, updatable = false,
            columnDefinition = "uniqueidentifier")
    private UUID submissionToken = UUID.randomUUID();

    /** WEB | EMAIL | PHONE | BRANCH | SAMA_REFERRAL | SOCIAL_MEDIA. */
    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    /** 13 codes — see V199 header. */
    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "subcategory", length = 64)
    private String subcategory;

    /** LOW | MEDIUM | HIGH | CRITICAL. */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "MEDIUM";

    /** See lifecycle state machine in class Javadoc. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "RECEIVED";

    @Column(name = "customer_name_en", length = 256)
    private String customerNameEn;

    @Column(name = "customer_name_ar", length = 256)
    private String customerNameAr;

    /** NATIONAL_ID | IQAMA | PASSPORT | CR. */
    @Column(name = "customer_id_type", length = 16)
    private String customerIdType;

    @Column(name = "customer_id_last4", length = 4)
    private String customerIdLast4;

    @Column(name = "customer_phone", length = 32)
    private String customerPhone;

    @Column(name = "customer_email", length = 256)
    private String customerEmail;

    @Column(name = "customer_city", length = 64)
    private String customerCity;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "account_last4", length = 4)
    private String accountLast4;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "submitted_on", nullable = false)
    private LocalDateTime submittedOn = LocalDateTime.now();

    @Column(name = "acknowledged_on")
    private LocalDateTime acknowledgedOn;

    @Column(name = "acknowledged_by", length = 128)
    private String acknowledgedBy;

    /** Submission + 5 working days (SAMA Principle 6). */
    @Column(name = "ack_sla_due_on")
    private LocalDateTime ackSlaDueOn;

    @Column(name = "resolved_on")
    private LocalDateTime resolvedOn;

    @Column(name = "resolution_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String resolutionSummaryEn;

    @Column(name = "resolution_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String resolutionSummaryAr;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    /** Submission + 20 working days (SAMA Principle 6). */
    @Column(name = "resolution_sla_due_on")
    private LocalDateTime resolutionSlaDueOn;

    @Column(name = "escalated_to_sama_on")
    private LocalDateTime escalatedToSamaOn;

    @Column(name = "sama_reference_no", length = 64)
    private String samaReferenceNo;

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "root_cause_code", length = 64)
    private String rootCauseCode;

    @Column(name = "compensation_amount_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal compensationAmountSar = BigDecimal.ZERO;

    @Column(name = "lessons_learned", columnDefinition = "NVARCHAR(MAX)")
    private String lessonsLearned;

    /** Tenant scope. Matches the {@code org_id} column in the schema. */
    @Column(name = "org_id", nullable = false)
    private Long organisationId;
}
