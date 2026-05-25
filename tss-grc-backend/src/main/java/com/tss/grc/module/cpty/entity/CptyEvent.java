package com.tss.grc.module.cpty.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Timeline row on a {@link Counterparty}.
 *
 * <p>Captures every rating action, limit change, RM handoff, KYC refresh,
 * and material event so the counterparty's audit trail tells the full
 * credit-life story for the next SAMA on-site review.
 */
@Entity
@Table(schema = "cpty", name = "cpty_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CptyEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * Event type:
     * {@code REGISTERED | RATING_ASSIGNED | RATING_ACTION | LIMIT_APPROVED |
     *        LIMIT_REVISED | LIMIT_BAND_CHANGED | KYC_REFRESHED |
     *        RM_HANDOFF | STATUS_CHANGED | INTERNAL_NOTE}.
     */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 256)
    private String actorName;

    @Column(name = "linked_rating_id")
    private Long linkedRatingId;

    @Column(name = "linked_action_id")
    private Long linkedActionId;

    @Column(name = "linked_limit_id")
    private Long linkedLimitId;

    /** For band-crossing events: previous band. */
    @Column(name = "band_before", length = 8)
    private String bandBefore;

    /** For band-crossing events: new band. */
    @Column(name = "band_after", length = 8)
    private String bandAfter;

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;
}
