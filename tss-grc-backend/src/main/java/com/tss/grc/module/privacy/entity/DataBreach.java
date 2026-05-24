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

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

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
