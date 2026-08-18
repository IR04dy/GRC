package com.tss.grc.module.bcm.cycle.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BIA Assessment — independent audit Tier-1 fix.
 *
 * <p>Rich Business Impact Analysis row per ISO 22301 §8.2.2 and spec §E.
 * Captures:
 * <ul>
 *   <li>Scope (service / process / cycle / department / owners)</li>
 *   <li>Recovery targets (MTPD, RTO, RPO) + MBCO narrative</li>
 *   <li>Minimum resources (staff / tech / facility / supplier)</li>
 *   <li>Manual workaround flag + description + peak periods</li>
 *   <li>Two-dimensional impact matrix (time bands × impact dimensions) as JSON</li>
 *   <li>Derived overall criticality (auto-calculated; can be re-derived on demand)</li>
 *   <li>Approval workflow + soft links to generated BCP / DRP rows</li>
 * </ul>
 *
 * <p>Distinct from the legacy {@code BiaRecord} under {@code module/bcm/entity/}
 * which only carried RTO/RPO/MTPD/overallImpact. Future sprint will migrate
 * legacy rows into this table.
 */
@Entity
@Table(schema = "bcm", name = "bia_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmBiaAssessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** Tenant-unique short code (BIA-YYYY-NNNNNN). */
    @Column(name = "bia_code", nullable = false, length = 50)
    private String biaCode;

    @Column(name = "cycle_id")        private Long cycleId;
    @Column(name = "service_id")      private Long serviceId;
    @Column(name = "process_id")      private Long processId;
    @Column(length = 255)             private String department;
    @Column(name = "bia_owner_id")    private Long biaOwnerId;
    @Column(name = "service_owner_id") private Long serviceOwnerId;
    @Column(name = "process_owner_id") private Long processOwnerId;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String description;

    // ── Recovery targets ──
    @Column(name = "mtpd_hours")            private Integer mtpdHours;
    @Column(name = "rto_hours")             private Integer rtoHours;
    @Column(name = "rpo_hours")             private Integer rpoHours;
    @Column(name = "mbco_description", columnDefinition = "NVARCHAR(MAX)")
    private String mbcoDescription;

    // ── Minimum resources ──
    @Column(name = "min_staff")              private Integer minStaff;
    @Column(name = "min_tech_text",     length = 1000) private String minTechText;
    @Column(name = "min_facility_text", length = 1000) private String minFacilityText;
    @Column(name = "min_supplier_text", length = 1000) private String minSupplierText;

    // ── Workaround ──
    @Column(name = "manual_workaround_available", nullable = false)
    private Boolean manualWorkaroundAvailable;

    @Column(name = "manual_workaround_description", columnDefinition = "NVARCHAR(MAX)")
    private String manualWorkaroundDescription;

    @Column(name = "peak_periods_text", length = 1000)
    private String peakPeriodsText;

    // ── Impact matrix ──
    /**
     * JSON object: {"0-2h":{"regulatory":1,"financial":2,…},"2-4h":{…},…}
     * Keys = time-band labels; values = map of impact-dimension → 1..5 score.
     */
    @Column(name = "impact_time_bands_json", columnDefinition = "NVARCHAR(MAX)")
    private String impactTimeBandsJson;

    /**
     * Per-dimension MAX across time bands. Denormalised at {@code calculateCriticality}
     * time so dashboards / reports don't re-parse the matrix.
     */
    @Column(name = "impact_dimensions_json", columnDefinition = "NVARCHAR(MAX)")
    private String impactDimensionsJson;

    /** LOW | MEDIUM | HIGH | CRITICAL | SOVEREIGN — auto-derived by service. */
    @Column(name = "overall_criticality", length = 20)
    private String overallCriticality;

    // Tier-4 #24 (SAMA OPRES) ───────────────────────────────────────
    /**
     * SAMA Operational Resilience Framework — Article 9. The absolute upper
     * bound a critical operation can tolerate disruption before unacceptable
     * harm to the firm, customers, or financial stability. Stored separately
     * from MTPD because OPRES uses tolerance as the contractual ceiling that
     * MTPD must never exceed.
     */
    @Column(name = "impact_tolerance_hours")
    private Integer impactToleranceHours;

    /** Flagged as a "critical operation" under SAMA OPRES (Article 4 mapping). */
    @Column(name = "opres_critical_operation_flag", nullable = false)
    private Boolean opresCriticalOperationFlag;

    // Tier-4 #27 (Sovereign / national-critical tier) ──────────────
    /** SOVEREIGN | NATIONAL | SECTORAL | NORMAL — Vision-2030 service classification. */
    @Column(name = "national_critical_tier", nullable = false, length = 20)
    private String nationalCriticalTier;

    @Column(name = "evidence_id")  private Long evidenceId;
    @Column(name = "reviewer_id")  private Long reviewerId;

    /** DRAFT | UNDER_REVIEW | CHANGES_REQUIRED | APPROVED | ACTIVE | ARCHIVED */
    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus;

    @Column(name = "approved_by", length = 255)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvedBy;

    @Column(name = "approved_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime approvedAt;

    /** Soft FK back to the BCM plan generated from this BIA, if any. */
    @Column(name = "generated_bcp_id") private Long generatedBcpId;
    /** Soft FK back to the DRP generated from this BIA, if any. */
    @Column(name = "generated_drp_id") private Long generatedDrpId;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;
    @Column(name = "created_by")                    private Long createdBy;
    @Column(name = "updated_by")                    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
