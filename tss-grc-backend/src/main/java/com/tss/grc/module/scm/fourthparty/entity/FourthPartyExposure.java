package com.tss.grc.module.scm.fourthparty.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per (primary vendor → fourth party) edge. See V50 migration.
 *
 * <p>Status machine: {@code DISCOVERED → VALIDATED → MITIGATED → RETIRED}.
 * Concentration risk is derived elsewhere (see {@link
 * com.tss.grc.module.scm.fourthparty.service.FourthPartyExposureService}).
 */
@Entity
@Table(schema = "scm", name = "fourth_party_exposures")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class FourthPartyExposure {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "primary_vendor_id")
    private Long primaryVendorId;

    @Column(name = "primary_vendor_name", nullable = false, length = 255)
    private String primaryVendorName;

    @Column(name = "fourth_party_name", nullable = false, length = 255)
    private String fourthPartyName;

    @Column(name = "fourth_party_country", length = 3)
    private String fourthPartyCountry;

    /** DATA_PROCESSING | INFRASTRUCTURE | SOFTWARE | LOGISTICS | FINANCIAL | SUPPORT. */
    @Column(name = "dependency_type", nullable = false, length = 30)
    private String dependencyType;

    /** LOW | MEDIUM | HIGH | CRITICAL. */
    @Column(name = "downstream_criticality", nullable = false, length = 20)
    private String downstreamCriticality;

    /** LOW | MEDIUM | HIGH. */
    @Column(name = "concentration_risk", nullable = false, length = 20)
    private String concentrationRisk = "LOW";

    @Column(name = "service_description_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String serviceDescriptionEn;

    @Column(name = "service_description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String serviceDescriptionAr;

    @Column(name = "data_categories_shared", columnDefinition = "NVARCHAR(MAX)")
    private String dataCategoriesShared;

    @Column(name = "contract_chain_clauses", columnDefinition = "NVARCHAR(MAX)")
    private String contractChainClauses;

    @Column(name = "sub_contracting_allowed", nullable = false)
    private Short subContractingAllowed = 0;

    @Column(name = "flow_down_clauses_present", nullable = false)
    private Short flowDownClausesPresent = 0;

    @Column(name = "last_validated_at")
    private LocalDateTime lastValidatedAt;

    @Column(name = "last_validated_by")
    private Long lastValidatedBy;

    @Column(name = "validator_name", length = 255)
    private String validatorName;

    @Column(name = "next_validation_due")
    private LocalDate nextValidationDue;

    /** DISCOVERED | VALIDATED | MITIGATED | RETIRED. */
    @Column(nullable = false, length = 20)
    private String status = "DISCOVERED";

    @Column(name = "mitigation_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String mitigationNotesEn;

    @Column(name = "mitigation_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String mitigationNotesAr;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
