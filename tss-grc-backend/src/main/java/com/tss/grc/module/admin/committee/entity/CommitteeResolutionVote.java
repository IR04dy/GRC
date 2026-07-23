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

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
