package com.tss.grc.module.iam.governance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RBAC Access-Governance P1 (plan §6.2) — one maker-checker access request.
 * Requester = {@code createdBy} (Spring auditing). A grant becomes effective only on
 * approval, which provisions the {@code iam.user_roles} row with the validity window:
 * {@code PENDING → APPROVED → PROVISIONED | REJECTED | REVOKED}. Runtime SoD in the
 * service: approver ≠ requester and approver ≠ beneficiary.
 */
@Entity
@Table(schema = "iam", name = "access_requests")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AccessRequest extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "beneficiary_user_id", nullable = false)
    private Long beneficiaryUserId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    /** NULL = open-ended assignment. */
    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(nullable = false, length = 1000)
    private String justification;

    /** PENDING | APPROVED | PROVISIONED | REJECTED | REVOKED. */
    @Column(nullable = false, length = 15)
    private String status = "PENDING";

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "provisioned_at")
    private LocalDateTime provisionedAt;

    /** V294 — the anchored workflow-engine request raised for inbox/SLA visibility. */
    @Column(name = "engine_request_id")
    private Long engineRequestId;
}
