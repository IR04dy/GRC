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
 * Sharia Supervisory Board resolution (fatwa) — a formal opinion of the
 * SSB on a product, transaction, or operational matter.
 *
 * <p>Lifecycle:
 * <pre>
 *   DRAFT → SSB_REVIEW → APPROVED → ACTIVE → SUPERSEDED
 *                     → REJECTED (terminal)
 * </pre>
 *
 * <p>Resolutions are referenced by every Islamic-banking product and
 * are the audit trail SAMA and the external Sharia auditor inspect
 * first. They typically cite Quran / Sunnah / Ijma' / Qiyas evidence
 * inline (rationaleEn/Ar fields).
 */
@Entity
@Table(schema = "sharia", name = "sharia_resolution")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ShariaResolution extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Resolution reference, e.g. {@code RES-2026-014}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** Subject area: {@code MURABAHA | IJARA | TAWARRUQ | SUKUK | MUDARABA | MUSHARAKA | TRANSACTION | OPERATIONAL | OTHER}. */
    @Column(name = "subject_area", nullable = false, length = 32)
    private String subjectArea;

    @Column(name = "title_en", nullable = false, length = 512)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 512)
    private String titleAr;

    @Column(name = "body_en", columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    @Column(name = "body_ar", columnDefinition = "NVARCHAR(MAX)")
    private String bodyAr;

    /** Quran / Sunnah / Ijma' / Qiyas evidence cited. */
    @Column(name = "rationale_en", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleEn;

    @Column(name = "rationale_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleAr;

    /** Status: {@code DRAFT | SSB_REVIEW | APPROVED | ACTIVE | SUPERSEDED | REJECTED}. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "ssb_review_started_at")
    private LocalDateTime ssbReviewStartedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "supersedes_resolution_id")
    private Long supersedesResolutionId;

    /** Comma-separated scholar ids who signed the resolution. */
    @Column(name = "signing_scholars", length = 256)
    private String signingScholars;

    /** Optional link to the SSB meeting that approved this. */
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonEn;

    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonAr;
}
