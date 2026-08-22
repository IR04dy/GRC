package com.tss.grc.module.privacy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "privacy", name = "data_breaches")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DataBreach {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    /**
     * F2-271 (V387) — Arabic title. House rule 7: a user-visible record carries {@code *_en}
     * AND {@code *_ar}, and Arabic is the PRIMARY language, so a PDPL Article 20 breach that
     * could only be titled in English was the worse half of that rule — the column did not
     * exist, so the Arabic could not be recorded at all.
     *
     * <p>⚠ Deliberately NOT {@code @NotBlank}, and the reason is F2-30's: bean validation
     * fires on UPDATE as well as insert, so requiring Arabic would make every breach that
     * predates V387 uneditable until somebody supplies it — and nothing can invent Arabic
     * governance text. Its existence is what makes the pair VISIBLE to
     * {@code BilingualInvariantRatchetTest}, which is the mechanism by which the constraint
     * can be added later once the rows carry Arabic.
     */
    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    /** F2-271 (V387) — Arabic description; see {@link #titleAr} for why it is not mandatory. */
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "breach_type", nullable = false, length = 100)
    private String breachType;

    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt;

    @Column(name = "data_subjects_affected_count")
    private Integer dataSubjectsAffectedCount;

    @Column(name = "pdpc_notification_required", nullable = false)
    private Boolean pdpcNotificationRequired = false;

    @Column(name = "pdpc_notified_at")
    private LocalDateTime pdpcNotifiedAt;

    /**
     * SDAIA notification workflow status (Open Issue #006).
     *
     * <p>PDPL Article 31 requires notification of personal-data breaches to
     * SDAIA (formerly PDPC) within 72 hours of awareness. SDAIA has no
     * machine-readable API — the workflow is manual through the SDAIA portal
     * at {@code https://www.sdaia.gov.sa/}. This field tracks the state of
     * that manual submission.
     *
     * <p>Values: {@code NOT_REPORTED | IN_PREPARATION | SUBMITTED | ACKNOWLEDGED}.
     */
    @Column(name = "sdaia_notification_status", nullable = false, length = 40)
    private String sdaiaNotificationStatus = "NOT_REPORTED";

    /** Reference number returned by the SDAIA portal acknowledgement. */
    @Column(name = "sdaia_submission_ref", length = 255)
    private String sdaiaSubmissionRef;

    /**
     * Timestamp the submission was posted to SDAIA. Discharges the PDPL
     * Article 31 72-hour clock measured from {@link #discoveredAt}.
     */
    @Column(name = "sdaia_submitted_at")
    private LocalDateTime sdaiaSubmittedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "OPEN";

    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_by")
    private Long deletedBy;
}
