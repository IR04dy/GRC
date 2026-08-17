package com.tss.grc.module.nca_ecc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tss.grc.module.nca_ecc.entity.NcaEccFiling;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NcaEccFilingDto {
    private String id;
    @NotNull @Min(2024) @Max(2100)
    private Integer fiscalYear;
    private String frameworkVersion;
    private String titleEn;
    private String titleAr;
    @Min(1) @Max(5) private Integer domain1Score;
    @Min(1) @Max(5) private Integer domain2Score;
    @Min(1) @Max(5) private Integer domain3Score;
    @Min(1) @Max(5) private Integer domain4Score;
    @Min(1) @Max(5) private Integer domain5Score;
    private BigDecimal overallScore;
    private String responsesJson;
    private String gapSummaryEn;
    private String gapSummaryAr;
    private Integer capaCount;
    private String status;
    /**
     * ⚠ F2-225: server-owned. This class is BOTH the create request body and the read
     * response, so the field stays declared — {@code READ_ONLY} is exactly the right
     * instrument (F2-215's {@code ServerOwnedFieldsModule} uses the same one): the
     * response still carries the drafter, and a create can no longer name one.
     *
     * <p>The {@code @NotBlank} that used to sit here is gone deliberately rather than
     * kept as decoration — a constraint on a field the caller may no longer send would
     * 400 every request, and one the server ignores is worse than none (F2-184).
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String draftedBy;
    private LocalDateTime draftedAt;
    private LocalDateTime peerReviewStartedAt;
    private String peerReviewer;
    private LocalDateTime peerReviewCompletedAt;
    private String peerReviewCommentsEn;
    private LocalDateTime cisoApprovalAt;
    private String cisoApprover;
    private LocalDateTime submittedAt;
    private String submissionReference;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReasonEn;
    private String linkedCapaIds;
    private String linkedEvidence;

    public static NcaEccFilingDto from(NcaEccFiling e) {
        return NcaEccFilingDto.builder()
            .id(e.getExternalId())
            .fiscalYear(e.getFiscalYear()).frameworkVersion(e.getFrameworkVersion())
            .titleEn(e.getTitleEn()).titleAr(e.getTitleAr())
            .domain1Score(e.getDomain1Score()).domain2Score(e.getDomain2Score())
            .domain3Score(e.getDomain3Score()).domain4Score(e.getDomain4Score())
            .domain5Score(e.getDomain5Score()).overallScore(e.getOverallScore())
            .responsesJson(e.getResponsesJson())
            .gapSummaryEn(e.getGapSummaryEn()).gapSummaryAr(e.getGapSummaryAr())
            .capaCount(e.getCapaCount())
            .status(e.getStatus())
            .draftedBy(e.getDraftedBy()).draftedAt(e.getDraftedAt())
            .peerReviewStartedAt(e.getPeerReviewStartedAt())
            .peerReviewer(e.getPeerReviewer())
            .peerReviewCompletedAt(e.getPeerReviewCompletedAt())
            .peerReviewCommentsEn(e.getPeerReviewCommentsEn())
            .cisoApprovalAt(e.getCisoApprovalAt()).cisoApprover(e.getCisoApprover())
            .submittedAt(e.getSubmittedAt()).submissionReference(e.getSubmissionReference())
            .acknowledgedAt(e.getAcknowledgedAt())
            .cancelledAt(e.getCancelledAt()).cancellationReasonEn(e.getCancellationReasonEn())
            .linkedCapaIds(e.getLinkedCapaIds()).linkedEvidence(e.getLinkedEvidence())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "DRAFT|PEER_REVIEW|CISO_APPROVAL|SUBMITTED|ACKNOWLEDGED|CANCELLED")
        private String status;
        /**
         * The peer reviewer is NOMINATED on entry to PEER_REVIEW, so this one is
         * legitimately the caller's: it names who is being asked to review, not who has
         * reviewed. {@code cisoApprover} was removed from this record by F2-225 because
         * it is the opposite — the CISO_APPROVAL branch stamps {@code cisoApprovalAt}
         * from the server's clock, so the approval is happening on this call and its
         * performer is the caller, not a name the caller types.
         */
        private String peerReviewer;
        private String peerReviewCommentsEn;
        private String submissionReference;
        private String cancellationReasonEn;
        private String linkedCapaIds;
        private String linkedEvidence;
    }
}
