package com.tss.grc.module.socops.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Identity & Access Management lifecycle event — one row per
 * joiner/mover/leaver action or privileged-access change. NCA ECC-2
 * control family 2 requires evidence of the full user-lifecycle audit
 * trail; SAMA examiners ask the "who-had-what-when" question with this
 * table in mind.
 */
@Entity
@Table(schema = "socops", name = "iam_jml_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IamJmlEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "event_code", nullable = false, length = 80) private String eventCode;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;

    @Column(name = "subject_user_id") private Long subjectUserId;
    @Column(name = "subject_username", length = 255) private String subjectUsername;
    @Column(name = "subject_email", length = 255) private String subjectEmail;
    @Column(name = "subject_department", length = 120) private String subjectDepartment;
    @Column(name = "subject_job_title", length = 200) private String subjectJobTitle;

    @Column(name = "source_system", nullable = false, length = 60) private String sourceSystem;
    @Column(name = "target_system", length = 200) private String targetSystem;
    @Column(name = "role_or_entitlement", length = 300) private String roleOrEntitlement;
    @Column(nullable = false) private Boolean privileged = false;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String justification;
    @Column(name = "approver_username", length = 255) private String approverUsername;
    @Column(name = "approver_role", length = 120) private String approverRole;

    @Column(nullable = false, length = 30) private String outcome;
    @Column(name = "sod_violation_detected", nullable = false) private Boolean sodViolationDetected = false;
    @Column(name = "sod_violation_detail", columnDefinition = "NVARCHAR(MAX)") private String sodViolationDetail;

    @Column(name = "requested_at") private LocalDateTime requestedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Column(name = "review_due_at") private LocalDateTime reviewDueAt;

    @Column(name = "related_ticket_id", length = 120) private String relatedTicketId;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
