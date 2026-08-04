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

    /**
     * CSV of <b>platform role names</b> that must cover the approval, e.g.
     * {@code "CHANGE_MANAGER,CISO"}.
     *
     * <p>⚠ This javadoc said <b>"advisory"</b> for as long as the column existed, and that word
     * was the finding (F2-95): the field was configured on 33 of 36 rows and applied nowhere,
     * while the API echoed it back beside a {@code satisfied} flag computed from vote counts
     * alone. "Advisory" is what gets written when a check turns out to be impossible mid
     * implementation — here because three vocabularies name roles on this path (these platform
     * roles, governance seat titles on committee rosters, and the free text a voter types into
     * {@code change_approvals.approver_role}).
     *
     * <p>Now resolved through {@code approver_id → iam.user_roles} by
     * {@code ChangeManagementService#requiredRoleStates}, which reports each role as SATISFIED,
     * MISSING, NO_ACTIVE_HOLDER or NOT_A_ROLE. It is <b>reported, not yet gated</b> — see that
     * method for the measurement showing a literal gate would refuse 9 of 11 already-decided
     * changes.
     */
    @Column(name = "required_roles", length = 400)
    private String requiredRoles;

    /**
     * S8e / CE-2 — when true, this cell authorises a change <b>only</b> on the strength of a live
     * pre-approved change model. Per tenant AND per cell, because "STANDARD/LOW needs no committee
     * provided it follows a pre-approved procedure" is a statement about the cell.
     *
     * <p><b>Defaults to false, and V354 asserts it is false on every existing row.</b> 5 STANDARD
     * changes exist in each database and 3 are in flight, so a default-on flag would refuse work
     * already under way — the mistake S6c avoided for {@code quorum_requires_chair}, where
     * default-on would have refused 17 of 18 quorate meetings. Enabling it is a tenant decision
     * taken after the catalogue has models in it.
     *
     * <p>⚠ Unlike {@link #requiredRoles}, this is <b>gated, not reported</b>: when it is on,
     * {@code ChangeManagementService} refuses {@code → CAB_APPROVED} with a 409 naming the missing
     * model. A second column that describes a control without applying it is F2-95's shape, and one
     * of those on this table is already one too many.
     */
    @Column(name = "requires_model", nullable = false)
    private Boolean requiresModel = false;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;

    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String notesAr;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
