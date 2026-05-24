package com.tss.grc.module.privacy.subprocessor.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One PDPL sub-processor register row — Sprint 11.
 *
 * <p>Saudi PDPL Article 30 (Implementing Regulation Article 39)
 * requires the controller (the bank) to inventory every sub-processor
 * with its authorisation chain, lawful basis, and cross-border-transfer
 * safeguards. This entity is that inventory.
 *
 * <p>Lifecycle state machine (enforced in
 * {@code SubprocessorService}):
 * <pre>
 *   PROPOSED → UNDER_DPO_REVIEW → APPROVED → SUSPENDED | RETIRED
 *                                    ↘ RETIRED
 *   SUSPENDED → APPROVED (via resume) | RETIRED
 * </pre>
 *
 * <p>DPO approval gate enforces: DPA in place + DPA signed + purpose
 * non-empty + cross-border safeguard set when country != SA.
 */
@Entity
@Table(schema = "privacy", name = "subprocessor")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Subprocessor extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "subprocessor_code", nullable = false, length = 64)
    private String subprocessorCode;

    /** Optional self-FK — for sub-of-a-sub chains. */
    @Column(name = "parent_processor_id")
    private Long parentProcessorId;

    @Column(name = "sub_processor_legal_name", nullable = false, length = 255)
    private String subProcessorLegalName;

    @Column(name = "sub_processor_legal_name_ar", length = 255)
    private String subProcessorLegalNameAr;

    /** Soft FK to {@code tprm.vendor.id}; null when not yet vendor-onboarded. */
    @Column(name = "vendor_id")
    private Long vendorId;

    /** ISO 3166-1 alpha-2, e.g. "SA", "US", "IE". */
    @Column(name = "country_of_processing", nullable = false, length = 2)
    private String countryOfProcessing;

    @Column(name = "region_of_processing", length = 255)
    private String regionOfProcessing;

    /** JSON array — PDPL data categories. Service layer treats as opaque. */
    @Column(name = "data_categories_json", columnDefinition = "NVARCHAR(MAX)")
    private String dataCategoriesJson;

    @Column(name = "data_subject_categories_json", columnDefinition = "NVARCHAR(MAX)")
    private String dataSubjectCategoriesJson;

    @Column(name = "processing_purpose_en", nullable = false, length = 1000)
    private String processingPurposeEn;

    @Column(name = "processing_purpose_ar", length = 1000)
    private String processingPurposeAr;

    /** CONSENT | CONTRACT | LEGAL_OBLIGATION | VITAL_INTEREST | PUBLIC_INTEREST | LEGITIMATE_INTEREST | REGULATORY. */
    @Column(name = "lawful_basis", nullable = false, length = 40)
    private String lawfulBasis;

    /** GENERAL | SPECIFIC. */
    @Column(name = "authorisation_type", nullable = false, length = 20)
    private String authorisationType;

    @Column(name = "authorisation_basis_doc_ref", length = 500)
    private String authorisationBasisDocRef;

    /** KSA_RESIDENT | SCC_PLUS_TIA | ADEQUACY_DECISION | EXPLICIT_CONSENT | OTHER. */
    @Column(name = "cross_border_safeguard_type", length = 40)
    private String crossBorderSafeguardType;

    @Column(name = "cross_border_safeguard_doc_ref", length = 500)
    private String crossBorderSafeguardDocRef;

    @Column(name = "tia_reference", length = 255)
    private String tiaReference;

    @Column(name = "tia_completed_at")
    private LocalDate tiaCompletedAt;

    @Column(name = "adequacy_decision_ref", length = 255)
    private String adequacyDecisionRef;

    /** MINIMAL | LOW | MEDIUM | HIGH | VERY_HIGH. */
    @Column(name = "data_volume_band", nullable = false, length = 20)
    private String dataVolumeBand = "LOW";

    @Column(name = "has_dpa_in_place", nullable = false)
    private Boolean hasDpaInPlace = false;

    @Column(name = "dpa_signed_date")
    private LocalDate dpaSignedDate;

    @Column(name = "dpa_expiry_date")
    private LocalDate dpaExpiryDate;

    /** PROPOSED | UNDER_DPO_REVIEW | APPROVED | SUSPENDED | RETIRED. */
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    private String lifecycleStage = "PROPOSED";

    @Column(name = "dpo_approved_by_user_id")
    private Long dpoApprovedByUserId;

    @Column(name = "dpo_approved_at")
    private LocalDateTime dpoApprovedAt;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "next_review_due_at")
    private LocalDateTime nextReviewDueAt;
}
