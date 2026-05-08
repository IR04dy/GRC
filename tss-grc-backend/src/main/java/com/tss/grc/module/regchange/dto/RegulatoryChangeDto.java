package com.tss.grc.module.regchange.dto;

import com.tss.grc.module.regchange.entity.RegulatoryChange;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegulatoryChangeDto {
    private String id;
    @NotBlank @Pattern(regexp = "NCA|SAMA|CMA|SDAIA|CITC|ZATCA|MoH|HRSD|MoC|NAZAHA|OTHER")
    private String regulator;
    private String citation;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    private String summaryEn;
    private String summaryAr;
    private String sourceUrl;
    private LocalDate publishedAt;
    private LocalDate effectiveAt;
    private String status;
    private LocalDateTime assessedAt;
    private String assessedBy;
    private String applicability;
    private Integer impactScore;
    private String impactSummaryEn;
    private String impactSummaryAr;
    private String implementationOwner;
    private LocalDateTime implementationStartedAt;
    private LocalDate implementationTargetDate;
    private LocalDateTime implementationCompletedAt;
    private String linkedControls;
    private String linkedCapaIds;
    private String linkedEvidence;
    private String deprioritisationReasonEn;
    private LocalDateTime closedAt;

    public static RegulatoryChangeDto from(RegulatoryChange e) {
        return RegulatoryChangeDto.builder()
            .id(e.getExternalId())
            .regulator(e.getRegulator()).citation(e.getCitation())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .summaryEn(e.getSummaryEn()).summaryAr(e.getSummaryAr())
            .sourceUrl(e.getSourceUrl())
            .publishedAt(e.getPublishedAt()).effectiveAt(e.getEffectiveAt())
            .status(e.getStatus())
            .assessedAt(e.getAssessedAt()).assessedBy(e.getAssessedBy())
            .applicability(e.getApplicability()).impactScore(e.getImpactScore())
            .impactSummaryEn(e.getImpactSummaryEn()).impactSummaryAr(e.getImpactSummaryAr())
            .implementationOwner(e.getImplementationOwner())
            .implementationStartedAt(e.getImplementationStartedAt())
            .implementationTargetDate(e.getImplementationTargetDate())
            .implementationCompletedAt(e.getImplementationCompletedAt())
            .linkedControls(e.getLinkedControls())
            .linkedCapaIds(e.getLinkedCapaIds())
            .linkedEvidence(e.getLinkedEvidence())
            .deprioritisationReasonEn(e.getDeprioritisationReasonEn())
            .closedAt(e.getClosedAt())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "NEW|ASSESSED|IMPACT_ANALYSED|IN_IMPLEMENTATION|IMPLEMENTED|CLOSED|DEPRIORITISED")
        private String status;
        private String assessedBy;
        private String applicability;
        private Integer impactScore;
        private String impactSummaryEn;
        private String impactSummaryAr;
        private String implementationOwner;
        private LocalDate implementationTargetDate;
        private String linkedControls;
        private String linkedCapaIds;
        private String linkedEvidence;
        private String deprioritisationReasonEn;
    }
}
