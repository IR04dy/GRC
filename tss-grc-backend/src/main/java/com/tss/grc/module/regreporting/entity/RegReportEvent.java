package com.tss.grc.module.regreporting.entity;

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
 * Timeline row on a {@link RegReportFiling}.
 *
 * <p>Captures every state transition, every reminder fired, and any
 * regulator interaction so the audit trail tells the full story of the
 * filing cycle.
 *
 * <p>Event types:
 * <pre>
 *   STATUS_CHANGED       — moved through the state machine
 *   REMINDER_FIRED       — T-N reminder sent
 *   SUBMITTED            — lodged with regulator
 *   ACK_RECEIVED         — regulator acknowledgment captured
 *   REJECTED             — regulator pushed back
 *   EXEMPTION_GRANTED    — formal waiver
 *   INTERNAL_NOTE        — analyst note
 * </pre>
 */
@Entity
@Table(schema = "regreporting", name = "regreport_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegReportEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "filing_id", nullable = false)
    private Long filingId;

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

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;
}
