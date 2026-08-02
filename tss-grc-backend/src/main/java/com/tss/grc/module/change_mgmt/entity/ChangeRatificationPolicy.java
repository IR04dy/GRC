package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * A17-D3 — the per-tenant emergency-ratification SLA (V345).
 *
 * <p>One row per organisation, seeded at ADR-017 D-3's default of five business
 * days. {@code slaBusinessDays} is bounded 1..10 by
 * {@code ck_ratification_sla_bounds} in the database as well as by the DTO, so
 * the answer to "what stops a tenant choosing 365 days?" holds for a direct
 * UPDATE, a support script, and an admin screen nobody has written yet.
 *
 * <p>{@code escalationRole} is the role notified on breach. It is stored rather
 * than hard-coded so a tenant whose security accountability sits with a
 * different title can redirect it — but the seeded value is {@code CISO}, which
 * was verified to be a real, global row in {@code iam.roles} in both databases
 * before it was written. F2-52 measured that 40 of 74 role names recited across
 * the platform's guards do not exist; an escalation aimed at a ghost role is an
 * escalation into a void, and this is the whole consequence half of D-3.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_ratification_policy")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeRatificationPolicy {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "sla_business_days", nullable = false)
    private Integer slaBusinessDays = 5;

    @Column(name = "escalation_role", nullable = false, length = 60)
    private String escalationRole = "CISO";

    @Column(name = "notes_en", length = 1000)
    private String notesEn;

    @Column(name = "notes_ar", length = 1000)
    private String notesAr;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
