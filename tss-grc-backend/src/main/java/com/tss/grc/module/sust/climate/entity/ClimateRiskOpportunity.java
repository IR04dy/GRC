package com.tss.grc.module.sust.climate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Climate Risk &amp; Opportunity register — first vertical slice of the
 * Sustainability &amp; Environmental Governance module (ESG-SUST).
 *
 * <p>A single row represents either a climate-related risk or an opportunity
 * (driven by {@code kind} = {@code RISK} | {@code OPPORTUNITY}). Categories
 * follow the TCFD + ISSB taxonomy:
 * <ul>
 *   <li>RISK: PHYSICAL / TRANSITION / REGULATORY / MARKET / TECHNOLOGY /
 *       REPUTATION / SUPPLY_CHAIN</li>
 *   <li>OPPORTUNITY: RESOURCE_EFFICIENCY / ENERGY_SOURCE / PRODUCT_SERVICE /
 *       MARKETS / RESILIENCE</li>
 * </ul>
 *
 * <p>Status machine: {@code IDENTIFIED → ASSESSED → TREATED → CLOSED | RETIRED}.
 * The service layer auto-suggests an ERM link when {@code riskScore >= 15}.
 * See V55 migration for full column commentary.
 */
@Entity
@Table(schema = "sust", name = "climate_risk_opportunity")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ClimateRiskOpportunity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;
    @Column(name = "item_code",   length = 40)          private String itemCode;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;

    @Column(nullable = false, length = 20) private String kind = "RISK";
    @Column(nullable = false, length = 30) private String category;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    // F2-240 - no initialiser ON PURPOSE. This entity is bound by its own PUT, so an
    // omitted key deserialises to the initialiser and the update's `!= null` guard can
    // never be false - the line reads as a partial update and behaves as a full
    // replace. The default is owned by ClimateRiskCreateRequest#toEntity, asserted
    // by NeverFiringNullGuardContractTest in both directions.
    @Column(name = "time_horizon", nullable = false, length = 10) private String timeHorizon;

    // F2-240 - no initialiser ON PURPOSE. This entity is bound by its own PUT, so an
    // omitted key deserialises to the initialiser and the update's `!= null` guard can
    // never be false - the line reads as a partial update and behaves as a full
    // replace. The default is owned by ClimateRiskCreateRequest#toEntity, asserted
    // by NeverFiringNullGuardContractTest in both directions.
    @Column(nullable = false) private Short likelihood;
    // F2-240 - no initialiser ON PURPOSE. This entity is bound by its own PUT, so an
    // omitted key deserialises to the initialiser and the update's `!= null` guard can
    // never be false - the line reads as a partial update and behaves as a full
    // replace. The default is owned by ClimateRiskCreateRequest#toEntity, asserted
    // by NeverFiringNullGuardContractTest in both directions.
    @Column(nullable = false) private Short impact;
    @Column(name = "risk_score", nullable = false) private Short riskScore = 9;

    @Column(name = "financial_impact_sar", precision = 18, scale = 2)
    private BigDecimal financialImpactSar;

    @Column(name = "related_service",             length = 255) private String relatedService;
    @Column(name = "related_supplier",            length = 255) private String relatedSupplier;
    @Column(name = "related_asset",               length = 255) private String relatedAsset;
    @Column(name = "related_erm_risk_id")                       private Long relatedErmRiskId;
    @Column(name = "related_tcfd_recommendation", length = 40)  private String relatedTcfdRecommendation;

    @Column(name = "controls_summary", columnDefinition = "NVARCHAR(MAX)") private String controlsSummary;
    @Column(name = "treatment_plan",   columnDefinition = "NVARCHAR(MAX)") private String treatmentPlan;
    @Column(name = "metrics_summary",  columnDefinition = "NVARCHAR(MAX)") private String metricsSummary;

    @Column(name = "evidence_ref", length = 255) private String evidenceRef;
    /** S-C — real FK into evidence.evidence_items. */
    @Column(name = "evidence_item_id") private Long evidenceItemId;
    @Column(name = "owner_name",   length = 255) private String ownerName;
    @Column(name = "owner_email",  length = 255) private String ownerEmail;

    @Column(nullable = false, length = 20) private String status = "IDENTIFIED";

    @Column(name = "identified_at") private LocalDateTime identifiedAt;
    @Column(name = "assessed_at")   private LocalDateTime assessedAt;
    @Column(name = "treated_at")    private LocalDateTime treatedAt;
    @Column(name = "closed_at")     private LocalDateTime closedAt;
    @Column(name = "review_date")   private LocalDate reviewDate;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        // riskScore is derived from likelihood × impact whenever either is set
        // by the service; here we just guarantee a sane default.
        if (riskScore == null || riskScore == 0) {
            riskScore = (short) (Math.max(1, likelihood) * Math.max(1, impact));
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Keep riskScore in sync if the service mutated likelihood/impact
        // without setting it explicitly.
        riskScore = (short) (Math.max(1, likelihood) * Math.max(1, impact));
    }
}
