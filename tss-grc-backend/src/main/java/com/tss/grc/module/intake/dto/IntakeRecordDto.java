package com.tss.grc.module.intake.dto;

import com.tss.grc.module.intake.entity.IntakeEvent;
import com.tss.grc.module.intake.entity.IntakeRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wire-format mirror of the React IntakeRecord interface (intake.ts).
 * Field names intentionally match the frontend so JSON binding is direct.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IntakeRecordDto {

    private Long id;
    private String referenceCode;
    private String kind;
    private String status;
    private String channel;

    private Boolean submitterIsAnonymous;
    private String  submitterRole;
    private String  submitterName;
    private String  submitterEmail;
    private String  submitterPhone;

    private String payload;       // Raw JSON string — frontend parses.

    private String        assignedTo;
    private LocalDateTime slaDueAt;
    private String        severity;

    private LocalDateTime acknowledgedAt;

    private String        promotedToEntityType;
    private String        promotedToRecordId;
    private LocalDateTime promotedAt;

    private String rejectionReason;
    private String locale;

    private List<EventDto> history;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EventDto {
        private LocalDateTime at;
        private String status;
        private String actor;
        private String note;
    }

    public static IntakeRecordDto from(IntakeRecord r) {
        return IntakeRecordDto.builder()
            .id(r.getId())
            .referenceCode(r.getReferenceCode())
            .kind(r.getKind())
            .status(r.getStatus())
            .channel(r.getChannel())
            .submitterIsAnonymous(r.getSubmitterIsAnonymous())
            .submitterRole(r.getSubmitterRole())
            .submitterName(r.getSubmitterName())
            .submitterEmail(r.getSubmitterEmail())
            .submitterPhone(r.getSubmitterPhone())
            .payload(r.getPayload())
            .assignedTo(r.getAssignedTo())
            .slaDueAt(r.getSlaDueAt())
            .severity(r.getSeverity())
            .acknowledgedAt(r.getAcknowledgedAt())
            .promotedToEntityType(r.getPromotedToEntityType())
            .promotedToRecordId(r.getPromotedToRecordId())
            .promotedAt(r.getPromotedAt())
            .rejectionReason(r.getRejectionReason())
            .locale(r.getLocale())
            .history(r.getHistory().stream().map(IntakeRecordDto::eventOf).collect(Collectors.toList()))
            .build();
    }

    private static EventDto eventOf(IntakeEvent e) {
        return EventDto.builder()
            .at(e.getAt())
            .status(e.getStatus())
            .actor(e.getActor())
            .note(e.getNote())
            .build();
    }
}
