package com.tss.grc.module.sust.finance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Green-Finance uplift G1 (plan §4) — one use-of-proceeds ledger entry:
 * instrument → project, amount, NEW_FINANCING vs REFINANCING, and the
 * maker-checker state {@code PROPOSED → APPROVED | REJECTED}. The proposer and
 * approver are recorded and MUST differ (runtime SoD — enforced in the service;
 * plan §4.3). Only APPROVED rows count toward allocation and impact figures.
 */
@Entity
@Table(schema = "sust", name = "instrument_allocations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class InstrumentAllocation extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** Soft FK → sust.green_instruments.id. */
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    /** Soft FK → sust.green_projects.id. */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "amount_sar_mn", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountSarMn;

    @Column(name = "allocation_date", nullable = false)
    private LocalDate allocationDate;

    /** NEW_FINANCING | REFINANCING. */
    @Column(name = "financing_type", nullable = false, length = 15)
    private String financingType = "NEW_FINANCING";

    /** PROPOSED | APPROVED | REJECTED. */
    @Column(nullable = false, length = 15)
    private String status = "PROPOSED";

    @Column(name = "proposed_by")
    private Long proposedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** GF-2 (V298) — primary linked evidence item (S-C bridge). */
    @Column(name = "evidence_item_id")
    private Long evidenceItemId;
}
