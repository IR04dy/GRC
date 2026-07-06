package com.tss.grc.module.workflow.requests.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * WF-1 — one recipient of a {@link WorkflowRequest} (the actor-selector model, §5 of the spec).
 *
 * <p>A recipient is addressed by category — {@code USER} (recipientId = user id), {@code ROLE}
 * (recipientKey = role name), {@code DEPARTMENT} (recipientId = department id), {@code COMMITTEE}
 * (recipientId = committee id) or {@code EXTERNAL} (recipientKey = contact email) — and carries
 * the actor role it plays in the request (ASSIGNEE / REVIEWER / APPROVER / OBSERVER / ESCALATION)
 * plus its sequential {@code stepNo}. Its own status tracks the per-recipient response.
 */
@Entity
@Table(schema = "workflow", name = "request_recipients")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WorkflowRequestRecipient extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "request_id", nullable = false) private Long requestId;

    /** USER / ROLE / DEPARTMENT / COMMITTEE / EXTERNAL. */
    @Column(name = "recipient_type", nullable = false, length = 20) private String recipientType;
    @Column(name = "recipient_id") private Long recipientId;
    /** Role name (ROLE) or external contact, e.g. email (EXTERNAL). */
    @Column(name = "recipient_key", length = 120) private String recipientKey;
    /** Human label captured at send time so the chain reads even if the target is renamed. */
    @Column(name = "recipient_label", length = 200) private String recipientLabel;

    /** ASSIGNEE / REVIEWER / APPROVER / OBSERVER / ESCALATION. */
    @Column(name = "actor_role", nullable = false, length = 30) private String actorRole = "ASSIGNEE";
    /** Sequential routing order (1-based). All recipients share step 1 in parallel mode. */
    @Column(name = "step_no", nullable = false) private Integer stepNo = 1;

    /** PENDING / NOTIFIED / IN_PROGRESS / RESPONDED / APPROVED / REJECTED / SKIPPED. */
    @Column(nullable = false, length = 30) private String status = "PENDING";
    @Column(name = "responded_at") private LocalDateTime respondedAt;
    @Column(name = "responded_by") private Long respondedBy;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String response;
    /** WF-6 — outcome token the responder chose (drives conditional branch rules). */
    @Column(name = "response_outcome", length = 40) private String responseOutcome;

    /** WF-7 — opaque credential minted on send for an EXTERNAL recipient (the ONLY key to the
     *  public tracker). Null for internal recipients. */
    @Column(name = "access_token", length = 64) private String accessToken;
    @Column(name = "token_expires_at") private LocalDateTime tokenExpiresAt;
}
