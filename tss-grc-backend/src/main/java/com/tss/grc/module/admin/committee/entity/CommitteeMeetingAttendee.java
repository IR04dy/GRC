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
