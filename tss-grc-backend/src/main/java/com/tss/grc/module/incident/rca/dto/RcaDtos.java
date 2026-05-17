package com.tss.grc.module.incident.rca.dto;

import com.tss.grc.module.incident.rca.entity.RootCauseAnalysis;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class RcaDtos {

    private RcaDtos() { /* static holder */ }

    public record Response(
            Long id,
            String relatedEntityType, Long relatedEntityId, String relatedEntityLabel,
            String methodology,
            String problemStatementEn, String problemStatementAr,
            String fiveWhysJson, String contributingFactorsJson,
            String rootCauseEn, String rootCauseAr,
            String recommendationsEn, String recommendationsAr,
            String facilitator, String participants, LocalDateTime conductedAt,
            String capaRefs,
            String status,
            LocalDateTime submittedAt, Long submittedBy,
            LocalDateTime approvedAt, Long approvedBy, String approverName,
            String rejectionReason,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Response from(RootCauseAnalysis r) {
            return new Response(
                    r.getId(),
                    r.getRelatedEntityType(), r.getRelatedEntityId(), r.getRelatedEntityLabel(),
                    r.getMethodology(),
                    r.getProblemStatementEn(), r.getProblemStatementAr(),
                    r.getFiveWhysJson(), r.getContributingFactorsJson(),
                    r.getRootCauseEn(), r.getRootCauseAr(),
                    r.getRecommendationsEn(), r.getRecommendationsAr(),
                    r.getFacilitator(), r.getParticipants(), r.getConductedAt(),
                    r.getCapaRefs(),
                    r.getStatus(),
                    r.getSubmittedAt(), r.getSubmittedBy(),
                    r.getApprovedAt(), r.getApprovedBy(), r.getApproverName(),
                    r.getRejectionReason(),
                    r.getCreatedAt(), r.getUpdatedAt()
            );
        }
    }

    public record CreateRequest(
            @NotBlank @Size(max = 50) String relatedEntityType,
            @NotNull Long relatedEntityId,
            @Size(max = 255) String relatedEntityLabel,
            @Size(max = 20) String methodology,
            @NotBlank String problemStatementEn,
            String problemStatementAr,
            String fiveWhysJson,
            String contributingFactorsJson,
            String rootCauseEn,
            String rootCauseAr,
            String recommendationsEn,
            String recommendationsAr,
            @Size(max = 255) String facilitator,
            String participants,
            LocalDateTime conductedAt,
            String capaRefs
    ) { }

    public record UpdateRequest(
            @Size(max = 20) String methodology,
            String problemStatementEn,
            String problemStatementAr,
            String fiveWhysJson,
            String contributingFactorsJson,
            String rootCauseEn,
            String rootCauseAr,
            String recommendationsEn,
            String recommendationsAr,
            @Size(max = 255) String facilitator,
            String participants,
            LocalDateTime conductedAt,
            String capaRefs
    ) { }

    public record RejectRequest(@NotBlank @Size(max = 500) String rejectionReason) { }
}
