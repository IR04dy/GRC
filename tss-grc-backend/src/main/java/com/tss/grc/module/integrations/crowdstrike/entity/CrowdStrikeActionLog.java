package com.tss.grc.module.integrations.crowdstrike.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Audit row for a single Falcon outbound action — CONTAIN_HOST,
 * LIFT_CONTAINMENT, or HIDE_HOST. Backs the V170
 * {@code platform.crowdstrike_action_log} table.
 *
 * <p>One row per attempt — a retry burst produces multiple rows; the
 * {@link #attemptCount} field makes "final attempt" obvious. The action
 * type plus the analyst's reason text are the load-bearing audit fields
 * for NCA ECC-2 / SAMA CSF traceability.
 *
 * <p>{@code requestedByUserId} is the SecurityContext user that triggered
 * the action via REST. Null for scheduled flows (CrowdStrike has none in
 * the current slice — every action is human-initiated).
 *
 * <p>Extends {@link BaseEntity} for audit columns + ROWVERSION + soft-delete.
 */
@Entity
@Table(name = "crowdstrike_action_log", schema = "platform")
@SQLRestriction("is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrowdStrikeActionLog extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "target_falcon_host_id", nullable = false, length = 64)
    private String targetFalconHostId;

    /** Soft FK to the SOC event that justified the action (when known). */
    @Column(name = "security_event_id")
    private Long securityEventId;

    /** SecurityContext user that requested the action via REST. */
    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "reason_text", length = 500)
    private String reasonText;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_payload", columnDefinition = "NVARCHAR(MAX)")
    private String responsePayload;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
