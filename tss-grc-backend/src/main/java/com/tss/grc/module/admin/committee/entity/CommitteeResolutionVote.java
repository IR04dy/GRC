package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * CH-14: one member's vote on a committee resolution. Aggregate tallies on the
 * resolution are derived from these rows; RECUSED votes are excluded from the
 * tally. A member who declared a conflict of interest on the meeting attendance
 * is forced to RECUSED.
 */
@Entity
@Table(schema = "admin", name = "committee_resolution_vote")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CommitteeResolutionVote {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "resolution_id", nullable = false)
    private Long resolutionId;

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "member_name", nullable = false, length = 255)
    private String memberName;

    @Column(name = "member_role", length = 120)
    private String memberRole;

    @Column(name = "vote", nullable = false, length = 20)
    private String vote;   // FOR | AGAINST | ABSTAIN | RECUSED

    @Column(name = "rationale", columnDefinition = "NVARCHAR(MAX)")
    private String rationale;

    // ── DF-3 / F2-21: actor provenance ──────────────────────────────────────
    // Before this, memberName came straight from the request body and nothing
    // recorded WHO submitted it — so a vote could be cast in any name, recusal
    // was evaluated against the claimed name, and the audit ledger repeated the
    // forgery. These three columns make the real actor part of the record.

    /** The authenticated user who recorded this row. **NULL means "provenance
     *  unknown (pre-fix)"** on rows written before V315 — deliberately not
     *  back-filled with a guess, because inventing an actor for a historical
     *  governance decision is the defect, not the fix. */
    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    /** Username of that actor, denormalised for minutes and exports. */
    @Column(name = "recorded_by_name", length = 255)
    private String recordedByName;

    /** 1 when a secretary/chair recorded this vote on the member's behalf
     *  (the legitimate "show of hands in the room" case), 0 when the member
     *  cast it themselves. Visible in the minutes — an on-behalf vote is
     *  allowed, but it is never silent. */
    @Column(name = "on_behalf", nullable = false)
    private Boolean onBehalf = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
