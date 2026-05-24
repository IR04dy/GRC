package com.tss.grc.module.ccm.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One execution of a {@link ControlTestDefinition} — Sprint 11
 * Continuous Control Monitoring Engine.
 *
 * <p>Run rows are intended to be immutable. The {@code is_deleted} flag
 * is retained on {@link com.tss.grc.common.model.BaseEntity} for storage
 * lifecycle (PDPL retention sweeps may eventually trim very old runs)
 * but no service path mutates a run after the initial insert. Tests +
 * dashboards always read the full history append-only.
 *
 * <p>{@link #evidenceHash} is SHA-256 of
 * {@code actualValueJson || ISO-8601(runAt)} — the tamper-evidence
 * primitive. An auditor reproducing the hash from the JSON + timestamp
 * confirms the row has not been rewritten post-hoc.
 *
 * <p>{@link #capaTaskId} is set by {@code CcmService} when three
 * consecutive CRITICAL-severity failures auto-open an Issue / CAPA.
 */
@Entity
@Table(schema = "ccm", name = "control_test_run")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ControlTestRun extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "test_definition_id", nullable = false)
    private Long testDefinitionId;

    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt = LocalDateTime.now();

    @Column(name = "duration_ms")
    private Integer durationMs;

    /** PASS | FAIL | ERROR | SKIPPED | TIMEOUT. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "actual_value_json", columnDefinition = "NVARCHAR(MAX)")
    private String actualValueJson;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /** SHA-256(actual_value_json || ISO-8601(run_at)) — tamper-evident. */
    @Column(name = "evidence_hash", length = 64)
    private String evidenceHash;

    /** Soft FK to capa.capa_tasks — set when a run triggers an auto-CAPA. */
    @Column(name = "capa_task_id")
    private Long capaTaskId;
}
