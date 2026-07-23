package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * CH-17: per-tenant change authorisation matrix. One row per
 * (change_type x risk_level) cell defines the authorisation required to move a
 * change to CAB_APPROVED: the routing gate (AUTO/CAB/ECAB), the minimum recorded
 * APPROVE votes, and an advisory CSV of roles that should cover the approval.
 *
 * <p>Drives {@code ChangeManagementService}'s CAB_APPROVED quorum gate,
 * replacing the flat {@code grc.change.cab-min-approvals} global. Seeded by
 * V306 for every organisation.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_authorisation_matrix")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeAuthorisationMatrix {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;   // STANDARD | NORMAL | EMERGENCY

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;    // LOW | MEDIUM | HIGH | CRITICAL

    @Column(name = "gate", nullable = false, length = 10)
    private String gate;         // AUTO | CAB | ECAB

    @Column(name = "min_approvals", nullable = false)
    private Integer minApprovals = 1;

    /** CSV of roles that should cover the approval (advisory, e.g. "CHANGE_MANAGER,CISO"). */
    @Column(name = "required_roles", length = 400)
    private String requiredRoles;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;

    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String notesAr;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
