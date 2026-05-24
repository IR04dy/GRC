package com.tss.grc.module.privacy.subprocessor.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only event ledger row for a {@link Subprocessor}.
 *
 * <p>Every meaningful change to a sub-processor — onboarding,
 * suspension, resumption, cross-border-safeguard change, review,
 * incident — gets a row here. The DPO can read the timeline directly;
 * SDAIA inspectors can spot-check the narrative against the register
 * row.
 *
 * <p>Event types:
 * <ul>
 *   <li>{@code ADDED} — first record creation</li>
 *   <li>{@code MATERIAL_CHANGE} — scope/purpose/safeguard changed,
 *       forces re-review</li>
 *   <li>{@code SUSPENDED} / {@code RESUMED} / {@code RETIRED} — lifecycle</li>
 *   <li>{@code CROSS_BORDER_CHANGE} — safeguard type changed</li>
 *   <li>{@code REVIEW_COMPLETED} — 12-month review done</li>
 *   <li>{@code INCIDENT_REPORTED} — privacy incident involving this sub</li>
 * </ul>
 */
@Entity
@Table(schema = "privacy", name = "subprocessor_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SubprocessorEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "subprocessor_id", nullable = false)
    private Long subprocessorId;

    /** ADDED | MATERIAL_CHANGE | SUSPENDED | RESUMED | RETIRED | CROSS_BORDER_CHANGE | REVIEW_COMPLETED | INCIDENT_REPORTED. */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate = LocalDateTime.now();

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @Column(name = "description_en", length = 2000)
    private String descriptionEn;

    @Column(name = "description_ar", length = 2000)
    private String descriptionAr;

    /**
     * PDPL Article 30: when a sub-processor changes materially, the
     * controller must inform data subjects. This flag tracks the
     * notification obligation.
     */
    @Column(name = "data_subjects_notified", nullable = false)
    private Boolean dataSubjectsNotified = false;

    @Column(name = "data_subjects_notification_date")
    private LocalDate dataSubjectsNotificationDate;
}
