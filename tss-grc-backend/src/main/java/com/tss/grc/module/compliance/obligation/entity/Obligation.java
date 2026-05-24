package com.tss.grc.module.compliance.obligation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 11 — Regulatory Obligation register entry.
 *
 * <p>One long-lived row per recurring regulatory or internal obligation
 * the bank must satisfy (file STR within 24h, submit quarterly CAR
 * returns to SAMA by day 15 of M+1, publish PDPL RoPA annually, etc.).
 * Per-cycle evidence lives in {@link ObligationAttestation}, launched
 * by an {@link ObligationAttestationCampaign}.
 *
 * <p>Mandated by SAMA Compliance Framework (Aug 2019) §3.2 and NCA
 * ECC-2 control 1-1 (Governance — "documented and assigned
 * cybersecurity obligations").
 *
 * <h3>Lifecycle state machine</h3>
 * <pre>
 *   DRAFT     → ACTIVE | RETIRED
 *   ACTIVE    → SUSPENDED | RETIRED
 *   SUSPENDED → ACTIVE | RETIRED
 *   RETIRED   (terminal)
 * </pre>
 */
@Entity
@Table(schema = "compliance", name = "obligation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Obligation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** e.g. {@code OBL-SAMA-2026-001} — human-readable, used in PDFs / emails. */
    @Column(name = "obligation_code", nullable = false, length = 64)
    private String obligationCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /**
     * SAMA, NCA, SDAIA, CMA, ZATCA, MOH, GAZT, MOJ, NCEMA, CITC, MOC, MOI, INTERNAL.
     */
    @Column(name = "regulator", nullable = false, length = 40)
    private String regulator;

    @Column(name = "regulation_reference", length = 500)
    private String regulationReference;

    /**
     * REPORTING | ATTESTATION | DOCUMENTATION | NOTIFICATION | AUDIT |
     * TRAINING | REVIEW | FILING | REGISTRATION.
     */
    @Column(name = "obligation_type", nullable = false, length = 40)
    private String obligationType;

    /**
     * DAILY | WEEKLY | MONTHLY | QUARTERLY | SEMI_ANNUAL | ANNUAL |
     * BIENNIAL | ON_EVENT | AD_HOC.
     */
    @Column(name = "frequency", nullable = false, length = 40)
    private String frequency;

    /** CRITICAL | HIGH | MEDIUM | LOW. */
    @Column(name = "severity_if_missed", nullable = false, length = 20)
    private String severityIfMissed = "MEDIUM";

    @Column(name = "first_due_date")
    private LocalDate firstDueDate;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    /** Heads-up window for the dashboard / reminders. */
    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays = 14;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "backup_owner_user_id")
    private Long backupOwnerUserId;

    @Column(name = "business_unit_en", length = 255)
    private String businessUnitEn;

    @Column(name = "business_unit_ar", length = 255)
    private String businessUnitAr;

    @Column(name = "related_control_id")
    private Long relatedControlId;

    @Column(name = "related_risk_id")
    private Long relatedRiskId;

    /** DRAFT | ACTIVE | SUSPENDED | RETIRED. */
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    private String lifecycleStage = "DRAFT";

    @Column(name = "last_attested_at")
    private LocalDateTime lastAttestedAt;

    @Column(name = "last_attested_by_user_id")
    private Long lastAttestedByUserId;

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
