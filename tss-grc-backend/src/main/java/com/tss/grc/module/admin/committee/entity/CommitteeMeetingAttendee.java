package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * CH-14: one row per attendee of a committee meeting. Enables a DETERMINISTIC
 * quorum computation (present voting members vs the committee's quorum_pct)
 * instead of the old manually-set flag, and captures a per-attendee
 * conflict-of-interest declaration.
 */
@Entity
@Table(schema = "admin", name = "committee_meeting_attendee")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CommitteeMeetingAttendee {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "committee_meeting_id", nullable = false)
    private Long committeeMeetingId;

    /**
     * F2-28 — the roster seat this row records, or NULL for a declared guest.
     *
     * <p>Before V319 an attendance row was a **free-text name**, and the quorum denominator was
     * a count of those rows — so inventing six voting attendees and marking four present made a
     * committee quorate. Binding to {@code committee_member_terms.id} means the row refers to a
     * seat that exists on this committee's active roster, and {@link #isVotingMember} is copied
     * from that seat rather than taken from the caller.
     */
    @Column(name = "member_term_id")
    private Long memberTermId;

    /**
     * F2-28 — 1 = a non-member who was in the room: an external auditor presenting, a
     * subject-matter expert, a regulator observer. Recorded because the minutes must be
     * factual; never counted toward quorum in either the numerator or the denominator.
     *
     * <p>Without an explicit guest state, a secretary recording reality would have to enter a
     * guest as a member — which is how a denominator gets inflated — or be blocked from
     * recording who actually attended. {@code ck_cma_seat_or_guest} makes "neither a seat nor a
     * guest" unrepresentable, which is exactly the free-text state this closes.
     */
    @Column(name = "is_guest", nullable = false)
    private Boolean isGuest = false;

    /** Display name. For a seat this is copied from the roster at recording time (so the
     *  minutes read as they did on the day); for a guest it is supplied by the secretary. */
    @Column(name = "member_name", nullable = false, length = 255)
    private String memberName;

    @Column(name = "member_role", length = 120)
    private String memberRole;

    @Column(name = "is_voting_member", nullable = false)
    private Boolean isVotingMember = true;

    @Column(name = "present", nullable = false)
    private Boolean present = false;

    @Column(name = "coi_declared", nullable = false)
    private Boolean coiDeclared = false;

    @Column(name = "coi_note", columnDefinition = "NVARCHAR(MAX)")
    private String coiNote;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
