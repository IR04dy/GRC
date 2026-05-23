package com.tss.grc.module.integrations.tenable.entity;

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
 * Audit row for a single Tenable outbound action — RECAST_SEVERITY,
 * ACCEPT_RISK, or REMOVE_RECAST. Backs the V176
 * {@code platform.tenable_recast_log} table.
 *
 * <p>One row per attempt — a retry burst produces multiple rows; the
 * {@link #attemptCount} field makes "final attempt" obvious. The action
 * type plus the analyst's justification are the load-bearing audit fields
 * for NCA ECC-2 / SAMA CSF traceability.
 *
 * <p>{@code requestedByUserId} is the SecurityContext user that triggered
 * the action via REST. Null is not expected — these endpoints reject
 * unauthenticated calls.
 *
 * <p>{@code targetAssetUuid} is nullable on purpose: a "global" recast or
 * accept-risk applies across every asset where the plugin matches.
 *
 * <p>Extends {@link BaseEntity} for audit columns + ROWVERSION + soft-delete.
 */
@Entity
@Table(name = "tenable_recast_log", schema = "platform")
@SQLRestriction("is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TenableRecastLog extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "action_type", nullable = false, length = 40)
    private String actionType;

    @Column(name = "target_plugin_id", nullable = false, length = 40)
    private String targetPluginId;

    /** NULL = global recast across all assets carrying this plugin id. */
    @Column(name = "target_asset_uuid", length = 80)
    private String targetAssetUuid;

    /** Only populated for RECAST_SEVERITY (null for ACCEPT_RISK + REMOVE_RECAST). */
    @Column(name = "new_severity", length = 20)
    private String newSeverity;

    @Column(name = "justification", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String justification;

    /** For ACCEPT_RISK — when the acceptance window closes (open-ended = null). */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** SecurityContext user that requested the action via REST. */
    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_payload", columnDefinition = "NVARCHAR(MAX)")
    private String responsePayload;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
