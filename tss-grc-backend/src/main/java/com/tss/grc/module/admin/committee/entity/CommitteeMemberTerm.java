package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Term-of-office tracking for committee members — driven by CMA Corporate
 * Governance Regulation (Article 27 — max 3 consecutive terms for non-execs,
 * 3-year term length recommended) and SAMA RGov 2017 (independence test
 * § 4.2.1, audit-committee independence § 5.3).
 *
 * <p>Each row represents a single term of office. Renewals create a new row
 * (with {@code termNumber} incremented and the old row's {@code renewalStatus}
 * set to {@code RENEWED}). End-of-term automation fires AR-154-style F8
 * reminders 90/60/30 days ahead of {@code termEndsAt}.
 *
 * <p>{@code isIndependent} drives the independence-ratio KPI on the
 * committee detail page and the regulatory-quorum check (e.g. Audit
 * Committee must have a majority of independents per CMA Article 54).
 */
@Entity
@Table(schema = "admin", name = "committee_member_terms")
@Getter @Setter @NoArgsConstructor
public class CommitteeMemberTerm {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "member_name", nullable = false, length = 255)
    private String memberName;

    /**
     * The platform user who holds this seat, or NULL. Authoritative for
     * {@code CommitteeResolutionService.recordVote} — the field that decides whether a
     * vote is the member's own or the secretariat's on their behalf, and (after F2-28)
     * a term in the quorum denominator. See {@link #noPlatformUser} for why NULL alone
     * cannot be read as "unlinked".
     */
    @Column(name = "member_user_id")
    private Long memberUserId;

    /**
     * F2-26. 1 = this seat is not expected to have a platform login — an external
     * auditor, an external advisor, a director who attends in person only.
     *
     * <p>Without this flag, a NULL {@code memberUserId} means both "awaiting secretariat
     * action" and "will never be linked", which is why 31 unlinked seats went unnoticed:
     * a coverage report built on NULL either nags about seats that cannot be linked or
     * gets ignored. With it the seat has three states — LINKED, EXTERNAL (declared),
     * UNLINKED (a real gap) — and only the third is a finding.
     */
    @Column(name = "no_platform_user", nullable = false)
    private Boolean noPlatformUser = false;

    /** Why this seat has no platform user. Mandatory whenever {@link #noPlatformUser}
     *  is set — an undocumented "external" claim is the same unevidenced assertion the
     *  flag exists to remove. Enforced in the service and by
     *  {@code ck_cmt_no_platform_user}. */
    @Column(name = "no_platform_user_basis_en", length = 300)
    private String noPlatformUserBasisEn;

    /** Chair | Vice-Chair | Member | Secretary | Standing Invitee | External Advisor */
    @Column(nullable = false, length = 120)
    private String role;

    @Column(name = "is_voting_member", nullable = false)
    private Short isVotingMember = 1;

    /** Per CMA/SAMA independence test — drives the independence-ratio KPI. */
    @Column(name = "is_independent", nullable = false)
    private Short isIndependent = 0;

    /** Free-text justification: "No commercial relationship with the
     *  company or its subsidiaries in the last 3 years…" — exactly what
     *  the CMA Corporate Governance Regulation requires committees to
     *  document. */
    @Column(name = "independence_basis_en", length = 500)
    private String independenceBasisEn;

    @Column(name = "appointed_at", nullable = false)
    private LocalDate appointedAt;

    /** Board-resolution code that appointed this member — links back to
     *  the resolution register for traceability. */
    @Column(name = "appointed_by_reso", length = 80)
    private String appointedByReso;

    @Column(name = "term_starts_at", nullable = false)
    private LocalDate termStartsAt;

    /** NULL for indefinite (management / ex-officio members). */
    @Column(name = "term_ends_at")
    private LocalDate termEndsAt;

    /** CMA Article 27 caps at 3 consecutive terms for non-executive
     *  directors of public companies. */
    @Column(name = "term_number", nullable = false)
    private Integer termNumber = 1;

    /** ACTIVE | EXPIRED | RESIGNED | REMOVED | RENEWED */
    @Column(name = "renewal_status", nullable = false, length = 20)
    private String renewalStatus = "ACTIVE";

    @Column(name = "end_reason", length = 500)
    private String endReason;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * F2-178 — does this seat confer anything <b>today</b>? The Java twin of
     * {@code CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW}.
     *
     * <p><b>Why it exists.</b> {@code renewal_status = 'ACTIVE'} is not the same question as "may this
     * seat authorise something", and the platform now asks both: F2-26's coverage report asks the first,
     * F2-177's seat guard asks the second. Nothing ever transitions an ACTIVE term to EXPIRED — the sweep
     * only sends 90/60/30-day reminders and {@code endTerm} is a manual act — so a term keeps reading
     * ACTIVE for ever once its window closes. This method is what lets the report show the two numbers
     * side by side instead of showing one and meaning the other.
     *
     * <p>⚠ <b>It must match the JPQL constant's three-valued logic exactly, including on NULL.</b>
     * {@code t.termStartsAt <= :on} is UNKNOWN — and therefore excluding — when the column is NULL, so a
     * NULL start returns {@code false} here rather than being treated as "started long ago". The column
     * is {@code nullable = false} and 0 rows in dev or production are NULL, so the branch is unreachable
     * today; it is written and tested anyway, because the alternative is a Java/SQL disagreement that
     * only appears once someone relaxes the column. {@code renewal_status} is deliberately <b>not</b>
     * tested here: it is the caller's half of the predicate, applied in the query's {@code WHERE} and by
     * {@code findActiveByCommittee}, and folding it in would make this method silently answer a different
     * question from the one its name asks.
     *
     * @param on the date to judge the window against — a parameter, not {@code LocalDate.now()}, for the
     *           same reason the repository query takes one (F2-160: the clock is an input)
     */
    public boolean isCurrentOn(LocalDate on) {
        if (on == null) return false;
        if (endedAt != null) return false;
        if (termStartsAt == null || termStartsAt.isAfter(on)) return false;
        return termEndsAt == null || !termEndsAt.isBefore(on);
    }
}
