package com.tss.grc.module.integrations.crowdstrike.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Audit row for a single Falcon detection observed by the poller. Backs
 * the V170 {@code platform.crowdstrike_detection_log} table.
 *
 * <p>One row per (organisation, falcon_detection_id) — the unique filtered
 * index on the table enforces dedupe. A re-poll of the same detection is a
 * no-op (the application-side dedupe in {@code CrowdStrikeInboundService}
 * catches it first; the constraint is the safety net).
 *
 * <p>Extends {@link BaseEntity} for audit columns + ROWVERSION + soft-delete.
 */
@Entity
@Table(name = "crowdstrike_detection_log", schema = "platform")
@SQLRestriction("is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrowdStrikeDetectionLog extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "falcon_detection_id", nullable = false, length = 160)
    private String falconDetectionId;

    @Column(name = "falcon_host_id", length = 64)
    private String falconHostId;

    /** Mapped NCA ECC-2 bucket (CRITICAL/HIGH/MEDIUM/LOW). */
    @Column(name = "severity", length = 20)
    private String severity;

    /** Raw 1..100 Falcon severity (kept verbatim for forensics). */
    @Column(name = "severity_raw")
    private Integer severityRaw;

    @Column(name = "tactic", length = 120)
    private String tactic;

    @Column(name = "technique", length = 120)
    private String technique;

    @Column(name = "mitre_attack_id", length = 40)
    private String mitreAttackId;

    @Column(name = "ingested_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime ingestedAt;

    /** FK to {@code socops.security_event.id}. Null when the detection was
     *  dropped by the application-side dedupe before the SE row was written. */
    @Column(name = "security_event_id")
    private Long securityEventId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
