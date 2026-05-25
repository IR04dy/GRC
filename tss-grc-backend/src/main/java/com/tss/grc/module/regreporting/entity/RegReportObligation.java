package com.tss.grc.module.regreporting.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * One recurring mandatory regulatory return — the catalog row.
 *
 * <p>Defines a recurring statutory obligation (e.g. SAMA daily liquidity
 * return, CMA quarterly directors' dealings disclosure, NCA cyber-incident
 * monthly summary). Filings for each cycle are generated from this catalog.
 */
@Entity
@Table(schema = "regreporting", name = "regreport_obligation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegReportObligation extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Catalog code, unique per tenant. e.g. {@code SAMA-DLR}, {@code CMA-QTR-DEAL}. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** Regulator code — {@code SAMA | NCA | CMA | SDAIA | NCEMA | ZATCA | OTHER}. */
    @Column(name = "regulator", nullable = false, length = 16)
    private String regulator;

    /**
     * Frequency — drives auto-generation of filings:
     * {@code DAILY | WEEKLY | MONTHLY | QUARTERLY | SEMIANNUAL | ANNUAL | AD_HOC}.
     */
    @Column(name = "frequency", nullable = false, length = 16)
    private String frequency;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    /** Short description of what the return covers. */
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** Business days after period-end when filing is due (e.g. 15 for monthly capital). */
    @Column(name = "due_offset_business_days", nullable = false)
    private Integer dueOffsetBusinessDays = 5;

    /** Statutory basis — article / circular ref (e.g. "SAMA Circular 41031843"). */
    @Column(name = "statutory_basis", length = 256)
    private String statutoryBasis;

    /** Accountable role — typically {@code CFO | CRO | CISO | DPO | COMPLIANCE_OFFICER}. */
    @Column(name = "owner_role", length = 64)
    private String ownerRole;

    /** Reviewer role — second-line. */
    @Column(name = "reviewer_role", length = 64)
    private String reviewerRole;

    /** Filing channel — where it gets lodged (e.g. "SAMA RBS portal", "CMA Tadawul"). */
    @Column(name = "filing_channel", length = 256)
    private String filingChannel;

    /** Reminder ladder (T-N business days) as CSV. e.g. "7,3,1". */
    @Column(name = "reminder_ladder", length = 64)
    private String reminderLadder = "7,3,1";

    /** Active in current cycle generation. False = retired catalog row. */
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
