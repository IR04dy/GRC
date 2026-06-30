package com.tss.grc.module.risk.dto;

import com.tss.grc.module.risk.entity.RiskAcceptance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** DTOs for the structured risk-acceptance workflow (ERM remediation epic E-A). */
public final class RiskAcceptanceDtos {

    private RiskAcceptanceDtos() {}

    public record CreateDto(
            @NotBlank String justificationEn,
            String justificationAr,
            String compensatingControlsEn,
            String compensatingControlsAr,
            Integer appetiteCeiling,
            @NotNull LocalDate expiresAt,
            String croSignedBy,
            String boardSignedBy
    ) {}

    /** Board sign-off can be supplied at approval time when residual is above appetite. */
    public record ApproveDto(String boardSignedBy) {}

    public record RevokeDto(@NotBlank String reason) {}

    public record Response(
            Long id, UUID externalId, Long riskId,
            String justificationEn, String justificationAr,
            String compensatingControlsEn, String compensatingControlsAr,
            Integer residualScoreAtAcceptance, Integer appetiteCeiling, Boolean requiresBoard,
            LocalDate expiresAt, String status,
            String croSignedBy, String boardSignedBy,
            String approvedBy, LocalDateTime approvedAt, String rejectionReason,
            LocalDateTime createdAt
    ) {
        public static Response from(RiskAcceptance a) {
            return new Response(
                a.getId(), a.getExternalId(), a.getRiskId(),
                a.getJustificationEn(), a.getJustificationAr(),
                a.getCompensatingControlsEn(), a.getCompensatingControlsAr(),
                a.getResidualScoreAtAcceptance(), a.getAppetiteCeiling(), a.getRequiresBoard(),
                a.getExpiresAt(), a.getStatus(),
                a.getCroSignedBy(), a.getBoardSignedBy(),
                a.getApprovedBy(), a.getApprovedAt(), a.getRejectionReason(),
                a.getCreatedAt());
        }
    }
}
