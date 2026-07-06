package com.tss.grc.module.workflow.requests.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * WF-1 — the platform-level workflow REQUEST object (§6 of the workflow-logic remediation spec).
 *
 * <p>Before this, Send/Request/Assign actions across modules were generic buttons — no recipient
 * selection, no routing mode, no lifecycle, no SLA. A WorkflowRequest knows: who asked and why
 * (source module/record), who must act ({@link WorkflowRequestRecipient} rows — user/role/
 * department/committee/external, each with an actor role and a sequential step), HOW it routes
 * (SEQUENTIAL / PARALLEL with an ALL/ANY completion rule / BROADCAST_ACK), when it is due
 * (due date + SLA hours + reminder/escalation path), and WHERE it stands (a 16-state lifecycle).
 * Every transition appends a {@link WorkflowRequestEvent} — the auditable timeline.
 */
@Entity
@Table(schema = "workflow", name = "requests")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WorkflowRequest extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "source_module", nullable = false, length = 40) private String sourceModule;
    @Column(name = "source_record_type", length = 60) private String sourceRecordType;
    @Column(name = "source_record_id") private Long sourceRecordId;

    /** EVIDENCE_REQUEST / APPROVAL / REVIEW / TASK / ACKNOWLEDGEMENT / INFORMATION / REPORT_DISTRIBUTION / FOLLOW_UP / CUSTOM. */
    @Column(name = "request_type", nullable = false, length = 40) private String requestType;

    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String description;

    @Column(name = "initiated_by") private Long initiatedBy;
    @Column(name = "initiated_by_name", length = 150) private String initiatedByName;

    /** Primary recipient category summary (USER / ROLE / DEPARTMENT / COMMITTEE / EXTERNAL / MIXED). */
    @Column(name = "recipient_category", length = 30) private String recipientCategory;

    /** SEQUENTIAL / PARALLEL / BROADCAST_ACK. */
    @Column(name = "routing_mode", nullable = false, length = 20) private String routingMode = "PARALLEL";
    /** Parallel completion rule: ALL recipients must respond, or ANY one suffices. */
    @Column(name = "completion_rule", nullable = false, length = 10) private String completionRule = "ALL";
    /** Sequential routing pointer — the step whose recipients may act now. */
    @Column(name = "current_step", nullable = false) private Integer currentStep = 1;

    @Column(name = "required_action", length = 60) private String requiredAction;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "sla_hours") private Integer slaHours;
    @Column(nullable = false, length = 20) private String priority = "MEDIUM";
    @Column(length = 20) private String severity;

    @Column(name = "reminder_days_before", nullable = false) private Integer reminderDaysBefore = 3;
    @Column(name = "last_reminder_at") private LocalDateTime lastReminderAt;
    @Column(name = "reminder_count", nullable = false) private Integer reminderCount = 0;

    @Column(name = "escalation_role", length = 60) private String escalationRole;
    @Column(name = "escalation_user_id") private Long escalationUserId;
    @Column(name = "escalated_at") private LocalDateTime escalatedAt;

    @Column(nullable = false, length = 30) private String status = "DRAFT";

    @Column(name = "response_required", nullable = false) private Boolean responseRequired = true;
    @Column(name = "evidence_required", nullable = false) private Boolean evidenceRequired = false;
    @Column(name = "closure_condition", length = 300) private String closureCondition;

    @Column(name = "reviewer_id") private Long reviewerId;
    @Column(name = "approver_id") private Long approverId;

    /** WF-4 — the real evidence.evidence_requests row this request opened on send (bridge). */
    @Column(name = "evidence_request_id") private Long evidenceRequestId;

    /** WF-6 — conditional-routing rules as a JSON array (§7 #5):
     *  [{stepNo, onOutcome, action: GOTO_STEP|RETURN|CLOSE|ESCALATE, targetStep}]. */
    @Column(name = "branch_rules", columnDefinition = "NVARCHAR(MAX)") private String branchRules;

    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "reopened_at") private LocalDateTime reopenedAt;
}
