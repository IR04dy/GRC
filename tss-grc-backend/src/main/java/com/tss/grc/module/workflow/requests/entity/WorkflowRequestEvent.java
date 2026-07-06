package com.tss.grc.module.workflow.requests.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * WF-1 — one append-only timeline/audit event on a {@link WorkflowRequest} (item 8 of the
 * remediation roadmap). Every lifecycle transition, reminder, and escalation appends a row:
 * what happened, who did it, from→to status, and the note. Rows are never updated or deleted —
 * this is the request's auditable history (the platform-wide `platform.audit_log` also gets a
 * row via {@code AuditLogService}; this table is the request-local, queryable timeline).
 */
@Entity
@Table(schema = "workflow", name = "request_events")
@Getter @Setter @NoArgsConstructor
public class WorkflowRequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "request_id", nullable = false) private Long requestId;

    /** CREATED / SENT / RESPONDED / SUBMITTED / REVIEWED / APPROVED / REJECTED / RESUBMITTED /
     *  ESCALATED / REMINDED / CLOSED / REOPENED / COMMENT. */
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "from_status", length = 30) private String fromStatus;
    @Column(name = "to_status", length = 30) private String toStatus;
    @Column(length = 150) private String actor;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
