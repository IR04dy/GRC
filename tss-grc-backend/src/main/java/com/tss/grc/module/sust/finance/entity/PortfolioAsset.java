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
 * Green-Finance uplift G3 (plan §5.1/§5.2) — one portfolio exposure with its green
 * tag. Tagging is maker-checker'd: 1st line proposes ({@code PROPOSED} +
 * proposed_status), 2nd line decides (SoD: proposer ≠ approver). Only assets whose
 * effective status is {@code ELIGIBLE} count in the Green Asset Ratio numerator;
 * every asset with a decided status counts in the denominator.
 */
@Entity
@Table(schema = "sust", name = "portfolio_assets")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class PortfolioAsset extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "asset_code", nullable = false, length = 40)
    private String assetCode;

    @Column(name = "name_en", nullable = false, length = 300)
    private String nameEn;

    @Column(name = "name_ar", length = 300)
    private String nameAr;

    @Column(length = 80)
    private String sector;

    @Column(name = "exposure_sar_mn", nullable = false, precision = 18, scale = 4)
    private BigDecimal exposureSarMn;

    @Column(name = "taxonomy_code", length = 40)
    private String taxonomyCode;

    /** UNASSESSED | PROPOSED | ELIGIBLE | TRANSITION | INELIGIBLE. */
    @Column(name = "eligibility_status", nullable = false, length = 20)
    private String eligibilityStatus = "UNASSESSED";

    /** The status the proposer asked for (pending decision). */
    @Column(name = "proposed_status", length = 20)
    private String proposedStatus;

    @Column(name = "proposed_by")
    private Long proposedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}
