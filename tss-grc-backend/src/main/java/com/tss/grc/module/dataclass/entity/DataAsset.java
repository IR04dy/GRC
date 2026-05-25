package com.tss.grc.module.dataclass.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A logical dataset with NDMO classification + PDPL tagging. */
@Entity
@Table(schema = "dataclass", name = "data_asset")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DataAsset extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "reference", nullable = false, length = 64) private String reference;

    @Column(name = "name_en", nullable = false, length = 256) private String nameEn;
    @Column(name = "name_ar", nullable = false, length = 256) private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    /** CUSTOMER_PII/FINANCIAL_TRANSACTION/EMPLOYEE/SHARIA/AML_KYC/MARKET_DATA/CONTRACT/CREDIT_FILE/OPERATIONAL/OTHER. */
    @Column(name = "domain", length = 32) private String domain;

    /** PUBLIC/INTERNAL/CONFIDENTIAL/TOP_SECRET — NDMO 4-level. */
    @Column(name = "classification", length = 16) private String classification;

    /** Special category under PDPL Art.4 — NONE/HEALTH/BIOMETRIC/GENETIC/RELIGIOUS/RACIAL/POLITICAL/CRIMINAL. */
    @Column(name = "pdpl_special_category", length = 16)
    private String pdplSpecialCategory = "NONE";

    /** Comma-separated PDPL Article 6 lawful basis tags. */
    @Column(name = "pdpl_lawful_basis", length = 256) private String pdplLawfulBasis;

    /** DRAFT/CLASSIFIED/APPROVED/IN_USE/REVIEW_DUE/RECLASSIFY/DECOMMISSIONED. */
    @Column(name = "status", nullable = false, length = 16) private String status = "DRAFT";

    /** Owner role at the bank — DATA_OWNER/STEWARD/DPO_DELEGATE. */
    @Column(name = "owner_role", length = 32) private String ownerRole;
    @Column(name = "owner_user_id") private Long ownerUserId;

    @Column(name = "host_system_label", length = 256) private String hostSystemLabel;

    @Column(name = "retention_years") private Integer retentionYears;
    @Column(name = "encryption_at_rest") private Boolean encryptionAtRest;
    @Column(name = "encryption_in_transit") private Boolean encryptionInTransit;
    @Column(name = "masking_required") private Boolean maskingRequired;

    @Column(name = "classified_at") private LocalDateTime classifiedAt;
    @Column(name = "approved_at")   private LocalDateTime approvedAt;
    @Column(name = "next_review_due") private LocalDate nextReviewDue;
    @Column(name = "decommissioned_at") private LocalDate decommissionedAt;

    @Column(name = "row_count_approx") private Long rowCountApprox;
    @Column(name = "linked_dpia_ref", length = 64) private String linkedDpiaRef;
}
