package com.tss.grc.module.scm.clauses.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.scm.clauses.entity.ContractClause;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ContractClauseDtos {
    private ContractClauseDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 64) String clauseCode,
            @NotBlank @Size(max = 255) String titleEn,
            String titleAr,
            @NotBlank @Size(max = 20) String category,
            String applicableTo,
            @NotBlank String bodyEn,
            String bodyAr,
            String guidanceEn, String guidanceAr,
            String regulatoryRefs,
            String riskLevel, String negotiability, String version,
            String owner, String tags
    ) {}

    public record UpdateRequest(
            String titleEn, String titleAr,
            String category, String applicableTo,
            String bodyEn, String bodyAr,
            String guidanceEn, String guidanceAr,
            String regulatoryRefs,
            String riskLevel, String negotiability, String version,
            String owner, String tags
    ) {}

    public record DeprecateRequest(@NotBlank @Size(max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            Long id, String clauseCode, String externalId,
            String titleEn, String titleAr,
            String category, String applicableTo,
            String bodyEn, String bodyAr,
            String guidanceEn, String guidanceAr,
            String regulatoryRefs,
            String riskLevel, String negotiability, String version,
            String status,
            LocalDateTime approvedAt, String approverName,
            LocalDateTime deprecatedAt, String deprecationReason,
            String owner, String tags,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Response from(ContractClause c) {
            return new Response(
                    c.getId(), c.getClauseCode(), c.getExternalId(),
                    c.getTitleEn(), c.getTitleAr(),
                    c.getCategory(), c.getApplicableTo(),
                    c.getBodyEn(), c.getBodyAr(),
                    c.getGuidanceEn(), c.getGuidanceAr(),
                    c.getRegulatoryRefs(),
                    c.getRiskLevel(), c.getNegotiability(), c.getVersion(),
                    c.getStatus(),
                    c.getApprovedAt(), c.getApproverName(),
                    c.getDeprecatedAt(), c.getDeprecationReason(),
                    c.getOwner(), c.getTags(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
