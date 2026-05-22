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

    @Column(name = "member_user_id")
    private Long memberUserId;

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
}
