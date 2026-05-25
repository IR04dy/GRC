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

/**
 * One counterparty in the bank's credit-risk register.
 *
 * <p>Sectors follow the Saudi standard taxonomy (SAMA Banking Sector
 * Reports): SOVEREIGN, FINANCIAL_INSTITUTION, CORPORATE_LARGE,
 * CORPORATE_SME, RETAIL, GOVERNMENT_RELATED_ENTITY (e.g. PIF /
 * Aramco / SABIC), MULTILATERAL.
 *
 * <p>Each counterparty has a parent-group reference so the platform can
 * roll up exposures across legal entities (SAMA's group-level limit
 * tracking). E.g. ARAMCO_TRADING + SAUDI_ARAMCO + LUBEREF all roll up
 * to ARAMCO_GROUP for the group exposure calculation.
 */
@Entity
@Table(schema = "cpty", name = "counterparty")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Counterparty extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, unique per tenant. e.g. {@code CP-00001}. */
    @Column(name = "reference", nullable = false, length = 32)
    private String reference;

    /** Legal Entity Identifier (ISO 17442), if applicable. */
    @Column(name = "lei", length = 20)
    private String lei;

    @Column(name = "legal_name_en", nullable = false, length = 256)
    private String legalNameEn;

    @Column(name = "legal_name_ar", nullable = false, length = 256)
    private String legalNameAr;

    /** Short trading name. */
    @Column(name = "short_name", length = 128)
    private String shortName;

    /**
     * Sector:
     * {@code SOVEREIGN | FINANCIAL_INSTITUTION | CORPORATE_LARGE |
     *        CORPORATE_SME | RETAIL | GOVERNMENT_RELATED_ENTITY |
     *        MULTILATERAL}.
     */
    @Column(name = "sector", nullable = false, length = 32)
    private String sector;

    /** ISO 3166-1 alpha-2 country code (SA, AE, US…). */
    @Column(name = "country", length = 2)
    private String country;

    /** Industry per ISIC Rev. 4 or NACE rev. 2 (free-text). */
    @Column(name = "industry", length = 128)
    private String industry;

    /** Parent group reference (rolls up exposures across legal entities). */
    @Column(name = "parent_group_reference", length = 64)
    private String parentGroupReference;

    /** {@code true} if part of Government-Related Entity (PIF / Aramco / SABIC / GACI). */
    @Column(name = "gre_flag", nullable = false)
    private Boolean greFlag = false;

    /** {@code true} if sovereign or sovereign-equivalent (SAMA, GOSI, PIF). */
    @Column(name = "sovereign_flag", nullable = false)
    private Boolean sovereignFlag = false;

    /** {@code true} if regulated financial institution. */
    @Column(name = "fi_flag", nullable = false)
    private Boolean fiFlag = false;

    /**
     * Counterparty status:
     * {@code ACTIVE | WATCHLIST | DEFAULT | RESOLVED | DORMANT}.
     * Derived from latest RatingAction + manual overrides.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** Relationship manager (corporate banking owner). */
    @Column(name = "relationship_manager_user_id")
    private Long relationshipManagerUserId;

    /** Credit officer (1LoD owner of the rating). */
    @Column(name = "credit_officer_user_id")
    private Long creditOfficerUserId;

    /** Last KYC refresh date (used for KYC-due alerts at year + 1). */
    @Column(name = "kyc_refreshed_at")
    private java.time.LocalDate kycRefreshedAt;

    /** Onboarding date — earliest credit relationship. */
    @Column(name = "onboarded_at")
    private java.time.LocalDate onboardedAt;

    /** Total committed exposure across all products (SAR M). Denormalised. */
    @Column(name = "total_exposure_sar_m", precision = 18, scale = 2)
    private BigDecimal totalExposureSarM = BigDecimal.ZERO;

    /** Latest internal rating grade (cached from CreditRating for fast filtering). */
    @Column(name = "current_internal_grade", length = 8)
    private String currentInternalGrade;

    /** Latest external rating (e.g. "A+" / "Baa2" / "BB-"). */
    @Column(name = "current_external_rating", length = 16)
    private String currentExternalRating;

    /** Latest external agency. */
    @Column(name = "current_external_agency", length = 32)
    private String currentExternalAgency;
}
