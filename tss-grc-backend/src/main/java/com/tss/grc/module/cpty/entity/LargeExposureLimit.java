package com.tss.grc.module.cpty.entity;

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

/**
 * Counterparty-level or group-level large-exposure limit.
 *
 * <p>SAMA's large-exposure framework caps single-counterparty exposure
 * at 25% of Tier-1 capital and group exposure at 25% of Tier-1. This
 * entity tracks the limit + current utilisation so the dashboard can
 * surface AMBER/RED counterparties and breach events.
 *
 * <p>Limit scope = {@code COUNTERPARTY} or {@code GROUP}. Group-scope
 * rows aggregate across all counterparties sharing the {@code parent
 * GroupReference}.
 */
@Entity
@Table(schema = "cpty", name = "large_exposure_limit")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class LargeExposureLimit extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Scope: {@code COUNTERPARTY | GROUP}. */
    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    /** Counterparty id (when scope = COUNTERPARTY). */
    @Column(name = "counterparty_id")
    private Long counterpartyId;

    /** Group reference (when scope = GROUP). */
    @Column(name = "group_reference", length = 64)
    private String groupReference;

    /** Display name. */
    @Column(name = "display_name_en", nullable = false, length = 256)
    private String displayNameEn;

    @Column(name = "display_name_ar", nullable = false, length = 256)
    private String displayNameAr;

    /** Approved limit (SAR M). */
    @Column(name = "limit_sar_m", nullable = false, precision = 18, scale = 2)
    private BigDecimal limitSarM;

    /** Current utilised exposure (SAR M). */
    @Column(name = "utilised_sar_m", nullable = false, precision = 18, scale = 2)
    private BigDecimal utilisedSarM = BigDecimal.ZERO;

    /** Computed utilisation percentage = utilised / limit × 100. */
    @Column(name = "utilisation_pct", precision = 8, scale = 4)
    private BigDecimal utilisationPct = BigDecimal.ZERO;

    /**
     * Band: {@code GREEN (≤70%) | AMBER (70-90%) | RED (>90%) | BREACH (>100%)}.
     * Derived on update.
     */
    @Column(name = "band", nullable = false, length = 8)
    private String band = "GREEN";

    /** Limit approval date. */
    @Column(name = "approved_at")
    private LocalDate approvedAt;

    /** Limit review date (annual or quarterly). */
    @Column(name = "next_review_at")
    private LocalDate nextReviewAt;

    /** Approver (Credit Committee member). */
    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    /** When utilised_sar_m was last refreshed. */
    @Column(name = "utilisation_refreshed_at")
    private LocalDate utilisationRefreshedAt;
}
