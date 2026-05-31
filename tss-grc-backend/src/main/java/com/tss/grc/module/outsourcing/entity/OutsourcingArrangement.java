package com.tss.grc.module.outsourcing.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Master outsourcing-arrangement record (SAMA Outsourcing Regulation §3-§8).
 *
 * <p>Lifecycle:
 * {@code PROPOSED → RISK_ASSESSED → BOARD_APPROVED → SAMA_NOTIFIED → LIVE
 *  → UNDER_REVIEW → EXITED}, with {@code REJECTED} from {@code PROPOSED}
 * or {@code RISK_ASSESSED}.
 *
 * <p>Material outsourcing must transition through {@code SAMA_NOTIFIED}
 * within 60 calendar days of {@code BOARD_APPROVED}.
 */
@Entity(name = "OutsourcingCloudArrangement")
@Table(schema = "outsourcing", name = "outsourcing_arrangement")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OutsourcingArrangement extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** Service provider legal name. */
    @Column(name = "provider_legal_name_en", nullable = false, length = 256)
    private String providerLegalNameEn;

    @Column(name = "provider_legal_name_ar", nullable = false, length = 256)
    private String providerLegalNameAr;

    @Column(name = "provider_country", length = 8)
    private String providerCountry;

    /** SAUDI/GCC/GLOBAL — geographic dimension for §8 cross-border gate. */
    @Column(name = "provider_jurisdiction", length = 16)
    private String providerJurisdiction;

    @Column(name = "service_title_en", nullable = false, length = 256)
    private String serviceTitleEn;

    @Column(name = "service_title_ar", nullable = false, length = 256)
    private String serviceTitleAr;

    @Column(name = "service_description_en", columnDefinition = "NVARCHAR(MAX)")
    private String serviceDescriptionEn;

    @Column(name = "service_description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String serviceDescriptionAr;

    /** CORE_BANKING/PAYMENTS/CARDS/CUSTODY/MARKET_DATA/IT_INFRASTRUCTURE/CLOUD/CALL_CENTRE/COLLECTIONS/PRINTING/HR/LEGAL/AUDIT/MARKETING/OTHER. */
    @Column(name = "service_category", nullable = false, length = 40)
    private String serviceCategory;

    /** MATERIAL/NON_MATERIAL — SAMA §4 materiality flag. */
    @Column(name = "materiality", nullable = false, length = 16)
    private String materiality = "NON_MATERIAL";

    /** True iff this is "core banking activity outsourcing" requiring non-objection letter. */
    @Column(name = "is_core_activity")
    private Boolean isCoreActivity = false;

    /** PROPOSED/RISK_ASSESSED/BOARD_APPROVED/SAMA_NOTIFIED/LIVE/UNDER_REVIEW/EXITED/REJECTED. */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "PROPOSED";

    /** Risk-assessment summary score (LOW/MEDIUM/HIGH/CRITICAL). */
    @Column(name = "risk_rating", length = 16)
    private String riskRating;

    @Column(name = "risk_assessed_at")
    private LocalDateTime riskAssessedAt;

    @Column(name = "board_approved_at")
    private LocalDateTime boardApprovedAt;

    @Column(name = "sama_notified_at")
    private LocalDateTime samaNotifiedAt;

    @Column(name = "sama_non_objection_ref", length = 64)
    private String samaNonObjectionRef;

    @Column(name = "live_at")
    private LocalDate liveAt;

    @Column(name = "exited_at")
    private LocalDate exitedAt;

    /** Reachable from LIVE/UNDER_REVIEW when SAMA opens inquiry or breach is being investigated. */
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonEn;

    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonAr;

    @Column(name = "contract_start")
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    @Column(name = "renewal_notice_days")
    private Integer renewalNoticeDays;

    @Column(name = "annual_spend_sar", precision = 18, scale = 2)
    private BigDecimal annualSpendSar;

    /** Next ongoing-review due date (SAMA §6). */
    @Column(name = "next_review_due")
    private LocalDate nextReviewDue;

    /** Review cadence in months (typically 12 for material, 24 for non-material). */
    @Column(name = "review_cadence_months")
    private Integer reviewCadenceMonths;

    @Column(name = "relationship_owner_user_id")
    private Long relationshipOwnerUserId;

    @Column(name = "risk_owner_user_id")
    private Long riskOwnerUserId;

    @Column(name = "linked_vendor_id")
    private Long linkedVendorId;
}
