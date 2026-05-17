package com.tss.grc.module.admin.committee.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class CommitteeDtos {
    private CommitteeDtos() {}

    // ── Committee ────────────────────────────────────────────────────
    public record CommitteeCreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String nameEn,
            String nameAr,
            String descriptionEn, String descriptionAr,
            @NotBlank @Size(max = 30) String committeeType,
            String chairpersonName, String chairpersonRole,
            String secretaryName,
            String membersJson, String charterEn, String charterAr,
            String meetingCadence, Integer quorumPct
    ) {}

    public record CommitteeUpdateRequest(
            String nameEn, String nameAr,
            String descriptionEn, String descriptionAr,
            String chairpersonName, String chairpersonRole, String secretaryName,
            String membersJson, String charterEn, String charterAr,
            String meetingCadence, Integer quorumPct
    ) {}

    public record CommitteeDissolveRequest(@NotBlank @Size(max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CommitteeResponse(
            Long id, String code, String externalId,
            String nameEn, String nameAr,
            String descriptionEn, String descriptionAr,
            String committeeType,
            String chairpersonName, String chairpersonRole, String secretaryName,
            String membersJson, String charterEn, String charterAr,
            String meetingCadence, Integer quorumPct,
            String status,
            LocalDateTime dissolvedAt, String dissolvedReason,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static CommitteeResponse from(Committee c) {
            return new CommitteeResponse(
                    c.getId(), c.getCode(), c.getExternalId(),
                    c.getNameEn(), c.getNameAr(),
                    c.getDescriptionEn(), c.getDescriptionAr(),
                    c.getCommitteeType(),
                    c.getChairpersonName(), c.getChairpersonRole(), c.getSecretaryName(),
                    c.getMembersJson(), c.getCharterEn(), c.getCharterAr(),
                    c.getMeetingCadence(), c.getQuorumPct(),
                    c.getStatus(),
                    c.getDissolvedAt(), c.getDissolvedReason(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    // ── Meeting ──────────────────────────────────────────────────────
    public record MeetingCreateRequest(
            @NotNull Long committeeId,
            String titleEn, String titleAr,
            @NotNull LocalDateTime scheduledAt,
            String locationOrLink,
            String agendaJson,
            String chairName, String secretaryName
    ) {}

    public record MeetingUpdateRequest(
            String titleEn, String titleAr,
            LocalDateTime scheduledAt,
            String locationOrLink,
            String agendaJson,
            String chairName, String secretaryName
    ) {}

    public record MeetingMinutesRequest(
            String minutesEn, String minutesAr,
            String attendeesJson, Short quorumMet,
            String decisionsJson, String actionItemsJson
    ) {}

    public record MeetingCancelRequest(@NotBlank @Size(max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeetingResponse(
            Long id, String meetingCode, String externalId,
            Long committeeId, String committeeCode, String committeeNameEn,
            String titleEn, String titleAr,
            LocalDateTime scheduledAt, LocalDateTime startedAt, LocalDateTime endedAt,
            String locationOrLink,
            String agendaJson, String attendeesJson, Short quorumMet,
            String minutesEn, String minutesAr,
            String decisionsJson, String actionItemsJson,
            String chairName, String secretaryName,
            String status, String cancelReason,
            LocalDateTime minutesApprovedAt, String approverName,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static MeetingResponse from(CommitteeMeeting m) {
            return new MeetingResponse(
                    m.getId(), m.getMeetingCode(), m.getExternalId(),
                    m.getCommitteeId(), m.getCommitteeCode(), m.getCommitteeNameEn(),
                    m.getTitleEn(), m.getTitleAr(),
                    m.getScheduledAt(), m.getStartedAt(), m.getEndedAt(),
                    m.getLocationOrLink(),
                    m.getAgendaJson(), m.getAttendeesJson(), m.getQuorumMet(),
                    m.getMinutesEn(), m.getMinutesAr(),
                    m.getDecisionsJson(), m.getActionItemsJson(),
                    m.getChairName(), m.getSecretaryName(),
                    m.getStatus(), m.getCancelReason(),
                    m.getMinutesApprovedAt(), m.getApproverName(),
                    m.getCreatedAt(), m.getUpdatedAt());
        }
    }
}
