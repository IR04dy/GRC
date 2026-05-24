package com.tss.grc.module.risk.kri.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One AMBER/RED breach window on a {@link KriDefinition} — Sprint 13.
 *
 * <p>Lifecycle:
 * <pre>
 *   measurement turns AMBER → engine opens a new breach (level=AMBER)
 *   measurement stays AMBER → existing AMBER breach remains open
 *   measurement turns RED   → AMBER breach is closed and a new RED
 *                             breach is opened (the RED row is what
 *                             surfaces to the audit committee)
 *   measurement stays RED   → existing RED breach remains open
 *                             (sustained-red counter ticks against it)
 *   measurement turns GREEN → any open breach is closed
 * </pre>
 *
 * <p>The {@code measurement_id} FK records the measurement that
 * opened the breach. Closing measurements stamp {@link #closedAt}
 * directly on this row — no separate "closing_measurement_id"
 * column, since the audit log already carries the closing
 * measurement's id in its payload.
 *
 * <p>{@link #capaTaskId} is set when sustained RED triggers an
 * auto-CAPA Issue (see {@code KriBreachService.SUSTAINED_RED_THRESHOLD}).
 */
@Entity
@Table(schema = "risk", name = "kri_breach")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class KriBreach extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "kri_id", nullable = false)
    private Long kriId;

    @Column(name = "measurement_id", nullable = false)
    private Long measurementId;

    /** AMBER | RED. */
    @Column(name = "breach_level", nullable = false, length = 20)
    private String breachLevel;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "acknowledged_by_user_id")
    private Long acknowledgedByUserId;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** Soft FK to platform.issues — auto-opened when sustained-red threshold is crossed. */
    @Column(name = "capa_task_id")
    private Long capaTaskId;
}
