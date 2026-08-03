package com.tss.grc.module.admin.committee.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * First-class committee resolution — replaces the V40
 * {@code decisions_json} blob with structured rows that are queryable,
 * exportable to the board resolutions register, and dependency-trackable
 * from other modules.
 *
 * <p>Lifecycle: resolutions are created at meeting time (when a motion
 * is carried) and never change outcome after the meeting closes — they
 * become immutable records of what the committee decided. The
 * {@code reviewRequiredBy} column triggers an F8 reminder when the
 * resolution sunsets and needs renewal.
 *
 * <p>The {@code regulatorNotify} flag marks resolutions that trigger
 * downstream regulator notification (CMA disclosure, SAMA material
 * decision, SDAIA breach notification, etc.) — picked up by an automation
 * rule that pre-fills the notification queue.
 */
@Entity
@Table(schema = "admin", name = "committee_resolutions")
@Getter @Setter @NoArgsConstructor
public class CommitteeResolution {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "resolution_code", nullable = false, length = 80)
    private String resolutionCode;

    @Column(nullable = false)
    private Integer seq;

    @Column(name = "agenda_item_seq")
    private Integer agendaItemSeq;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    // F2-30 — house rule 7 ("Arabic names are backend-mandatory") was asserted in
    // CLAUDE.md and enforced nowhere. Measured 0 blank rows on this column before the
    // constraint landed, so it cannot strand an existing record.
    @NotBlank
    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    // F2-30 — house rule 7 ("Arabic names are backend-mandatory") was asserted in
    // CLAUDE.md and enforced nowhere. Measured 0 blank rows on this column before the
    // constraint landed, so it cannot strand an existing record. The resolution BODY is
    // the operative text of a board decision, so an Arabic-only tenant reading a
    // resolution in English is exactly the failure house rule 7 exists to prevent.
    @NotBlank
    @Column(name = "body_ar", columnDefinition = "NVARCHAR(MAX)")
    private String bodyAr;

    @Column(name = "proposed_by", length = 255)
    private String proposedBy;

    @Column(name = "seconded_by", length = 255)
    private String secondedBy;

    @Column(name = "vote_for", nullable = false)
    private Integer voteFor = 0;

    @Column(name = "vote_against", nullable = false)
    private Integer voteAgainst = 0;

    @Column(name = "vote_abstain", nullable = false)
    private Integer voteAbstain = 0;

    /**
     * PROPOSED | APPROVED | REJECTED | DEFERRED | WITHDRAWN — see
     * {@link com.tss.grc.module.admin.committee.service.CommitteeDecisionAuthority}, which owns
     * the vocabulary and the {@code isDecided} split.
     *
     * <p>S7a / V349 added {@code PROPOSED}. Before it, all four permitted values were
     * <em>decisions</em>, so a motion could not be raised without recording a verdict for it
     * (F2-89) — and since {@code create()} published {@code COMMITTEE_RESOLUTION_<outcome>}, every
     * placeholder a caller could pick fired an enabled rule: APPROVED started a regulator
     * disclosure clock, DEFERRED asked the secretary to re-table a motion never tabled.
     */
    @Column(nullable = false, length = 20)
    private String outcome;

    /** PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED */
    @Column(nullable = false, length = 20)
    private String confidentiality = "INTERNAL";

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "review_required_by")
    private LocalDate reviewRequiredBy;

    @Column(name = "regulator_notify", nullable = false)
    private Short regulatorNotify = 0;

    @Column(name = "dissent_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String dissentNotesEn;

    @Column(name = "dissent_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String dissentNotesAr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── F2-40 / V322 — where this tally came from ────────────────────────────

    /**
     * <b>{@code COMPUTED}</b> — derived by {@code recomputeTally()} from
     * {@code committee_resolution_vote} rows. <b>{@code ASSERTED}</b> — recorded by a named user
     * from minutes of a vote taken outside the platform, which is what the secretariat form on
     * {@code CommitteesPage} does and a legitimate flow for a board that votes on paper.
     * <b>{@code ASSERTED_LEGACY}</b> — present before V322, provenance unknown: all 20 rows that
     * existed then, every one APPROVED with a scalar tally and not one vote row.
     *
     * <p>Labelling rather than refusing is the whole correction here. The obvious fix — "the
     * tally is caller-authored, so ignore the caller's tally" — would have silently discarded the
     * recorded decision the UI exists to capture. What was actually missing is the ability to
     * tell an asserted tally from a computed one, which is also what stops
     * {@code recomputeTally()} overwriting a recorded board decision with {@code 0/0/0 DEFERRED}.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "tally_source", length = 20)
    private String tallySource;

    /**
     * Who asserted it. Server-assigned from the authenticated principal and
     * {@code READ_ONLY} for the same reason DF-3 made the voter server-assigned: a provenance
     * field a caller can set records nothing. {@code NULL} on {@code COMPUTED} rows (the votes
     * carry their own attribution) and on the {@code ASSERTED_LEGACY} rows, where there is no
     * attribution to record and inventing one would be worse than the gap.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "tally_recorded_by_user_id")
    private Long tallyRecordedByUserId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "tally_recorded_by_name", length = 255)
    private String tallyRecordedByName;

    /** When the source was last established — asserted at create, or recomputed from votes. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "tally_stamped_at")
    private LocalDateTime tallyStampedAt;

    // ── F2-100 / V349 — when the binding decision was announced ───────────────

    /**
     * When {@code COMMITTEE_RESOLUTION_<outcome>} was published for this motion, or {@code NULL}
     * if the decision has not become binding yet.
     *
     * <p><b>This is the idempotency fact</b> for the three sites that can make a decision binding
     * — creating a resolution on already-approved minutes, casting the vote that decides it, and
     * approving the minutes it sits in. Whichever happens last publishes; the other two find this
     * stamped and do nothing. A column rather than the audit-marker scan
     * {@code CommitteeScheduledSweep} uses, because F2-90 had just established that the ledger was
     * not being written for meeting actions at all — an idempotency check that reads a log which
     * may never have been written will re-announce.
     *
     * <p>{@code READ_ONLY}: a caller who could set this could suppress the announcement of their
     * own committee's decision, which is the DF-3 rule (a provenance field a caller can set
     * records nothing).
     *
     * <p>⚠ Rows that pre-date V349 are stamped with their {@code created_at} as a
     * <b>suppression</b> — the platform will not announce a historical board decision as new. For
     * the four rows AR-147 had already fired on that is literally true; for the rest it is a
     * suppression and the migration says so rather than implying a history it does not have.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "decision_published_at")
    private LocalDateTime decisionPublishedAt;
}
