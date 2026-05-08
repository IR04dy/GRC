package com.tss.grc.module.risk_lc.dto;

import com.tss.grc.module.risk_lc.entity.TreatmentPlan;
import com.tss.grc.module.risk_lc.entity.TreatmentAction;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Wire format — mirrors the UI {@code TreatmentPlan} TS interface 1:1. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TreatmentPlanDto {
    private String id;          // external_id
    private Long riskId;
    @NotBlank private String strategy;
    @NotBlank private String rationaleEn;
    @NotBlank private String rationaleAr;
    private String status;
    @NotNull private BigDecimal costSarK;
    private String costBenefitEn;
    private String costBenefitAr;
    private List<TreatmentActionDto> actions;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectedReason;

    public static TreatmentPlanDto from(TreatmentPlan e) {
        return TreatmentPlanDto.builder()
            .id(e.getExternalId())
            .riskId(e.getRiskId())
            .strategy(e.getStrategy())
            .rationaleEn(e.getRationaleEn())
            .rationaleAr(e.getRationaleAr())
            .status(e.getStatus())
            .costSarK(e.getCostSarK())
            .costBenefitEn(e.getCostBenefitEn())
            .costBenefitAr(e.getCostBenefitAr())
            .actions(e.getActions() == null ? List.of() : e.getActions().stream().map(TreatmentActionDto::from).collect(Collectors.toList()))
            .createdAt(e.getCreatedAt())
            .submittedAt(e.getSubmittedAt())
            .approvedBy(e.getApprovedBy())
            .approvedAt(e.getApprovedAt())
            .rejectedReason(e.getRejectedReason())
            .build();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TreatmentActionDto {
        private String id;
        private String titleEn;
        private String titleAr;
        private String ownerRole;
        private LocalDate targetDate;
        private String status;
        private BigDecimal expectedRiskReduction;
        private LocalDateTime completedAt;
        private String evidenceRef;

        public static TreatmentActionDto from(TreatmentAction a) {
            return TreatmentActionDto.builder()
                .id(a.getExternalId())
                .titleEn(a.getTitleEn())
                .titleAr(a.getTitleAr())
                .ownerRole(a.getOwnerRole())
                .targetDate(a.getTargetDate())
                .status(a.getStatus())
                .expectedRiskReduction(a.getExpectedRiskReduction())
                .completedAt(a.getCompletedAt())
                .evidenceRef(a.getEvidenceRef())
                .build();
        }
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "DRAFT|SUBMITTED|APPROVED|IN_PROGRESS|COMPLETED|REJECTED")
        private String status;
        private String approvedBy;
        private String rejectedReason;
    }
}
