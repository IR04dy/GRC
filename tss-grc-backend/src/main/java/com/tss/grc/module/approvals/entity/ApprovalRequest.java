package com.tss.grc.module.approvals.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(schema = "approvals", name = "requests")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ApprovalRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "entity_type", nullable = false, length = 60) private String entityType;
    @Column(name = "entity_id",   nullable = false, length = 120) private String entityId;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;
    @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)") private String summaryEn;
    @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)") private String summaryAr;

    @Column(name = "requested_by", nullable = false, length = 255) private String requestedBy;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "assigned_to", nullable = false, length = 255) private String assignedTo;
    @Column(name = "original_assigned_to", nullable = false, length = 255) private String originalAssignedTo;

    /** P1/P2/P3/P4. */
    @Column(nullable = false, length = 10) private String priority = "P3";
    @Column(name = "sla_deadline") private LocalDateTime slaDeadline;

    /** PENDING/APPROVED/REJECTED/DELEGATED/EXPIRED */
    @Column(nullable = false, length = 20) private String status = "PENDING";

    @Column(name = "decided_at") private LocalDateTime decidedAt;
    @Column(name = "decided_by", length = 255) private String decidedBy;
    @Column(name = "decision_comment_en", columnDefinition = "NVARCHAR(MAX)") private String decisionCommentEn;
    @Column(name = "decision_comment_ar", columnDefinition = "NVARCHAR(MAX)") private String decisionCommentAr;

    @Column(name = "delegated_from", length = 255) private String delegatedFrom;
    @Column(name = "delegation_reason_en", columnDefinition = "NVARCHAR(MAX)") private String delegationReasonEn;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
