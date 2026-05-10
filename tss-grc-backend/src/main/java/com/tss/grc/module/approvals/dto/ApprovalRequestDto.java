package com.tss.grc.module.approvals.dto;

import com.tss.grc.module.approvals.entity.ApprovalRequest;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalRequestDto {
    private String id;
    @NotBlank private String entityType;
    @NotBlank private String entityId;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    private String summaryEn;
    private String summaryAr;
    @NotBlank private String requestedBy;
    private LocalDateTime requestedAt;
    @NotBlank private String assignedTo;
    private String originalAssignedTo;
    @NotBlank @Pattern(regexp = "P1|P2|P3|P4")
    private String priority;
    private LocalDateTime slaDeadline;
    private String status;
    private LocalDateTime decidedAt;
    private String decidedBy;
    private String decisionCommentEn;
    private String decisionCommentAr;
    private String delegatedFrom;
    private String delegationReasonEn;

    public static ApprovalRequestDto from(ApprovalRequest e) {
        return ApprovalRequestDto.builder()
            .id(e.getExternalId())
            .entityType(e.getEntityType()).entityId(e.getEntityId())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .summaryEn(e.getSummaryEn()).summaryAr(e.getSummaryAr())
            .requestedBy(e.getRequestedBy()).requestedAt(e.getRequestedAt())
            .assignedTo(e.getAssignedTo()).originalAssignedTo(e.getOriginalAssignedTo())
            .priority(e.getPriority()).slaDeadline(e.getSlaDeadline())
            .status(e.getStatus())
            .decidedAt(e.getDecidedAt()).decidedBy(e.getDecidedBy())
            .decisionCommentEn(e.getDecisionCommentEn()).decisionCommentAr(e.getDecisionCommentAr())
            .delegatedFrom(e.getDelegatedFrom()).delegationReasonEn(e.getDelegationReasonEn())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED|DELEGATED|EXPIRED|PENDING")
        private String status;
        private String decidedBy;
        private String decisionCommentEn;
        private String decisionCommentAr;
        // For DELEGATED:
        private String newAssignedTo;
        private String delegationReasonEn;
    }
}
