package com.tss.grc.module.tprm.outsourcing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — One outsourcing arrangement under SAMA Rules Governing
 * Outsourcing (2019, updated 2023).
 *
 * <p>An arrangement is the contractual layer above one or more vendors
 * (the {@code primary_vendor_id} FK plus the optional {@code co_vendor_ids}
 * CSV). Bank operations register both material and non-material
 * arrangements here. SAMA non-objection is mandatory for material
 * arrangements before go-live ({@code §4.2}).
 *
 * <h3>Lifecycle state machine</h3>
 * <pre>
 *   PLANNED          → DUE_DILIGENCE | EXITED
 *   DUE_DILIGENCE    → AWAITING_SAMA | APPROVED | EXITED
 *   AWAITING_SAMA    → APPROVED | EXITED
 *   APPROVED         → ACTIVE | EXITED
 *   ACTIVE           → MONITORING_ALERT | UNDER_REVIEW | EXIT_PLANNING | EXITED
 *   MONITORING_ALERT → ACTIVE | UNDER_REVIEW | EXIT_PLANNING | EXITED
 *   UNDER_REVIEW     → ACTIVE | EXIT_PLANNING | EXITED
 *   EXIT_PLANNING    → EXIT_IN_PROGRESS | EXITED
 *   EXIT_IN_PROGRESS → EXITED
 *   EXITED           (terminal)
 * </pre>
 */
@Entity
@Table(schema = "tprm", name = "outsourcing_arrangement")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OutsourcingArrangement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "arrangement_code", nullable = false, length = 64)
    private String arrangementCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /**
     * IT_INFRASTRUCTURE | CLOUD_SAAS | CUSTOMER_OPERATIONS |
     * BACK_OFFICE_PROCESSING | CALL_CENTRE | CASH_LOGISTICS |
     * MARKETING | LEGAL | INTERNAL_AUDIT | IT_DEVELOPMENT |
     * ADMINISTRATIVE | SHARIA_AUDIT | ANALYTICS_BI |
     * AML_KYC_SCREENING | PRINTING_DISTRIBUTION | FACILITY_MGMT.
     */
    @Column(name = "service_type", nullable = false, length = 40)
    private String serviceType;

    @Column(name = "primary_vendor_id")
    private Long primaryVendorId;

    /** CSV of secondary vendor IDs for multi-vendor arrangements. */
    @Column(name = "co_vendor_ids", length = 500)
    private String coVendorIds;

    @Column(name = "is_material", nullable = false)
    private Boolean isMaterial = false;

    @Column(name = "materiality_basis_en", length = 1000)
    private String materialityBasisEn;

    @Column(name = "materiality_basis_ar", length = 1000)
    private String materialityBasisAr;

    @Column(name = "sama_non_objection_required", nullable = false)
    private Boolean samaNonObjectionRequired = false;

    @Column(name = "sama_non_objection_obtained", nullable = false)
    private Boolean samaNonObjectionObtained = false;

    @Column(name = "sama_non_objection_ref", length = 255)
    private String samaNonObjectionRef;

    @Column(name = "sama_non_objection_date")
    private LocalDate samaNonObjectionDate;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    @Column(name = "contract_value_sar_annual", precision = 18, scale = 2)
    private BigDecimal contractValueSarAnnual;

    @Column(name = "contract_ref", length = 255)
    private String contractRef;

    @Column(name = "contract_document_url", length = 500)
    private String contractDocumentUrl;

    /**
     * PAYMENTS | CORE_BANKING | AML_CFT | CUSTOMER_DATA | CYBERSECURITY |
     * BCM_DRP | REGULATORY_REPORTING | FINANCIAL_REPORTING | NA.
     */
    @Column(name = "critical_function", nullable = false, length = 40)
    private String criticalFunction = "NA";

    @Column(name = "country_of_processing", nullable = false, length = 2)
    private String countryOfProcessing;

    @Column(name = "region_of_processing", length = 255)
    private String regionOfProcessing;

    @Column(name = "data_residency_kdc", nullable = false)
    private Boolean dataResidencyKdc = true;

    @Column(name = "personal_data_processed", nullable = false)
    private Boolean personalDataProcessed = false;

    @Column(name = "pdpl_dpa_signed", nullable = false)
    private Boolean pdplDpaSigned = false;

    @Column(name = "pdpl_dpa_date")
    private LocalDate pdplDpaDate;

    /**
     * Cross-border safeguard pattern when {@code data_residency_kdc = 0}.
     * SCC_PLUS_TIA | NDMO_GA | BCR | DEROGATION | NA.
     */
    @Column(name = "cross_border_safeguard_type", length = 40)
    private String crossBorderSafeguardType;

    /**
     * PLANNED | DUE_DILIGENCE | AWAITING_SAMA | APPROVED | ACTIVE |
     * MONITORING_ALERT | UNDER_REVIEW | EXIT_PLANNING |
     * EXIT_IN_PROGRESS | EXITED.
     */
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    private String lifecycleStage = "PLANNED";

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "sponsor_user_id", nullable = false)
    private Long sponsorUserId;

    @Column(name = "next_review_due_date")
    private LocalDate nextReviewDueDate;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
