package com.tss.grc.module.outsourcing.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One cloud service subscription / SaaS deployment under a parent
 * outsourcing arrangement (SAMA Cloud Computing Regulatory Framework).
 *
 * <p>Lifecycle:
 * {@code ONBOARDING → CLASSIFIED → APPROVED → IN_USE → DEPRECATED
 *  → DECOMMISSIONED}, with {@code BLOCKED} from {@code CLASSIFIED} if
 * the data classification fails the residency gate.
 *
 * <p>Approval gate: T3 + T4 require {@code dataResidency=IN_KINGDOM};
 * T2 may be IN_KINGDOM or GCC; T1 may be GLOBAL.
 */
@Entity
@Table(schema = "outsourcing", name = "cloud_service")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CloudService extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "arrangement_id", nullable = false)
    private Long arrangementId;

    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "service_name_en", nullable = false, length = 256)
    private String serviceNameEn;

    @Column(name = "service_name_ar", nullable = false, length = 256)
    private String serviceNameAr;

    /** SAAS/PAAS/IAAS/FAAS/HYBRID. */
    @Column(name = "deployment_model", nullable = false, length = 16)
    private String deploymentModel;

    /** PUBLIC/PRIVATE/HYBRID/COMMUNITY. */
    @Column(name = "service_model", length = 16)
    private String serviceModel;

    @Column(name = "csp_legal_name_en", nullable = false, length = 256)
    private String cspLegalNameEn;

    @Column(name = "csp_legal_name_ar", nullable = false, length = 256)
    private String cspLegalNameAr;

    /** AWS/AZURE/GOOGLE_CLOUD/STC_CLOUD/MOBILY_CLOUD/SALESFORCE/ORACLE_CLOUD/OTHER. */
    @Column(name = "csp_brand", length = 32)
    private String cspBrand;

    /** SAMA Cloud Framework §4 — T1 PUBLIC / T2 INTERNAL / T3 CONFIDENTIAL / T4 RESTRICTED. */
    @Column(name = "data_classification", length = 12)
    private String dataClassification;

    /** IN_KINGDOM/GCC/GLOBAL — primary processing/storage region. */
    @Column(name = "data_residency", length = 12)
    private String dataResidency;

    /** Region label as the CSP names it (e.g. me-south-1, KSA-Riyadh). */
    @Column(name = "csp_region_label", length = 64)
    private String cspRegionLabel;

    /** ONBOARDING/CLASSIFIED/APPROVED/IN_USE/DEPRECATED/DECOMMISSIONED/BLOCKED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ONBOARDING";

    @Column(name = "classified_at")
    private LocalDateTime classifiedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "in_use_at")
    private LocalDate inUseAt;

    @Column(name = "deprecated_at")
    private LocalDate deprecatedAt;

    @Column(name = "decommissioned_at")
    private LocalDate decommissionedAt;

    @Column(name = "block_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String blockReasonEn;

    @Column(name = "block_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String blockReasonAr;

    /** Approving authority on file: CISO/CIO/Sharia Officer (if applicable)/Risk Committee. */
    @Column(name = "approver_role", length = 32)
    private String approverRole;

    @Column(name = "supports_pdpl_dpia_ref", length = 64)
    private String supportsPdplDpiaRef;
}
