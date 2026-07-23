package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Track D (user decision #1): per-tenant map of a CH-17 authorisation gate
 * (CAB / ECAB) to the committee that acts as that body. Referral creation
 * resolves gate -> committee_id from here when no committee is passed explicitly.
 */
@Entity
@Table(schema = "admin", name = "committee_gate_map")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CommitteeGateMap {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "gate", nullable = false, length = 10)
    private String gate;   // CAB | ECAB

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
