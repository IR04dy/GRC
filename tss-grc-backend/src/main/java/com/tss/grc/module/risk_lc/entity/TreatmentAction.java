package com.tss.grc.module.risk_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "risk_lc", name = "treatment_actions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TreatmentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private TreatmentPlan plan;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    @Column(name = "owner_role", nullable = false)
    private String ownerRole;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** PENDING/IN_PROGRESS/COMPLETED/BLOCKED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "expected_risk_reduction", nullable = false, precision = 4, scale = 3)
    private BigDecimal expectedRiskReduction = BigDecimal.ZERO;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "evidence_ref", length = 64)
    private String evidenceRef;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;
}
