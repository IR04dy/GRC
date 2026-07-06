package com.tss.grc.module.workflow.requests.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** WF-1 — request/response DTOs for the workflow request engine (no entity binding, ASM-style). */
public final class WorkflowRequestDtos {

    private WorkflowRequestDtos() {}

    /** One recipient in the actor-selector payload (§5). */
    public record RecipientDto(
        @NotBlank @Size(max = 20) String recipientType,   // USER / ROLE / DEPARTMENT / COMMITTEE / EXTERNAL
        Long recipientId,                                  // user / department / committee id
        @Size(max = 120) String recipientKey,              // role name or external contact
        @Size(max = 200) String recipientLabel,
        @Size(max = 30) String actorRole,                  // ASSIGNEE (default) / REVIEWER / APPROVER / OBSERVER / ESCALATION
        Integer stepNo                                     // sequential order; defaults to 1
    ) {}

    /** WF-6 — one conditional-routing rule (§7 #5): when the recipient completing
     *  {@code stepNo} responds with {@code onOutcome}, the branch overrides default routing. */
    public record BranchRuleDto(
        Integer stepNo,
        @NotBlank @Size(max = 40) String onOutcome,
        @NotBlank @Size(max = 20) String action,           // GOTO_STEP / RETURN / CLOSE / ESCALATE
        Integer targetStep                                 // required for GOTO_STEP (forward-only)
    ) {}

    public record CreateRequest(
        @NotBlank @Size(max = 40) String sourceModule,
        @Size(max = 60) String sourceRecordType,
        Long sourceRecordId,
        @NotBlank @Size(max = 40) String requestType,
        @NotBlank @Size(max = 300) String titleEn,
        @Size(max = 300) String titleAr,
        String description,
        @Size(max = 20) String routingMode,                // SEQUENTIAL / PARALLEL (default) / BROADCAST_ACK
        @Size(max = 10) String completionRule,             // ALL (default) / ANY
        @Size(max = 60) String requiredAction,
        LocalDate dueDate,
        Integer slaHours,
        @Size(max = 20) String priority,                   // LOW / MEDIUM (default) / HIGH / CRITICAL
        @Size(max = 20) String severity,
        Integer reminderDaysBefore,
        @Size(max = 60) String escalationRole,
        Long escalationUserId,
        Boolean responseRequired,
        Boolean evidenceRequired,
        @Size(max = 300) String closureCondition,
        Long reviewerId,
        Long approverId,
        @Valid List<RecipientDto> recipients,
        @Valid List<BranchRuleDto> branchRules,            // WF-6 — optional conditional routing
        Long recipientListId,                              // WF-8 — expand a saved distribution list into recipients
        Boolean expandCommittees,                          // WF-9 — materialize COMMITTEE recipients into per-member gates
        Integer completionThreshold                        // WF-9 — QUORUM: min responses required
    ) {}

    /** Body for transition endpoints (respond/review/approve/reject/…). */
    public record ActionRequest(String note, String outcome) {}

    // ─── WF-8 reusable recipient lists (distribution lists) ──────────────
    public record RecipientListMemberDto(
        Long id,
        @NotBlank @Size(max = 20) String recipientType,
        Long recipientId,
        @Size(max = 120) String recipientKey,
        @Size(max = 200) String recipientLabel,
        @Size(max = 30) String actorRole,
        Integer stepNo
    ) {}

    public record CreateRecipientList(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Valid List<RecipientListMemberDto> members
    ) {}

    public record RecipientListResponse(
        Long id, String name, String description, Boolean isActive,
        List<RecipientListMemberDto> members
    ) {}

    /** WF-8 — one suggested recipient for a source record (composer aid, §2.1/§2.2). */
    public record RecipientSuggestion(
        String recipientType, Long recipientId, String recipientKey,
        String recipientLabel, String actorRole, String reason
    ) {}

    /** WF-7 — the MINIMAL public projection an EXTERNAL recipient sees via their token (§7 #18):
     *  the request's own facts + the external recipient's own status only. Deliberately carries
     *  NO internal ids, NO other recipients, NO timeline. */
    public record PublicRequestView(
        String requestCode, String requestType, String titleEn, String titleAr,
        String requiredAction, String description,
        LocalDate dueDate, String status, String countdownStatus, String priority,
        String requestedBy, String recipientLabel, String recipientStatus, boolean canRespond
    ) {}

    public record RecipientResponse(
        Long id, String recipientType, Long recipientId, String recipientKey, String recipientLabel,
        String actorRole, Integer stepNo, String status,
        LocalDateTime respondedAt, Long respondedBy, String response, String responseOutcome
    ) {}

    public record EventResponse(
        Long id, String eventType, String fromStatus, String toStatus,
        String actor, Long actorUserId, String note, LocalDateTime createdAt
    ) {}

    public record RequestResponse(
        Long id, String requestCode,
        String sourceModule, String sourceRecordType, Long sourceRecordId,
        String requestType, String titleEn, String titleAr, String description,
        Long initiatedBy, String initiatedByName,
        String recipientCategory, String routingMode, String completionRule, Integer completionThreshold, Integer currentStep,
        String requiredAction, LocalDate dueDate, Integer slaHours,
        String countdownStatus, Long hoursRemaining,
        String priority, String severity,
        Integer reminderDaysBefore, LocalDateTime lastReminderAt, Integer reminderCount,
        String escalationRole, Long escalationUserId, LocalDateTime escalatedAt,
        String status, Boolean responseRequired, Boolean evidenceRequired, String closureCondition,
        Long reviewerId, Long approverId, Long evidenceRequestId,
        LocalDateTime closedAt, LocalDateTime reopenedAt,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        List<RecipientResponse> recipients,
        List<BranchRuleDto> branchRules                    // WF-6
    ) {}
}
