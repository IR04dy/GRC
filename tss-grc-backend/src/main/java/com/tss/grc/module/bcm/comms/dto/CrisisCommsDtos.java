package com.tss.grc.module.bcm.comms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for the Slice 9-E crisis-comms (v2) endpoints — templates +
 * dispatch cascades.
 */
public final class CrisisCommsDtos {
    private CrisisCommsDtos() {}

    // ─── Template create / update request ───────────────────────────
    public record TemplateRequest(
        @Size(max = 64) String templateCode,
        @NotBlank @Size(max = 255) String titleEn,
        @NotBlank @Size(max = 255) String titleAr,
        @NotBlank @Pattern(regexp = "BOARD|REGULATOR|CUSTOMERS|STAFF|PARTNERS|MEDIA|PUBLIC") String audience,
        @NotBlank @Pattern(regexp = "RANSOMWARE|DATA_BREACH|OUTAGE|NATURAL_DISASTER|OT_INCIDENT|REGULATORY_NOTIFICATION|MEDIA_RESPONSE|RECOVERY|LESSONS_LEARNED|GENERIC_MAJOR") String scenarioCategory,
        @NotBlank @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|INFORMATIONAL") String severityBand,
        @NotBlank @Size(max = 255) String subjectEn,
        @NotBlank @Size(max = 255) String subjectAr,
        @NotBlank String bodyEn,
        @NotBlank String bodyAr,
        @Size(max = 500) String smsBodyEn,
        @Size(max = 500) String smsBodyAr,
        String placeholdersJson,
        @Size(max = 500) String regulatoryAnchor
    ) {}

    // ─── Approval transition ────────────────────────────────────────
    public record ApprovalTransitionRequest(
        @NotBlank @Pattern(regexp = "DRAFT|IN_REVIEW|APPROVED_BY_LEGAL|APPROVED_BY_CISO|RETIRED") String approvalStatus,
        @Size(max = 255) String approverName,
        Long approverUserId
    ) {}

    // ─── Template response ──────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TemplateResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String templateCode,
        String titleEn,
        String titleAr,
        String audience,
        String scenarioCategory,
        String severityBand,
        String subjectEn,
        String subjectAr,
        String bodyEn,
        String bodyAr,
        String smsBodyEn,
        String smsBodyAr,
        String placeholdersJson,
        String regulatoryAnchor,
        String approvalStatus,
        LocalDateTime approvedByLegalAt,
        String approvedByLegalName,
        LocalDateTime approvedByCisoAt,
        String approvedByCisoName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Dispatch request ───────────────────────────────────────────
    public record DispatchRequest(
        @NotNull Long crisisEventId,
        @NotNull Long templateId,
        @NotNull List<@Pattern(regexp = "EMAIL|SMS|WHATSAPP|TEAMS|VOICE_BLAST") String> channels,
        @Size(max = 40) String audienceFilter,
        List<@NotBlank String> recipients,
        Map<String, String> placeholders,
        @Size(max = 255) String dispatchedByName,
        Long dispatchedByUserId
    ) {}

    // ─── Cascade response ───────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CascadeResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long crisisEventId,
        Long templateId,
        String channel,
        String audience,
        String renderedSubject,
        Integer recipientCount,
        Integer successCount,
        Integer failureCount,
        LocalDateTime dispatchedAt,
        LocalDateTime completedAt,
        String dispatchedByName,
        String status,
        String responsePayload,
        /** F2-147 — true when nothing left the building on this channel. */
        Boolean simulated
    ) {}

    /**
     * Multi-row response for one dispatch action (one row per channel).
     *
     * <p>{@code simulatedChannels} is the honest half of the roll-up
     * (F2-147). {@code totalSuccess} answers "how many were counted", which
     * an operator reads as "how many were told"; the two are the same number
     * only when this list is empty. It is a list rather than a count so the
     * UI can name the channels without walking the cascade rows.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DispatchResult(
        Long crisisEventId,
        Long templateId,
        List<CascadeResponse> cascades,
        Integer totalRecipients,
        Integer totalSuccess,
        Integer totalFailure,
        Long elapsedMillis,
        List<String> simulatedChannels
    ) {}

    /**
     * Per-channel readiness — what an operator needs <em>before</em> pulling
     * the trigger, not after (F2-147).
     *
     * @param channelType  EMAIL | SMS | WHATSAPP | TEAMS | VOICE_BLAST
     * @param registered   a {@code ChannelSender} bean exists for this type
     * @param healthy      the sender reports itself wired and ready
     * @param deliveryMode LIVE | SIMULATED — whether a gateway is involved
     */
    public record ChannelReadiness(
        String channelType,
        boolean registered,
        boolean healthy,
        String deliveryMode
    ) {}
}
