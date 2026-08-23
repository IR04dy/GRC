package com.tss.grc.module.change_mgmt.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "change_mgmt", name = "change_requests")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // F2-286 — the column is insertable = false, so the DATABASE refuses a caller's value
    // and the create hands back the instance Jackson bound: without this the response
    // echoed a value the row does not carry, and only a re-fetch showed the truth. This
    // says the mapping refuses it, not that the caller should never state it — where the
    // caller should, the fix is to make the column insertable (F2-286 did that for
    // ethics.screening_hits.screened_at, whose value nothing could ever state).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "change_code", length = 50)
    private String changeCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    // F2-30 — house rule 7 ("Arabic names are backend-mandatory") was asserted in
    // CLAUDE.md and enforced nowhere. Measured 0 blank rows on this column before the
    // constraint landed, so it cannot strand an existing record.
    @NotBlank
    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "change_type", nullable = false, length = 30)
    // ⚠⚠ F2-294 removed `= "NORMAL"`. `create` already carried
    // `if (c.getChangeType() == null) c.setChangeType("NORMAL")`, dead while this stood.
    // 6 of 17 dev rows and 6 of 15 prod rows differ, and the reset turns the one EMERGENCY
    // change into a NORMAL one — removing it from the population an auditor reviews under
    // ADR-017 D-3's emergency ratification separation. API-reachable only: both callers
    // spread the whole record (`{...c}` in the register form, `{...change}` in the PIR
    // card, whose F2-105 comment explains why it must).
    private String changeType; // STANDARD | NORMAL | EMERGENCY

    @Column(name = "category", length = 40)
    private String category;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "impact_summary", columnDefinition = "NVARCHAR(MAX)")
    private String impactSummary;

    @Column(name = "rollback_plan", columnDefinition = "NVARCHAR(MAX)")
    private String rollbackPlan;

    @Column(name = "test_plan", columnDefinition = "NVARCHAR(MAX)")
    private String testPlan;

    // CH-11: first-class implementation plan (completes the impl/test/rollback trio).
    @Column(name = "implementation_plan", columnDefinition = "NVARCHAR(MAX)")
    private String implementationPlan;

    @Column(name = "business_justification", columnDefinition = "NVARCHAR(MAX)")
    private String businessJustification;

    @Column(name = "requested_by_id")
    private Long requestedById;

    @Column(name = "requested_by_name", length = 255)
    private String requestedByName;

    @Column(name = "change_owner_id")
    private Long changeOwnerId;

    @Column(name = "change_owner_name", length = 255)
    private String changeOwnerName;

    @Column(name = "implementer_name", length = 255)
    private String implementerName;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    // ── S8g / CE-8: the PSO — projected service outage (V356) ────────────────
    //
    // ⚠ These sit on the change rather than in a `change_schedule_entry` table, and the plan
    // specified otherwise. `scheduled_start`/`scheduled_end` above already answer "when is this
    // change happening?" on 15 of 17 dev rows, so a second table holding the same window would give
    // the platform two answers that can disagree — and the FSC and the change register would each be
    // able to cite themselves. The outage window IS this change's implementation window; what was
    // missing is whether service is lost during it, for how long, and to whom.
    //
    // `ck_change_pso` (V356) refuses `outageExpected = true` without a positive duration AND named
    // services: "there will be an outage" with no length and no service is a rumour, not a
    // declaration. It arrives inert — 0 on every existing row, asserted by the migration.

    @Column(name = "outage_expected", nullable = false)
    private Boolean outageExpected = false;

    /** Expected minutes of lost service. Must be > 0 when {@link #outageExpected} is true. */
    @Column(name = "outage_minutes")
    private Integer outageMinutes;

    /** Free text naming the services that lose availability. Mandatory when an outage is declared. */
    @Column(name = "services_affected", length = 1000)
    private String servicesAffected;

    /**
     * Whether this change carries a complete outage declaration.
     *
     * <p>Mirrors {@code ck_change_pso} in Java so the FSC can label an entry without re-testing three
     * fields at every call site. Kept as a method rather than a stored flag for S8f's reason: a
     * derived truth that is also stored is a truth that can disagree with itself.
     */
    @Transient
    public boolean hasDeclaredOutage() {
        return Boolean.TRUE.equals(outageExpected)
            && outageMinutes != null && outageMinutes > 0
            && servicesAffected != null && !servicesAffected.isBlank();
    }

    @Column(name = "implementation_started")
    private LocalDateTime implementationStarted;

    @Column(name = "implementation_ended")
    private LocalDateTime implementationEnded;

    @Column(name = "post_review_date")
    private LocalDate postReviewDate;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "DRAFT";

    @Column(name = "cab_decision_at")
    private LocalDateTime cabDecisionAt;

    @Column(name = "cab_decision_notes", columnDefinition = "NVARCHAR(MAX)")
    private String cabDecisionNotes;

    // F2-93 / A17-D3 (V345) — WHO decided. The record carried cabDecisionAt and
    // cabDecisionNotes and named nobody; the decision's author existed only in the
    // audit ledger. That is a governance gap on its own (an examiner asking "who
    // authorised this emergency change?" should not be routed to a log), and it is
    // also what made D-3's segregation-of-duties test impossible: the ratifier must
    // not be the person who emergency-approved, and there was nothing to compare
    // against. Stamped from the authenticated principal on the three CAB-decision
    // transitions — never from a request body.
    @Column(name = "cab_decision_by_id")
    private Long cabDecisionById;

    @Column(name = "cab_decision_by_name", length = 255)
    private String cabDecisionByName;

    @Column(name = "outcome", length = 30)
    private String outcome;

    // ── A17-D3: retrospective ratification of emergency changes (V345) ────────
    // Orthogonal to `status` on purpose: an emergency change awaiting ratification
    // is genuinely IMPLEMENTED *and* genuinely awaiting ratification, and folding
    // both into one column would discard the half the FSC/calendar/KPI pack read.
    // Vocabulary + rationale: RatificationStates.
    @Column(name = "ratification_state", nullable = false, length = 24)
    private String ratificationState = "NOT_REQUIRED";

    /** Never null while state is PENDING — enforced by ck_change_ratification_clock. */
    @Column(name = "ratification_due_at")
    private LocalDateTime ratificationDueAt;

    @Column(name = "ratified_at")
    private LocalDateTime ratifiedAt;

    @Column(name = "ratified_by_id")
    private Long ratifiedById;

    @Column(name = "ratified_by_name", length = 255)
    private String ratifiedByName;

    @Column(name = "ratification_notes", columnDefinition = "NVARCHAR(MAX)")
    private String ratificationNotes;

    /**
     * Set once when the breach sweep escalates. Deliberately NOT cleared by a
     * later ratification: "ratified late" and "never breached" are different
     * governance facts and must not collapse into the same record.
     */
    @Column(name = "ratification_escalated_at")
    private LocalDateTime ratificationEscalatedAt;

    // CH-11: structured post-implementation review (PIR). post_review_date above
    // records WHEN the review window opened; these record its CONTENT + sign-off.
    @Column(name = "pir_summary", columnDefinition = "NVARCHAR(MAX)")
    private String pirSummary;

    @Column(name = "pir_objectives_met")
    private Boolean pirObjectivesMet;

    @Column(name = "pir_lessons", columnDefinition = "NVARCHAR(MAX)")
    private String pirLessons;

    @Column(name = "pir_follow_up", columnDefinition = "NVARCHAR(MAX)")
    private String pirFollowUp;

    @Column(name = "pir_reviewed_by", length = 255)
    private String pirReviewedBy;

    /**
     * CE-15 / V357 — the principal who recorded the review.
     *
     * ⚠ Until CE-15 the reviewer was {@code pirReviewedBy} alone, set from a client-supplied
     * {@code reviewer} string in the same request. A gate comparing that to the implementer would have
     * compared two strings the same caller types. This is the id the server knows.
     */
    @Column(name = "pir_reviewed_by_id")
    private Long pirReviewedById;

    @Column(name = "pir_reviewed_at")
    private LocalDateTime pirReviewedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
