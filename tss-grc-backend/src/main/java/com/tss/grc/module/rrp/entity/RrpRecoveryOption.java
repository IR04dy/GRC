package com.tss.grc.module.rrp.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * One recovery option on the bank's menu.
 *
 * <p>The recovery option register lists pre-rehearsed management actions
 * the bank can take under stress to restore viability. Each option
 * carries:
 *
 * <ul>
 *   <li>category — CAPITAL / LIQUIDITY / ASSET_SALE / BUSINESS_LINE / EXPENSE
 *   <li>capacity — SAR amount the option can deliver
 *   <li>time-to-execute — calendar days from board approval to cash impact
 *   <li>impact — narrative on which indicators it moves and by how much
 *   <li>preconditions — what must already be true (e.g. signed share-issuance facility)
 *   <li>limitations — what could block it (e.g. market conditions, regulatory consent)
 * </ul>
 *
 * <p>SAMA inspects this menu at every D-SIB review — they want to see
 * realistic capacity numbers, not aspirational ones.
 */
@Entity
@Table(schema = "rrp", name = "rrp_recovery_option")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RrpRecoveryOption extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    /** Option code within the cycle. e.g. {@code OPT-001}. */
    @Column(name = "option_code", nullable = false, length = 32)
    private String optionCode;

    /**
     * Category:
     * {@code CAPITAL | LIQUIDITY | ASSET_SALE | BUSINESS_LINE | EXPENSE | OTHER}.
     */
    @Column(name = "category", nullable = false, length = 24)
    private String category;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** Capacity the option can deliver, in SAR millions. */
    @Column(name = "capacity_sar_m", nullable = false)
    private Long capacitySarM = 0L;

    /** Calendar days from Board approval to cash impact. */
    @Column(name = "time_to_execute_days", nullable = false)
    private Integer timeToExecuteDays = 30;

    /** Realism rating: {@code HIGH | MEDIUM | LOW}. SAMA likes HIGH options. */
    @Column(name = "feasibility", nullable = false, length = 12)
    private String feasibility = "MEDIUM";

    /** Narrative impact on indicators (e.g. "+1.5pp on CET1"). */
    @Column(name = "impact_en", columnDefinition = "NVARCHAR(MAX)")
    private String impactEn;

    @Column(name = "impact_ar", columnDefinition = "NVARCHAR(MAX)")
    private String impactAr;

    /** Preconditions — what must already be in place. */
    @Column(name = "preconditions_en", columnDefinition = "NVARCHAR(MAX)")
    private String preconditionsEn;

    @Column(name = "preconditions_ar", columnDefinition = "NVARCHAR(MAX)")
    private String preconditionsAr;

    /** Limitations — what could block the option. */
    @Column(name = "limitations_en", columnDefinition = "NVARCHAR(MAX)")
    private String limitationsEn;

    @Column(name = "limitations_ar", columnDefinition = "NVARCHAR(MAX)")
    private String limitationsAr;

    /** Accountable executive (CFO for capital, Treasurer for liquidity, etc.). */
    @Column(name = "accountable_role", length = 64)
    private String accountableRole;
}
