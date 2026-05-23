package com.tss.grc.module.tprm.bcp_attestation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 10-D — One vendor's response to a {@link VendorBcpCampaign}.
 *
 * <p>Materialised at {@code openCampaign()} time for every vendor that
 * matches the parent campaign's {@code mandatoryThreshold}. Vendors
 * receive the URL containing {@link #submissionToken} and submit their
 * attestation through the vendor portal; the TPRM Lead then reviews
 * and accepts / rejects / marks out-of-scope.
 *
 * <h3>Per-row state machine</h3>
 * <pre>
 *   PENDING_INVITE  → INVITED (campaign opened)
 *   INVITED         → REMINDED | SUBMITTED | NA_OUT_OF_SCOPE | OVERDUE
 *   REMINDED        → SUBMITTED | NA_OUT_OF_SCOPE | OVERDUE | ESCALATED
 *   OVERDUE         → SUBMITTED | NA_OUT_OF_SCOPE | ESCALATED
 *   SUBMITTED       → ACCEPTED | REJECTED | NA_OUT_OF_SCOPE
 *   ACCEPTED        (terminal)
 *   REJECTED        → INVITED (re-invite allowed)
 *   NA_OUT_OF_SCOPE (terminal)
 * </pre>
 *
 * <p>{@link #rtoBreach} / {@link #mtpdBreach} flags are computed by
 * the service when a vendor submits — if the attested RTO/MTPD exceeds
 * the contractual SLA snapshot, the row is flagged for the reviewer's
 * attention even though the workflow still progresses normally.
 */
@Entity
@Table(schema = "tprm", name = "vendor_bcp_attestation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class VendorBcpAttestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    /** Opaque per-row token embedded in the vendor-portal URL. */
    @Column(name = "submission_token", nullable = false)
    private UUID submissionToken = UUID.randomUUID();

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "first_reminder_at")
    private LocalDateTime firstReminderAt;

    @Column(name = "second_reminder_at")
    private LocalDateTime secondReminderAt;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalated_to_user_id")
    private Long escalatedToUserId;

    @Column(name = "overdue_at")
    private LocalDateTime overdueAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submitted_by_contact_email", length = 255)
    private String submittedByContactEmail;

    @Column(name = "submitted_by_contact_name", length = 255)
    private String submittedByContactName;

    @Column(name = "rto_hours_attested")
    private Integer rtoHoursAttested;

    @Column(name = "mtpd_hours_attested")
    private Integer mtpdHoursAttested;

    /** Snapshot of contracted RTO at invite time — used for breach detection. */
    @Column(name = "contractual_rto_hours")
    private Integer contractualRtoHours;

    @Column(name = "contractual_mtpd_hours")
    private Integer contractualMtpdHours;

    @Column(name = "rto_breach", nullable = false)
    private Boolean rtoBreach = false;

    @Column(name = "mtpd_breach", nullable = false)
    private Boolean mtpdBreach = false;

    @Column(name = "has_tested_bcp")
    private Boolean hasTestedBcp;

    @Column(name = "last_bcp_test_date")
    private LocalDate lastBcpTestDate;

    @Column(name = "last_dr_test_date")
    private LocalDate lastDrTestDate;

    @Column(name = "subprocessor_dependencies", columnDefinition = "NVARCHAR(MAX)")
    private String subprocessorDependencies;

    @Column(name = "evidence_file_path", length = 500)
    private String evidenceFilePath;

    @Column(name = "evidence_file_name", length = 255)
    private String evidenceFileName;

    /**
     * PENDING_INVITE | INVITED | REMINDED | OVERDUE | SUBMITTED |
     * ACCEPTED | REJECTED | NA_OUT_OF_SCOPE.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING_INVITE";

    @Column(name = "reviewer_comment_en", length = 1000)
    private String reviewerCommentEn;

    @Column(name = "reviewer_comment_ar", length = 1000)
    private String reviewerCommentAr;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_by_name", length = 255)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

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
