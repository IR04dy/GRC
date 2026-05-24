package com.tss.grc.module.cyber.asm.entity;

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
 * One shadow-IT SaaS / cloud-service discovery — Sprint 11 ASM.
 *
 * <p>Discovery → governance pipeline:
 * <pre>
 *   PENDING_REVIEW → IN_PROGRESS → SANCTIONED (with TPRM vendor link)
 *                                  | MIGRATED_TO_SANCTIONED
 *                                  | BLOCKED (terminal)
 *                                  | NA      (terminal)
 * </pre>
 *
 * <p>{@link #pdplDataProcessed} drives an automatic DPO escalation in
 * {@code ShadowItService} — the {@code SHADOW_IT_DISCOVERED} F8 event
 * carries the flag so the privacy automation rule can pick it up.
 *
 * <p>{@link #sanctionedVendorId} is a soft FK to {@code tprm.vendor} —
 * populated when the entry is promoted to a sanctioned third-party
 * relationship. {@link #promotedAssetId} is a soft FK to
 * {@code cyber.attack_surface_asset} — populated when the service is
 * also tracked as a formal ASM asset post-sanction.
 */
@Entity
@Table(schema = "cyber", name = "attack_surface_shadow_it")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AttackSurfaceShadowIt extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "service_name_ar", length = 255)
    private String serviceNameAr;

    @Column(name = "vendor_domain", length = 255)
    private String vendorDomain;

    /** EMAIL_SIGNUPS | EXPENSE_REPORT | NETWORK_TRAFFIC | USER_DECLARATION */
    @Column(name = "discovered_via", nullable = false, length = 40)
    private String discoveredVia;

    @Column(name = "first_observed_at", nullable = false)
    private LocalDateTime firstObservedAt = LocalDateTime.now();

    @Column(name = "observed_user_count")
    private Integer observedUserCount;

    /** PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED | UNKNOWN */
    @Column(name = "data_classification", nullable = false, length = 20)
    private String dataClassification = "UNKNOWN";

    @Column(name = "pdpl_data_processed", nullable = false)
    private Boolean pdplDataProcessed = Boolean.FALSE;

    /** PENDING_REVIEW | IN_PROGRESS | SANCTIONED | BLOCKED | MIGRATED_TO_SANCTIONED | NA */
    @Column(name = "status", nullable = false, length = 40)
    private String status = "PENDING_REVIEW";

    /** Soft FK to tprm.vendor — set when promoted to SANCTIONED. */
    @Column(name = "sanctioned_vendor_id")
    private Long sanctionedVendorId;

    /** Soft FK to cyber.attack_surface_asset — set when also promoted to ASM register. */
    @Column(name = "promoted_asset_id")
    private Long promotedAssetId;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "notes_en", length = 2000)
    private String notesEn;

    @Column(name = "notes_ar", length = 2000)
    private String notesAr;
}
