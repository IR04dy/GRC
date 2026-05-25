package com.tss.grc.module.ethics.coi.dto;

import com.tss.grc.module.ethics.coi.entity.CoiDeclaration;
import com.tss.grc.module.ethics.coi.entity.CoiDeclarationCampaign;
import com.tss.grc.module.ethics.coi.entity.CoiDisclosedItem;
import com.tss.grc.module.ethics.coi.entity.CoiRecusalEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO bundle for the Conflict-of-Interest (COI) module — Sprint 13.
 *
 * <p>Three input shapes (campaign create, declaration submit,
 * declaration review, recusal record) plus matching response
 * envelopes. Bilingual fields are required wherever the entity
 * column is {@code NOT NULL}.
 */
public final class CoiDtos {

    private CoiDtos() {}

    // ─── Requests ───────────────────────────────────────────────────

    public record CreateCampaignRequest(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 256) String titleEn,
            @NotBlank @Size(max = 256) String titleAr,
            @NotNull Integer fiscalYear,
            @NotNull LocalDate opensOn,
            @NotNull LocalDate closesOn,
            String scope,
            Integer targetUserCount
    ) {}

    public record DisclosedItemDto(
            @NotBlank String itemType,
            @NotBlank @Size(max = 256) String counterpartyEn,
            @NotBlank @Size(max = 256) String counterpartyAr,
            String relationshipDescriptionEn,
            String relationshipDescriptionAr,
            BigDecimal financialValueSar,
            LocalDate startDate,
            LocalDate endDate,
            Boolean isActive,
            String mitigationActionEn,
            String mitigationActionAr
    ) {}

    public record SubmitDeclarationRequest(
            @NotNull Long campaignId,
            Long userId,
            @Size(max = 64) String employeeNo,
            @NotBlank @Size(max = 256) String fullNameEn,
            @NotBlank @Size(max = 256) String fullNameAr,
            String positionTitleEn,
            String positionTitleAr,
            String departmentEn,
            String departmentAr,
            Boolean hasConflict,
            Boolean hasPotentialConflict,
            @NotBlank String attestationText,
            @Valid List<DisclosedItemDto> disclosedItems
    ) {}

    public record ReviewDeclarationRequest(
            @NotBlank String reviewOutcome,    // CLEARED / MITIGATION_REQUIRED / RECUSAL_REQUIRED / ESCALATED
            String reviewNotesEn,
            String reviewNotesAr
    ) {}

    public record RecordRecusalRequest(
            Long declarationId,
            @NotNull Long userId,
            @NotBlank @Size(max = 128) String meetingReference,
            @Size(max = 32) String committeeCode,
            LocalDateTime recusalDate,
            @NotBlank String recusalReasonEn,
            @NotBlank String recusalReasonAr,
            String agendaItems,
            Boolean recordedInMinutes
    ) {}

    // ─── Responses ──────────────────────────────────────────────────

    public record CampaignResponse(
            Long id,
            Long organisationId,
            String code,
            String titleEn,
            String titleAr,
            Integer fiscalYear,
            LocalDate opensOn,
            LocalDate closesOn,
            String status,
            String scope,
            Integer targetUserCount,
            Integer declaredUserCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static CampaignResponse from(CoiDeclarationCampaign c) {
            return new CampaignResponse(
                    c.getId(), c.getOrganisationId(), c.getCode(),
                    c.getTitleEn(), c.getTitleAr(), c.getFiscalYear(),
                    c.getOpensOn(), c.getClosesOn(),
                    c.getStatus(), c.getScope(),
                    c.getTargetUserCount(), c.getDeclaredUserCount(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record DisclosedItemResponse(
            Long id,
            Long declarationId,
            String itemType,
            String counterpartyEn,
            String counterpartyAr,
            String relationshipDescriptionEn,
            String relationshipDescriptionAr,
            BigDecimal financialValueSar,
            LocalDate startDate,
            LocalDate endDate,
            Boolean isActive,
            String mitigationActionEn,
            String mitigationActionAr,
            LocalDateTime createdAt
    ) {
        public static DisclosedItemResponse from(CoiDisclosedItem i) {
            return new DisclosedItemResponse(
                    i.getId(), i.getDeclarationId(), i.getItemType(),
                    i.getCounterpartyEn(), i.getCounterpartyAr(),
                    i.getRelationshipDescriptionEn(), i.getRelationshipDescriptionAr(),
                    i.getFinancialValueSar(), i.getStartDate(), i.getEndDate(),
                    i.getIsActive(),
                    i.getMitigationActionEn(), i.getMitigationActionAr(),
                    i.getCreatedAt());
        }
    }

    public record DeclarationResponse(
            Long id,
            Long organisationId,
            Long campaignId,
            Long userId,
            String employeeNo,
            String fullNameEn,
            String fullNameAr,
            String positionTitleEn,
            String positionTitleAr,
            String departmentEn,
            String departmentAr,
            LocalDateTime declarationDate,
            Boolean hasConflict,
            Boolean hasPotentialConflict,
            String attestationText,
            LocalDateTime submittedOn,
            Long reviewedBy,
            LocalDateTime reviewedOn,
            String reviewOutcome,
            String reviewNotesEn,
            String reviewNotesAr,
            List<DisclosedItemResponse> disclosedItems,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static DeclarationResponse from(CoiDeclaration d, List<CoiDisclosedItem> items) {
            return new DeclarationResponse(
                    d.getId(), d.getOrganisationId(), d.getCampaignId(),
                    d.getUserId(), d.getEmployeeNo(),
                    d.getFullNameEn(), d.getFullNameAr(),
                    d.getPositionTitleEn(), d.getPositionTitleAr(),
                    d.getDepartmentEn(), d.getDepartmentAr(),
                    d.getDeclarationDate(),
                    d.getHasConflict(), d.getHasPotentialConflict(),
                    d.getAttestationText(),
                    d.getSubmittedOn(),
                    d.getReviewedBy(), d.getReviewedOn(),
                    d.getReviewOutcome(),
                    d.getReviewNotesEn(), d.getReviewNotesAr(),
                    items == null
                            ? List.of()
                            : items.stream().map(DisclosedItemResponse::from).toList(),
                    d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    public record RecusalEventResponse(
            Long id,
            Long organisationId,
            Long declarationId,
            Long userId,
            String meetingReference,
            String committeeCode,
            LocalDateTime recusalDate,
            String recusalReasonEn,
            String recusalReasonAr,
            String agendaItems,
            Boolean recordedInMinutes,
            LocalDateTime createdAt
    ) {
        public static RecusalEventResponse from(CoiRecusalEvent r) {
            return new RecusalEventResponse(
                    r.getId(), r.getOrganisationId(), r.getDeclarationId(),
                    r.getUserId(),
                    r.getMeetingReference(), r.getCommitteeCode(),
                    r.getRecusalDate(),
                    r.getRecusalReasonEn(), r.getRecusalReasonAr(),
                    r.getAgendaItems(), r.getRecordedInMinutes(),
                    r.getCreatedAt());
        }
    }
}
