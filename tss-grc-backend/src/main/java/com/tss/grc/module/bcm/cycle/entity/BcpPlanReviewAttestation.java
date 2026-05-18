package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit-trail row per BCP/DRP plan review attestation — Tier-2 #10.
 *
 * <p>Plan owners attest periodically (default: every 6 months for critical
 * plans, 12 months for the rest) that the plan content is still accurate.
 * Every attestation creates a row here so ISO 22301 §7.4 evidence is
 * reconstructible: who attested, when, what version, and what the review
 * window was advanced to.
 *
 * <p>Outcomes:
 * <ul>
 *   <li>{@code STILL_CURRENT}     — owner confirms no material change;
 *                                   next_review_date advanced</li>
 *   <li>{@code CHANGES_REQUIRED}  — owner flags edits needed; next_review_date
 *                                   stays put so the plan keeps surfacing</li>
 *   <li>{@code REOPENED_TO_EDIT}  — written automatically when the plan is
 *                                   reopened from APPROVED/PUBLISHED back to
 *                                   DRAFT for substantive edits</li>
 * </ul>
 */
@Entity
@Table(schema = "bcm", name = "plan_review_attestations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcpPlanReviewAttestation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion;

    @Column(name = "attested_at")
    private LocalDateTime attestedAt = LocalDateTime.now();

    @Column(name = "attested_by_id")
    private Long attestedById;

    @Column(name = "attested_by_name", nullable = false, length = 255)
    private String attestedByName;

    /** STILL_CURRENT | CHANGES_REQUIRED | REOPENED_TO_EDIT */
    @Column(name = "attestation_outcome", nullable = false, length = 20)
    private String attestationOutcome = "STILL_CURRENT";

    @Column(length = 1000)
    private String notes;

    @Column(name = "previous_next_review_date")
    private LocalDate previousNextReviewDate;

    @Column(name = "new_next_review_date")
    private LocalDate newNextReviewDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
