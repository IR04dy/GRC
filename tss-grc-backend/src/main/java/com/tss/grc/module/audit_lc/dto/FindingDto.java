package com.tss.grc.module.audit_lc.dto;

import com.tss.grc.module.audit_lc.entity.Finding;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FindingDto {
    private String id;                  // external_id — used in URLs + API paths
    private Long   numericId;           // internal PK — used by audit-log + automation lookups
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
            .id(e.getExternalId()).numericId(e.getId())
            .engagementCode(e.getEngagementCode()).refNumber(e.getRefNumber())
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
        /**
         * ⚠⚠ F2-441 — this was the ONE of the four management-response fields the request did not
         * declare, while its three siblings sat on the lines directly above it and
         * {@link FindingDto#mgmtAccepts} returned it on the READ wire. So a management response
         * could be recorded — the text, in both languages, and the author — and <b>whether
         * management accepts the finding could not be recorded at all</b>, on the IIA Standard 2500
         * step whose entire purpose is that verdict.
         *
         * <p>🔑 F2-215's two-of-N at 3 of 4, in one adjacent block: a reader who finds three
         * management-response fields on the request has no reason to ask about a fourth.
         *
         * <p>⚠ {@code Boolean}, not {@code boolean}, and that is load-bearing: <b>null means "no
         * answer recorded"</b> and is a third state, not a synonym for "disagreed". The frontend
         * badge used to collapse them ({@code f.mgmtAccepts ? success : warning}) so a finding with
         * no recorded answer displayed as one management had REJECTED — F2-209's three-states rule,
         * and F2-197's invented all-clear inverted into an invented dispute.
         */
        private Boolean mgmtAccepts;
    }
}
