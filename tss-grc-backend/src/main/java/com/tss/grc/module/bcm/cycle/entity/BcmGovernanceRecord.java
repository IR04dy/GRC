package com.tss.grc.module.bcm.cycle.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BCM governance record — policy + scope + committee + review cycle.
 * Lifecycle: DRAFT → UNDER_REVIEW → APPROVED → ACTIVE; CHANGES_REQUIRED
 * loops back; REVIEW_DUE fires when next_review_date has passed;
 * ARCHIVED is terminal.
 */
@Entity
@Table(schema = "bcm", name = "governance_records")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmGovernanceRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "policy_title_en", nullable = false, length = 255)
    private String policyTitleEn;

    @Column(name = "policy_title_ar", length = 255)
    private String policyTitleAr;

    @Column(name = "policy_version", nullable = false, length = 20)
    // ⚠ F2-294 removed `= "1.0"`. `createGovernance` already carried
    // `if (body.getPolicyVersion() == null) body.setPolicyVersion("1.0")`, which this
    // initialiser made unreachable. SCREEN-REACHABLE: `BcmReadinessPage#submitEditGov`
    // sends `policyVersion: String(v.policyVersion ?? '').trim() || undefined`, so
    // clearing the field in the edit form omitted the key and reset the version rather
    // than leaving it alone. 0 of 1 rows differ today, so latent by data, not by code.
    private String policyVersion;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String scope;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String objective;

    @Column(name = "bcm_manager_id")
    private Long bcmManagerId;

    @Column(name = "executive_sponsor_id")
    private Long executiveSponsorId;

    @Column(name = "committee_id")
    private Long committeeId;

    /** ANNUAL | SEMI_ANNUAL | QUARTERLY | ON_CHANGE */
    @Column(name = "review_frequency", length = 30)
    private String reviewFrequency;

    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "evidence_id")
    private Long evidenceId;

    /** DRAFT | UNDER_REVIEW | CHANGES_REQUIRED | APPROVED | ACTIVE | REVIEW_DUE | ARCHIVED */
    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "DRAFT";

    @Column(name = "approved_by", length = 120)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvedBy;

    @Column(name = "approved_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime approvedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
