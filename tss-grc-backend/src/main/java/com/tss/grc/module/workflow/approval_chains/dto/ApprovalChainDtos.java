package com.tss.grc.module.workflow.approval_chains.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 13 — Approval-chain request / response DTOs.
 *
 * <p>Grouped in a single file to keep the controller surface compact.
 */
public final class ApprovalChainDtos {
    private ApprovalChainDtos() {}

    // ─── Validation regex constants ───────────────────────────────────

    public static final String STEP_TYPE_REGEX = "SERIAL|PARALLEL";
    public static final String INSTANCE_STATUS_REGEX =
        "PENDING|IN_PROGRESS|APPROVED|REJECTED|WITHDRAWN|EXPIRED";
    public static final String ACTION_REGEX =
        "APPROVED|REJECTED|DELEGATED|REQUESTED_CHANGES|RECUSED";

    // ─── Template create / update ────────────────────────────────────

    public record TemplateCreateRequest(
        @Size(max = 64) String templateCode,
        @NotBlank @Size(max = 500) String titleEn,
        @NotBlank @Size(max = 500) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @Size(max = 4000) String appliesToWorkflowCodes,
        @Size(max = 40) String appliesToModule,
        Boolean isDefault,
        @NotNull @Valid List<StepCreateRequest> steps
    ) {}

    public record TemplateUpdateRequest(
        @Size(max = 500) String titleEn,
        @Size(max = 500) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @Size(max = 4000) String appliesToWorkflowCodes,
        @Size(max = 40) String appliesToModule,
        Boolean isDefault
    ) {}

    public record StepCreateRequest(
        @NotNull @Positive Integer stepOrder,
        @Pattern(regexp = STEP_TYPE_REGEX) String stepType,
        @NotBlank @Size(max = 500) String titleEn,
        @NotBlank @Size(max = 500) String titleAr,
        @Size(max = 40) String requiredRole,
        @Positive Long requiredUserId,
        @Size(max = 40) String requiredRoleInChain,
        Boolean isMandatory,
        Boolean canDelegate,
        @PositiveOrZero Integer slaHours,
        @Size(max = 40) String escalationRole
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepResponse(
        Long id,
        Long templateId,
        Integer stepOrder,
        String stepType,
        String titleEn,
        String titleAr,
        String requiredRole,
        Long requiredUserId,
        String requiredRoleInChain,
        Boolean isMandatory,
        Boolean canDelegate,
        Integer slaHours,
        String escalationRole
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TemplateResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String templateCode,
        String titleEn,
        String titleAr,
        String descriptionEn,
        String descriptionAr,
        String appliesToWorkflowCodes,
        String appliesToModule,
        Boolean isDefault,
        List<StepResponse> steps,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Instance start / record / withdraw ───────────────────────────

    public record InstanceStartRequest(
        @NotNull @Positive Long templateId,
        Long sourceWorkflowSubmissionId,
        @Size(max = 40) String sourceEntityType,
        Long sourceEntityId,
        @NotNull @Positive Long originatorUserId,
        @Size(max = 500) String titleEn,
        @Size(max = 500) String titleAr
    ) {}

    public record InstanceActionRequest(
        @NotNull @Positive Integer stepOrder,
        @NotNull @Positive Long actorUserId,
        @NotBlank @Pattern(regexp = ACTION_REGEX) String action,
        @Size(max = 4000) String commentEn,
        @Size(max = 4000) String commentAr,
        Long delegatedToUserId
    ) {}

    public record InstanceWithdrawRequest(
        @NotNull @Positive Long actorUserId,
        @Size(max = 1000) String reasonEn,
        @Size(max = 1000) String reasonAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActionResponse(
        Long id,
        Long instanceId,
        Integer stepOrder,
        Long stepId,
        Long actorUserId,
        String action,
        LocalDateTime decidedAt,
        String commentEn,
        String commentAr,
        Long delegatedToUserId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstanceResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long templateId,
        String templateCode,
        Long sourceWorkflowSubmissionId,
        String sourceEntityType,
        Long sourceEntityId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer currentStepOrder,
        Long originatorUserId,
        String titleEn,
        String titleAr,
        List<ActionResponse> actions,
        List<StepResponse> currentStepDefinitions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    /** Item in the {@code /chains/pending-for-me} list — flat for the UI. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PendingDecisionResponse(
        Long instanceId,
        Long templateId,
        String templateCode,
        String templateTitleEn,
        String templateTitleAr,
        Integer stepOrder,
        Long stepId,
        String stepTitleEn,
        String stepTitleAr,
        String stepType,
        String requiredRole,
        Integer slaHours,
        LocalDateTime startedAt,
        LocalDateTime stepBecameCurrentAt,
        Long originatorUserId
    ) {}
}
