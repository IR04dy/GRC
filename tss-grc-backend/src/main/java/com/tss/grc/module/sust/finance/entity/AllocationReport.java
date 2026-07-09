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
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Green-Finance uplift G2 (plan §4.1/REG-7) — the annual post-issuance report snapshot
 * for one instrument: use-of-proceeds Allocation figures + attributed Impact figures,
 * frozen at generation time (per IFRS-style stability, published numbers must not drift
 * when underlying rows change later). Lifecycle {@code DRAFT → APPROVED → PUBLISHED}
 * with maker-checker (creator ≠ approver) enforced in the service.
 */
@Entity
@Table(schema = "sust", name = "allocation_reports")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AllocationReport extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** Soft FK → sust.green_instruments.id. */
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** ALLOCATION | IMPACT | COMBINED. */
    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType = "COMBINED";

    @Column(name = "allocated_sar_mn", precision = 18, scale = 4)
    private BigDecimal allocatedSarMn;

    @Column(name = "unallocated_sar_mn", precision = 18, scale = 4)
    private BigDecimal unallocatedSarMn;

    @Column(name = "allocation_ratio_pct", precision = 9, scale = 4)
    private BigDecimal allocationRatioPct;

    @Column(name = "refinancing_share_pct", precision = 9, scale = 4)
    private BigDecimal refinancingSharePct;

    @Column(name = "tco2e_avoided_yr", precision = 18, scale = 4)
    private BigDecimal tco2eAvoidedYr;

    @Column(name = "mwh_renewable_yr", precision = 18, scale = 4)
    private BigDecimal mwhRenewableYr;

    @Column(name = "water_m3_saved_yr", precision = 18, scale = 4)
    private BigDecimal waterM3SavedYr;

    @Column(name = "intensity_tco2e_per_sar_mn", precision = 18, scale = 6)
    private BigDecimal intensityTco2ePerSarMn;

    @Column(name = "narrative_en", columnDefinition = "NVARCHAR(MAX)")
    private String narrativeEn;

    @Column(name = "narrative_ar", columnDefinition = "NVARCHAR(MAX)")
    private String narrativeAr;

    /** DRAFT | APPROVED | PUBLISHED. */
    @Column(nullable = false, length = 15)
    private String status = "DRAFT";

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
