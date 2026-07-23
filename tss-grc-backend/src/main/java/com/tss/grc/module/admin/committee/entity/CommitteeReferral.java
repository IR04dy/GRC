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

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
