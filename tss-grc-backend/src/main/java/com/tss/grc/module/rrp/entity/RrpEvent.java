package com.tss.grc.module.rrp.entity;

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
 * Timeline row on an {@link RrpCycle}.
 *
 * <p>Captures every state transition, indicator-band crossing,
 * recovery-option update, scenario-rehearsal record, and Board
 * milestone so the cycle's audit trail tells the full story for the
 * next SAMA D-SIB review.
 *
 * <p>Event types:
 * <pre>
 *   STATUS_CHANGED         — cycle moved through the state machine
 *   OPTION_ADDED           — new recovery option on the menu
 *   OPTION_REASSESSED      — capacity / time-to-execute changed
 *   CRITICAL_FUNCTION_ADDED — new critical function registered
 *   INDICATOR_BREACH        — RAG band crossed (live alert)
 *   INDICATOR_RECOVERED     — RAG band returned to GREEN
 *   SCENARIO_REHEARSED      — table-top exercise recorded
 *   BOARD_MILESTONE         — Board / Risk Committee event
 *   SUBMITTED               — lodged with SAMA
 *   ACK_RECEIVED            — SAMA acknowledgment
 *   INTERNAL_NOTE           — analyst note
 * </pre>
 */
@Entity
@Table(schema = "rrp", name = "rrp_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RrpEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 256)
    private String actorName;

    @Column(name = "status_after", length = 32)
    private String statusAfter;

    @Column(name = "linked_option_id")
    private Long linkedOptionId;

    @Column(name = "linked_indicator_id")
    private Long linkedIndicatorId;

    @Column(name = "linked_critical_function_id")
    private Long linkedCriticalFunctionId;

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
