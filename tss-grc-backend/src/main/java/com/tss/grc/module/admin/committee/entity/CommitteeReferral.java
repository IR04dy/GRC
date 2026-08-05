package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Track D: links a Change or Policy to the committee that decides it, so the
 * committee engine becomes the system-of-record for referred approvals. The
 * link is additive — a source item with no referral keeps its legacy CAB /
 * approval path unchanged.
 */
@Entity
@Table(schema = "admin", name = "committee_referral")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CommitteeReferral {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "source_module", nullable = false, length = 30)
    private String sourceModule;   // CHANGE | POLICY

    @Column(name = "source_entity_id", nullable = false)
    private Long sourceEntityId;

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "resolution_id")
    private Long resolutionId;

    @Column(name = "gate", nullable = false, length = 10)
    private String gate;   // CAB | ECAB

    @Column(name = "mode", nullable = false, length = 20)
    private String mode = "APPROVAL";   // APPROVAL | RATIFICATION

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";  // PENDING | SCHEDULED | DECIDED | WITHDRAWN

    @Column(name = "referred_by", length = 150)
    private String referredBy;

    /** CSV of names (requester/author + owner) force-RECUSED from the committee
     *  vote on this item — SoD, so the requester can't cast the deciding vote.
     *
     *  <p>⚠ F2-113 — <b>this is the fallback, not the primary, and it must not be
     *  retired.</b> Measured 2026-08-05: a change's people are team labels
     *  ('CloudOps', 'IAM Team') and a committee's members are people
     *  ('Khalid Al-Rashidi'), so on its own this column could never match — the bar
     *  had never engaged for any change in either database. {@link #recusalUserIds}
     *  is what makes it able to. But 29 of dev's 42 active seats carry no
     *  {@code member_user_id} (and {@code committee_member_terms.no_platform_user}
     *  exists because an external director legitimately never gets one), so for those
     *  seats the name is the only comparable thing there is. Both columns are read. */
    @Column(name = "recusal_names", length = 500)
    private String recusalNames;

    /**
     * F2-113 / V358 — CSV of <b>platform user ids</b> force-RECUSED from the vote on this item.
     *
     * <p>The half of the comparison that can actually match. Populated from the referred record's
     * captured identity ({@code change_requests.requested_by_id} / {@code change_owner_id}, which
     * CE-15 began writing) and compared against {@code committee_member_terms.member_user_id},
     * which b901faec began populating. Before those two commits neither side existed, which is why
     * this arrives after {@link #recusalNames} rather than instead of it.
     *
     * <p>Unpaired with the names by design: {@code recordVote} asks set membership on each side
     * independently, exactly as the name CSV was always read. The cost is a possible refusal on two
     * different people who share a label where one side is unlinked — a spurious RECUSED, which
     * subtracts one member from a tally and is the safe direction, and is what the name-only
     * comparison already did.
     */
    @Column(name = "recusal_user_ids", length = 200)
    private String recusalUserIds;

    // ── S7b / V350 — the agenda line's label, snapshotted at referral time ───────────

    /**
     * The referring module's own reference for the item (e.g. {@code CHG-2025-0014}).
     *
     * <p><b>Why the referral carries this instead of the agenda builder joining.</b> The agenda is
     * built in {@code admin/committee} and the item is a {@code change_mgmt} record; S5a took that
     * module's peer imports to zero behind {@code CommitteePort} and {@code ChangeModuleBoundaryTest}
     * fails if they return. Reading {@code change_requests} to render an agenda line would be a new
     * reverse dependency in the direction there is no port for. The referring module supplies the
     * label at referral time — the same direction the port already runs.
     *
     * <p>A snapshot is also the right semantics: an agenda records what was put before the
     * committee <em>under the title it was tabled under</em>, not whatever the record was renamed
     * to afterwards.
     *
     * <p>⚠ <b>It is also the agenda builder's presence flag.</b> Only referrals carrying a
     * {@code sourceCode} are put on an agenda. Pre-V350 rows have none — and that is what keeps
     * dev's two orphaned referrals (#7 and #8 point at change ids 101 and 102, which exist in no
     * organisation) off it. A builder keyed on {@code status = 'PENDING'} alone would have listed
     * two items that do not exist while omitting all three real ones.
     */
    @Column(name = "source_code", length = 80)
    private String sourceCode;

    /** What the committee is being asked to decide, in English. */
    @Column(name = "subject_en", length = 500)
    private String subjectEn;

    /** …and in Arabic (house rule 7). Nullable: the referring module may genuinely hold no Arabic
     *  title, and refusing the referral over a label would block the governance path to protect
     *  the label — S6b-ii's lesson. */
    @Column(name = "subject_ar", length = 500)
    private String subjectAr;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
