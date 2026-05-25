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
 * Point-in-time credit rating record on a counterparty.
 *
 * <p>Two flavours per the {@code ratingType} field:
 * <ul>
 *   <li>{@code INTERNAL} — the bank's own grade. Carries PD / LGD / EAD
 *       per the IRB framework. Grade scale is bank-specific but
 *       typically 8-15 buckets (R1..R10 / AAA..D).</li>
 *   <li>{@code EXTERNAL} — agency rating. Agency = S&amp;P / Moody's /
 *       Fitch / CARE Saudi / TASNEEF. Scale per agency.</li>
 * </ul>
 *
 * <p>A counterparty typically has multiple ratings over time forming
 * the rating history. The Counterparty entity caches the latest of
 * each type for fast filtering on dashboards.
 */
@Entity
@Table(schema = "cpty", name = "credit_rating")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CreditRating extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    /** {@code INTERNAL | EXTERNAL}. */
    @Column(name = "rating_type", nullable = false, length = 16)
    private String ratingType;

    /** Effective date — when the rating was assigned. */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** Rating grade (e.g. {@code R3}, {@code A+}, {@code Baa2}). */
    @Column(name = "grade", nullable = false, length = 16)
    private String grade;

    /** Rating scale (e.g. {@code R1-R10}, {@code S&P long-term}, {@code Moody's}). */
    @Column(name = "scale", length = 64)
    private String scale;

    /** External agency (only when ratingType = EXTERNAL). */
    @Column(name = "agency", length = 32)
    private String agency;

    /** Probability of Default — 12-month, INTERNAL only. */
    @Column(name = "pd", precision = 10, scale = 6)
    private BigDecimal pd;

    /** Loss Given Default — INTERNAL only. */
    @Column(name = "lgd", precision = 10, scale = 6)
    private BigDecimal lgd;

    /** Exposure at Default (SAR M) — INTERNAL only. */
    @Column(name = "ead_sar_m", precision = 18, scale = 2)
    private BigDecimal eadSarM;

    /** Outlook: {@code STABLE | POSITIVE | NEGATIVE | DEVELOPING}. */
    @Column(name = "outlook", length = 16)
    private String outlook = "STABLE";

    /** Free-text justification. */
    @Column(name = "rationale_en", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleEn;

    @Column(name = "rationale_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleAr;

    /** Officer who assigned the rating. */
    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    /** Approver (typically Credit Committee member). */
    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;
}
