package com.tss.grc.module.policy.drafting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request and response DTOs for AI-assisted policy drafting.
 */
public final class PolicyDraftDtos {

    private PolicyDraftDtos() {}

    public record CreatePolicyDraftRequest(
        @NotBlank(message = "policyType is required")
        @Size(max = 60)
        String policyType,

        @NotBlank(message = "category is required")
        @Size(max = 60)
        String category,

        @Size(max = 40)
        String tone,

        List<Long> frameworkIds,
        List<Long> controlIds,
        String applicabilityScope,
        String customInstructions
    ) {}

    public record UpdatePolicyDraftRequest(
        @NotBlank(message = "titleEn is required")
        @Size(max = 255)
        String titleEn,

        @NotBlank(message = "titleAr is required")
        @Size(max = 255)
        String titleAr,

        String summaryEn,
        String summaryAr,

        @NotBlank(message = "contentMdEn is required")
        String contentMdEn,

        @NotBlank(message = "contentMdAr is required")
        String contentMdAr,

        List<Long> citedControlIds,

        @Size(max = 20)
        String suggestedReviewCadence,

        List<String> assumptions,

        @Size(max = 1000)
        String reviewerNote
    ) {}

    public record AcceptPolicyDraftRequest(
        @NotBlank(message = "policyCode is required")
        @Size(max = 40)
        String policyCode,

        @Size(max = 20)
        String version,

        @Size(max = 255)
        String ownerName,

        @Size(max = 255)
        String ownerEmail,

        @Size(max = 20)
        String reviewCadence,

        String applicabilityScope,

        @Size(max = 1000)
        String reviewerNote
    ) {}

    public record RejectPolicyDraftRequest(
        @NotBlank(message = "reviewerNote is required")
        @Size(max = 1000)
        String reviewerNote
    ) {}

    public record GeneratedPolicyContent(
        String titleEn,
        String titleAr,
        String summaryEn,
        String summaryAr,
        String contentMdEn,
        String contentMdAr,
        List<Long> citedControlIds,
        String suggestedReviewCadence,
        List<String> assumptions
    ) {}

    public record PolicyDraftResponse(
        Long id,
        String externalId,
        String status,
        String policyType,
        String category,
        String tone,
        CreatePolicyDraftRequest request,
        GeneratedPolicyContent generated,
        GeneratedPolicyContent edited,
        List<Long> hallucinatedCitations,
        String promptTemplateId,
        String promptTemplateVersion,
        String provider,
        String model,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        String failureReason,
        Long acceptedPolicyId,
        String requestedBy,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewerNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
