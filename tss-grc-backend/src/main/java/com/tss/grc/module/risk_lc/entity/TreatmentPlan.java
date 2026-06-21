package com.tss.grc.module.risk_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Treatment plan against a risk. Mirrors {@code lib/riskLifecycle.ts}'s
 * {@code TreatmentPlan} interface so the REST round-trip is 1:1 with the UI.
 */
@Entity
@Table(schema = "risk_lc", name = "treatment_plans")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TreatmentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** External id (TP-2026-001) — what the UI uses. */
    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** FK to risk.risks.id (cross-module reference). */
    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    /** MITIGATE/TRANSFER/AVOID/ACCEPT */
    @Column(nullable = false, length = 20)
    private String strategy;

    @Column(name = "rationale_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String rationaleEn;

    @Column(name = "rationale_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String rationaleAr;

    /** State machine: DRAFT → SUBMITTED → APPROVED → IN_PROGRESS → COMPLETED → REJECTED. */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "cost_sar_k", nullable = false, precision = 12, scale = 2)
    private BigDecimal costSarK = BigDecimal.ZERO;

    @Column(name = "cost_benefit_en", columnDefinition = "NVARCHAR(MAX)")
    private String costBenefitEn;

    @Column(name = "cost_benefit_ar", columnDefinition = "NVARCHAR(MAX)")
    private String costBenefitAr;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectedReason;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;

    // PERF-NQ-007: LAZY (was EAGER). EAGER triggered a separate actions SELECT for
    // every plan on list views (N+1). The repository's read finders now LEFT JOIN
    // FETCH this collection in a single query (see TreatmentPlanRepository), and
    // the @Transactional write paths initialise it within their open session — so
    // TreatmentPlanDto.from() never hits a LazyInitializationException despite
    // spring.jpa.open-in-view=false.
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Where(clause = "is_deleted = 0")
    private List<TreatmentAction> actions = new ArrayList<>();
}
