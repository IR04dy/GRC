package com.tss.grc.module.iam.governance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * RBAC Access-Governance P3 (plan §6.6) — one break-glass activation: emergency role
 * granted to the activator, tied to a mandatory incident ref, max 24h validity,
 * auto-expiring, with a mandatory post-hoc review
 * ({@code PENDING_REVIEW → REVIEWED}, reviewer ≠ activator).
 */
@Entity
@Table(schema = "iam", name = "break_glass_events")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BreakGlassEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "activated_by", nullable = false) private Long activatedBy;
    @Column(name = "activated_by_username", nullable = false, length = 100) private String activatedByUsername;
    @Column(name = "role_id", nullable = false) private Long roleId;
    @Column(name = "incident_ref", nullable = false, length = 100) private String incidentRef;
    @Column(length = 1000) private String reason;
    @Column(name = "activated_at", nullable = false) private LocalDateTime activatedAt = LocalDateTime.now();
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    /** ACTIVE | EXPIRED. */
    @Column(nullable = false, length = 15) private String status = "ACTIVE";
    /** PENDING_REVIEW | REVIEWED — post-hoc review is mandatory. */
    @Column(name = "review_status", nullable = false, length = 20) private String reviewStatus = "PENDING_REVIEW";
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "review_note", length = 1000) private String reviewNote;
    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
