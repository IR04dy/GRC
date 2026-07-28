package com.tss.grc.module.board_pack.dto;

import com.tss.grc.module.board_pack.entity.BoardPack;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BoardPackDto {
    private String id;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    @NotBlank private String periodLabel;
    @NotNull private LocalDate periodStart;
    @NotNull private LocalDate periodEnd;
    @NotBlank @Pattern(regexp = "BOARD|AUDIT_COMMITTEE|RISK_COMMITTEE|EXEC")
    private String audience;
    private String riskSummaryJson;
    private String appetiteBreachesJson;
    private String auditEngagementsJson;
    private String incidentsJson;
    private String pdplSummaryJson;
    private String ccmSummaryJson;
    private String slaSummaryJson;
    private String customSectionsJson;
    private String status;
    @NotBlank private String draftedBy;
    private LocalDateTime draftedAt;
    private LocalDateTime submittedForReviewAt;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private String reviewCommentsEn;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private LocalDateTime publishedAt;
    private String publicationUrl;
    private LocalDateTime cancelledAt;
    private String cancellationReasonEn;

    public static BoardPackDto from(BoardPack e) {
        return BoardPackDto.builder()
            .id(e.getExternalId())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .periodLabel(e.getPeriodLabel()).periodStart(e.getPeriodStart()).periodEnd(e.getPeriodEnd())
            .audience(e.getAudience())
            .riskSummaryJson(e.getRiskSummaryJson())
            .appetiteBreachesJson(e.getAppetiteBreachesJson())
            .auditEngagementsJson(e.getAuditEngagementsJson())
            .incidentsJson(e.getIncidentsJson())
            .pdplSummaryJson(e.getPdplSummaryJson())
            .ccmSummaryJson(e.getCcmSummaryJson())
            .slaSummaryJson(e.getSlaSummaryJson())
            .customSectionsJson(e.getCustomSectionsJson())
            .status(e.getStatus())
            .draftedBy(e.getDraftedBy()).draftedAt(e.getDraftedAt())
            .submittedForReviewAt(e.getSubmittedForReviewAt())
            .reviewedAt(e.getReviewedAt()).reviewedBy(e.getReviewedBy())
            .reviewCommentsEn(e.getReviewCommentsEn())
            .approvedAt(e.getApprovedAt()).approvedBy(e.getApprovedBy())
            .publishedAt(e.getPublishedAt()).publicationUrl(e.getPublicationUrl())
            .cancelledAt(e.getCancelledAt()).cancellationReasonEn(e.getCancellationReasonEn())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "DRAFT|IN_REVIEW|APPROVED|PUBLISHED|CANCELLED")
        private String status;
        // F2-22b(vi): reviewedBy / approvedBy removed — stamped from the principal.
        private String reviewCommentsEn;
        private String publicationUrl;
        private String cancellationReasonEn;
    }
}
