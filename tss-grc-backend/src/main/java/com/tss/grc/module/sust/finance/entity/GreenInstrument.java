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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Green-Finance uplift G1 (plan §4) — a green/sustainable finance instrument
 * (الصكوك الخضراء): green sukuk, green bond, sustainability sukuk, green loan or
 * sustainability-linked loan. Carries the Shariah dimension (structure + board
 * approval reference) required for sukuk in the Saudi market, the external-review
 * (SPO) metadata expected by ICMA GBP / green-sukuk practice, and the lifecycle
 * {@code DRAFT → SHARIAH_REVIEW → APPROVED → ISSUED → REPORTING → MATURED}.
 *
 * <p>Amounts are SAR millions to match the financed-emissions convention.
 */
@Entity
@Table(schema = "sust", name = "green_instruments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class GreenInstrument extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "instrument_code", nullable = false, length = 40)
    private String instrumentCode;

    /** GREEN_SUKUK | GREEN_BOND | SUSTAINABILITY_SUKUK | GREEN_LOAN | SLL. */
    @Column(name = "instrument_type", nullable = false, length = 30)
    private String instrumentType;

    @Column(name = "name_en", nullable = false, length = 300)
    private String nameEn;

    @Column(name = "name_ar", length = 300)
    private String nameAr;

    @Column(length = 20)
    private String isin;

    @Column(length = 60)
    private String listing;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "amount_sar_mn", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountSarMn;

    @Column(name = "issuance_costs_sar_mn", nullable = false, precision = 18, scale = 4)
    private BigDecimal issuanceCostsSarMn = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "SAR";

    @Column(name = "framework_reference", length = 300)
    private String frameworkReference;

    /** MUDARABA | WAKALA | IJARA | HYBRID | NA (non-sukuk instruments). */
    @Column(name = "shariah_structure", nullable = false, length = 20)
    private String shariahStructure = "NA";

    @Column(name = "shariah_approval_ref", length = 200)
    private String shariahApprovalRef;

    @Column(name = "shariah_approved", nullable = false)
    private Boolean shariahApproved = false;

    @Column(name = "external_reviewer", length = 200)
    private String externalReviewer;

    @Column(name = "spo_rating", length = 60)
    private String spoRating;

    /** CSV of eligible-category codes (ICMA/CCE). */
    @Column(name = "icma_categories", length = 500)
    private String icmaCategories;

    @Column(name = "profit_rate_pct", precision = 9, scale = 4)
    private BigDecimal profitRatePct;

    @Column(name = "lookback_months", nullable = false)
    private Integer lookbackMonths = 24;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    /** GF-5 (V298, REG-8) — offering-document reference next to isin/listing. */
    @Column(name = "prospectus_ref", length = 200)
    private String prospectusRef;

    /** GF-2 (V298) — primary linked evidence item (S-C bridge). */
    @Column(name = "evidence_item_id")
    private Long evidenceItemId;
}
