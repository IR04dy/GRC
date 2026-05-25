package com.tss.grc.module.sharia.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * New / re-priced Islamic-banking product going through the SSB
 * approval pipeline.
 *
 * <p>Per SAMA §4.2 + AAOIFI GSIFI 3, no Islamic-banking product may
 * be sold to customers without {@code APPROVED_FINAL} state on this
 * record.
 *
 * <p>Lifecycle:
 * <pre>
 *   SUBMITTED → SSB_REVIEW → APPROVED_PROVISIONAL → APPROVED_FINAL → LAUNCHED → DECOMMISSIONED
 *                          → REJECTED (terminal)
 * </pre>
 *
 * <p>{@code APPROVED_PROVISIONAL} is a sandbox / pilot launch
 * permission — the bank may run a closed-group beta to validate
 * operational mechanics before the SSB locks the final fatwa.
 */
@Entity
@Table(schema = "sharia", name = "sharia_product_approval")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ShariaProductApproval extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, e.g. {@code PA-2026-021}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "product_name_en", nullable = false, length = 256)
    private String productNameEn;

    @Column(name = "product_name_ar", nullable = false, length = 256)
    private String productNameAr;

    /** Product structure: {@code MURABAHA | IJARA | TAWARRUQ | SUKUK | MUDARABA | MUSHARAKA | SALAM | ISTISNA | OTHER}. */
    @Column(name = "structure", nullable = false, length = 32)
    private String structure;

    /** Status: {@code SUBMITTED | SSB_REVIEW | APPROVED_PROVISIONAL | APPROVED_FINAL | LAUNCHED | DECOMMISSIONED | REJECTED}. */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "SUBMITTED";

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** Linked resolution that approves this product (set when APPROVED_FINAL). */
    @Column(name = "linked_resolution_id")
    private Long linkedResolutionId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "ssb_review_started_at")
    private LocalDateTime ssbReviewStartedAt;

    @Column(name = "approved_provisional_at")
    private LocalDateTime approvedProvisionalAt;

    @Column(name = "approved_final_at")
    private LocalDateTime approvedFinalAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "launched_at")
    private LocalDate launchedAt;

    @Column(name = "decommissioned_at")
    private LocalDate decommissionedAt;

    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonEn;

    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonAr;

    @Column(name = "business_owner_user_id")
    private Long businessOwnerUserId;

    @Column(name = "sharia_officer_user_id")
    private Long shariaOfficerUserId;
}
