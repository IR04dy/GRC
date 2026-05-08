package com.tss.grc.module.audit_lc.dto;

import com.tss.grc.module.audit_lc.entity.Finding;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FindingDto {
    private String id;                  // external_id
    @NotBlank private String engagementCode;
    private String refNumber;
    @NotBlank private String titleEn;
    @NotBlank private String titleAr;
    @NotBlank @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW|OBSERVATION")
    private String severity;
    private String status;
    private String controlCrosswalk;    // JSON
    private String conditionEn;
    private String conditionAr;
    private String criteriaEn;
    private String criteriaAr;
    private String causeEn;
    private String causeAr;
    private String recommendationEn;
    private String recommendationAr;
    private String mgmtResponseEn;
    private String mgmtResponseAr;
    private String mgmtResponseAuthor;
    private Boolean mgmtAccepts;
    private LocalDate targetDate;
    private String ownerRole;
    private LocalDateTime raisedAt;
    private String capaIds;

    public static FindingDto from(Finding e) {
        return FindingDto.builder()
            .id(e.getExternalId()).engagementCode(e.getEngagementCode()).refNumber(e.getRefNumber())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .severity(e.getSeverity()).status(e.getStatus())
            .controlCrosswalk(e.getControlCrosswalk())
            .conditionEn(e.getConditionEn()).conditionAr(e.getConditionAr())
            .criteriaEn(e.getCriteriaEn()).criteriaAr(e.getCriteriaAr())
            .causeEn(e.getCauseEn()).causeAr(e.getCauseAr())
            .recommendationEn(e.getRecommendationEn()).recommendationAr(e.getRecommendationAr())
            .mgmtResponseEn(e.getMgmtResponseEn()).mgmtResponseAr(e.getMgmtResponseAr())
            .mgmtResponseAuthor(e.getMgmtResponseAuthor()).mgmtAccepts(e.getMgmtAccepts())
            .targetDate(e.getTargetDate()).ownerRole(e.getOwnerRole())
            .raisedAt(e.getRaisedAt()).capaIds(e.getCapaIds())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "OPEN|MGMT_RESPONSE|IN_REMEDIATION|AWAITING_VERIFICATION|CLOSED")
        private String status;
        private String mgmtResponseEn;
        private String mgmtResponseAr;
        private String mgmtResponseAuthor;
    }
}
