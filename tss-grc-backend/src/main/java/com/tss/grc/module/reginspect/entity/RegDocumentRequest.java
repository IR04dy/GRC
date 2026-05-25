package com.tss.grc.module.reginspect.entity;

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
 * One document request raised by the regulator during an inspection.
 *
 * <p>Lifecycle:
 * <pre>
 *   OPEN → IN_PROGRESS → SUBMITTED
 *                     ↘ OVERDUE (computed when due_date &lt; today &amp;&amp; status != SUBMITTED)
 * </pre>
 *
 * <p>Typical SAMA SLA is 3-5 business days; for high-volume requests the
 * inspector negotiates a batched timeline. The {@code dueDate} is what the
 * regulator agreed to; the platform tracks slippage via the F8 OVERDUE
 * trigger.
 */
@Entity
@Table(schema = "reginspect", name = "reginspect_document_request")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegDocumentRequest extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    /** External reference (e.g. "SAMA-DR-2026-Q2-001-014"). */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** Request title — usually short ("Q2-2026 SADAD reconciliation logs"). */
    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    /** Full request text (regulator's wording). */
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** {@code OPEN | IN_PROGRESS | SUBMITTED}. */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    /** Negotiated submission deadline. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Owner — typically Compliance or Risk Manager. */
    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    /** Where the answer is stored (SharePoint URL, S3 key, etc.). */
    @Column(name = "evidence_reference", length = 512)
    private String evidenceReference;

    /** Submission notes — what was sent + any caveats. */
    @Column(name = "submission_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String submissionNotesEn;

    @Column(name = "submission_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String submissionNotesAr;
}
