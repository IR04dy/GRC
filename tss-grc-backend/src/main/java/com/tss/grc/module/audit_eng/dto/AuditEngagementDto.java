package com.tss.grc.module.audit_eng.dto;

import com.tss.grc.module.audit_eng.entity.AuditEngagement;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEngagementDto {
    private String id;
    private String engagementCode;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    private String objectiveEn;
    private String objectiveAr;
    private String scopeEn;
    private String scopeAr;
    @NotBlank @Pattern(regexp = "INTERNAL|EXTERNAL|REGULATORY|CO_SOURCED")
    private String auditType;
    private String framework;
    private String auditorFirm;
    @NotBlank private String leadAuditor;
    private String auditTeam;
    private String auditCommitteeChair;
    private String status;
    private LocalDateTime planningStartedAt;
    private LocalDateTime fieldworkStartedAt;
    private LocalDateTime fieldworkEndedAt;
    private LocalDateTime reportingStartedAt;
    private LocalDateTime reportIssuedAt;
    private LocalDateTime followUpStartedAt;
    private LocalDateTime closedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReasonEn;
    private Integer budgetHours;
    private Integer actualHours;
    private Integer findingsCount;
    private String linkedFindingIds;
    private String annualPlanId;

    public static AuditEngagementDto from(AuditEngagement e) {
        return AuditEngagementDto.builder()
            .id(e.getExternalId())
            .engagementCode(e.getEngagementCode())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .objectiveEn(e.getObjectiveEn()).objectiveAr(e.getObjectiveAr())
            .scopeEn(e.getScopeEn()).scopeAr(e.getScopeAr())
            .auditType(e.getAuditType()).framework(e.getFramework())
            .auditorFirm(e.getAuditorFirm()).leadAuditor(e.getLeadAuditor())
            .auditTeam(e.getAuditTeam()).auditCommitteeChair(e.getAuditCommitteeChair())
            .status(e.getStatus())
            .planningStartedAt(e.getPlanningStartedAt())
            .fieldworkStartedAt(e.getFieldworkStartedAt())
            .fieldworkEndedAt(e.getFieldworkEndedAt())
            .reportingStartedAt(e.getReportingStartedAt())
            .reportIssuedAt(e.getReportIssuedAt())
            .followUpStartedAt(e.getFollowUpStartedAt())
            .closedAt(e.getClosedAt())
            .cancelledAt(e.getCancelledAt()).cancellationReasonEn(e.getCancellationReasonEn())
            .budgetHours(e.getBudgetHours()).actualHours(e.getActualHours())
            .findingsCount(e.getFindingsCount())
            .linkedFindingIds(e.getLinkedFindingIds())
            .annualPlanId(e.getAnnualPlanId())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "PLANNING|FIELDWORK|REPORTING|FOLLOW_UP|CLOSED|CANCELLED")
        private String status;
        private String cancellationReasonEn;
        private Integer actualHours;
        private String linkedFindingIds;
    }
}
