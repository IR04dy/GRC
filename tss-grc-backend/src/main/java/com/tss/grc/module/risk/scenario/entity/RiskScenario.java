package com.tss.grc.module.risk.scenario.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Risk scenario analysis row — Saudi GRC prompt §7.3.
 *
 * <p>Persists a single scenario run (Monte-Carlo, stress test, or what-if)
 * against a parent risk. JSON columns carry the methodology-specific
 * input and result so the entity stays generic across the three types.
 *
 * <p>State machine: {@code DRAFT → APPROVED | REJECTED | ARCHIVED}.
 */
@Entity
@Table(schema = "risk", name = "scenarios")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskScenario {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    // ─── Parent risk linkage (nullable for "what-if before a risk is registered") ─
    @Column(name = "risk_id")
    private Long riskId;

    @Column(name = "risk_code", length = 64)
    private String riskCode;

    @Column(name = "risk_title_en", length = 255)
    private String riskTitleEn;

    // ─── Identification ───────────────────────────────────────────────
    @Column(name = "scenario_code", nullable = false, length = 64)
    private String scenarioCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** MONTE_CARLO | STRESS_TEST | WHAT_IF. */
    @Column(name = "scenario_type", nullable = false, length = 20)
    private String scenarioType = "MONTE_CARLO";

    @Column(name = "horizon_months", nullable = false)
    private Integer horizonMonths = 12;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "SAR";

    // ─── Methodology-specific payloads ────────────────────────────────
    @Column(name = "input_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String inputJson;

    @Column(name = "result_json", columnDefinition = "NVARCHAR(MAX)")
    private String resultJson;

    // Computed columns for fast dashboard reads (denormalised from result_json).
    @Column(name = "expected_loss", precision = 18, scale = 2)
    private BigDecimal expectedLoss;

    @Column(name = "p95_loss", precision = 18, scale = 2)
    private BigDecimal p95Loss;

    @Column(name = "p99_loss", precision = 18, scale = 2)
    private BigDecimal p99Loss;

    // ─── Narrative ───────────────────────────────────────────────────
    @Column(name = "assumptions_en", columnDefinition = "NVARCHAR(MAX)")
    private String assumptionsEn;

    @Column(name = "assumptions_ar", columnDefinition = "NVARCHAR(MAX)")
    private String assumptionsAr;

    @Column(name = "methodology_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String methodologyNotesEn;

    @Column(name = "methodology_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String methodologyNotesAr;

    // ─── Lifecycle ───────────────────────────────────────────────────
    /** DRAFT, APPROVED, REJECTED, ARCHIVED. */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approver_name", length = 255)
    private String approverName;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archived_by")
    private Long archivedBy;

    @Column(name = "archive_reason", length = 500)
    private String archiveReason;

    // ─── BaseEntity audit fields (mirrors RCA / Signature) ───────────
    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
