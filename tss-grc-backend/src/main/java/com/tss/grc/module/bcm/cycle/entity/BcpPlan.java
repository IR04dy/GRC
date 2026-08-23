package com.tss.grc.module.bcm.cycle.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BCP / DRP plan (parent record) — audit T1.4b plan builder.
 *
 * <p>One row per plan; sections live in {@link BcpSection}; version snapshots
 * in {@link BcpVersion}. Same table holds both BCP and DRP (distinguished by
 * {@code planType}); the only difference is the section catalogue the UI
 * presents at creation time.
 *
 * <p>Status lifecycle:
 * <pre>
 *   DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED → ARCHIVED
 *   DRAFT → CANCELLED
 *   PUBLISHED → SUPERSEDED  (when a newer version takes over)
 * </pre>
 *
 * <p>On APPROVED, the service snapshots all sections into {@link BcpVersion}
 * and bumps {@code currentVersion}. Editing an APPROVED/PUBLISHED plan
 * requires reopening to DRAFT via {@code BcpPlanService.reopen()}.
 */
@Entity
@Table(schema = "bcm", name = "bcp_plans")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcpPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    /** BCP | DRP */
    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType = "BCP";

    @Column(name = "source_bia_id") private Long sourceBiaId;
    @Column(name = "service_id")    private Long serviceId;
    @Column(name = "process_id")    private Long processId;

    @Column(name = "rto_hours")  private Integer rtoHours;
    @Column(name = "rpo_hours")  private Integer rpoHours;
    @Column(name = "mtpd_hours") private Integer mtpdHours;

    @Column(name = "plan_owner_id")      private Long   planOwnerId;
    @Column(name = "response_team_role", length = 100) private String responseTeamRole;

    /**
     * DRAFT | UNDER_REVIEW | APPROVED | PUBLISHED | ARCHIVED | SUPERSEDED | CANCELLED
     *
     * <p>F2-297 — deliberately NOT initialised. This field carried {@code = "DRAFT"}, and because
     * {@code PATCH /api/v1/bcm/plans/{id}} binds this entity, an omitted {@code status} arrived as
     * "DRAFT" rather than null — indistinguishable from a caller asking to move the plan back. The
     * guard in {@code BcpPlanService#update} therefore fired on every plan that was not already
     * DRAFT and not already locked, so the BCP Builder's edit form — which sends no status at all —
     * answered 400 "Use transition() to change status" naming a state its form does not offer.
     *
     * <p>F2-238 removed exactly this initialiser from {@code CrisisEvent}, {@code Issue} and
     * {@code BcmBiaAssessment} — the three PATCH-bound siblings of this one — and left this fourth.
     * The create path already owns the default through {@code ServerOwnedState.requireInitial} with
     * {@code BcpPlanService.INITIAL_STATUS} (F2-220), so the initialiser was pure residue on create
     * and a refusal on update. Do not restore it: the column is NOT NULL and the create writes it.
     */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_by", length = 255) private String        approvedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at")               private LocalDateTime approvedAt;
    @Column(name = "published_at")              private LocalDateTime publishedAt;
    @Column(name = "last_exercised_at")         private LocalDateTime lastExercisedAt;
    @Column(name = "next_review_date")          private LocalDate     nextReviewDate;

    // Tier-2 #10: attestation linkage. Bumped by BcpPlanService.attest() so
    // the UI can render "Last reviewed by X on Y". reviewState is denormalised
    // off next_review_date so list-pages can filter without recomputing.
    @Column(name = "last_attested_at")             private LocalDateTime lastAttestedAt;
    @Column(name = "last_attested_by_name", length = 255) private String  lastAttestedByName;
    @Column(name = "last_attested_by_id")          private Long          lastAttestedById;
    /** CURRENT | DUE_SOON | OVERDUE — recomputed on attest + on read-sweep. */
    @Column(name = "review_state", length = 20)
    private String reviewState = "CURRENT";

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;
    @Column(name = "created_by")                    private Long createdBy;
    @Column(name = "updated_by")                    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
