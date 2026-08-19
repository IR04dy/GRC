package com.tss.grc.module.ethics.coi.dto;

import com.tss.grc.module.ethics.coi.entity.CoiDeclaration;
import com.tss.grc.module.ethics.coi.entity.CoiDeclarationCampaign;
import com.tss.grc.module.ethics.coi.entity.CoiDisclosedItem;
import com.tss.grc.module.ethics.coi.entity.CoiRecusalEvent;
import com.tss.grc.module.iam.entity.User;
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

    /**
     * F2-244 — {@code userNameEn} / {@code userNameAr} / {@code userResolved} are new, and the
     * first two were <b>already declared by the frontend</b>.
     *
     * <p>{@code lib/coi.ts}'s {@code RecusalResponse} has carried {@code userNameEn?} and
     * {@code userNameAr?} all along and this record declared neither, so
     * {@code CoiRegisterPage}'s detail panel renders the person's name behind
     * {@code selected.userNameEn && …} — a field the wire has never carried. F2-211's shape: the
     * client nominates, the entity adjudicates, and {@code CoiRecusalEvent} has <b>no name column
     * at all</b>. The consequence was that the SAMA Article 30 recusal register never named the
     * person who recused — not in the detail panel, and not in the table, which has no person
     * column either.
     *
     * <p>🔑 <b>{@code userResolved} exists because two different facts were previously
     * indistinguishable</b>: a user whose {@code full_name_en} happens to be blank, and a
     * {@code user_id} that resolves to nobody. 11 of the 28 live COI rows in dev and prod are the
     * second kind, so rendering a blank name would let the register's worst rows read as its
     * tidiest (F2-197: an invented figure invites a question, an invented all-clear closes one).
     * The server owns this fact — a client cannot compute it — and the three states are F2-209's:
     * a value, a resolvable user with no name recorded, and no such user.
     */
    public record RecusalEventResponse(
            Long id,
            Long organisationId,
            Long declarationId,
            Long userId,
            Boolean userResolved,
            String userNameEn,
            String userNameAr,
            String meetingReference,
            String committeeCode,
            LocalDateTime recusalDate,
            String recusalReasonEn,
            String recusalReasonAr,
            String agendaItems,
            Boolean recordedInMinutes,
            LocalDateTime createdAt
    ) {
        /**
         * @param subject the user {@code r.getUserId()} resolves to <em>inside the row's own
         *                tenant</em>, or {@code null} when it resolves to nobody. Never resolve it
         *                here — a DTO has no repository and no tenant, and guessing either is how
         *                a cross-tenant read is built by accident.
         */
        public static RecusalEventResponse from(CoiRecusalEvent r, User subject) {
            return new RecusalEventResponse(
                    r.getId(), r.getOrganisationId(), r.getDeclarationId(),
                    r.getUserId(),
                    subject != null,
                    subject == null ? null : subject.getFullNameEn(),
                    subject == null ? null : subject.getFullNameAr(),
                    r.getMeetingReference(), r.getCommitteeCode(),
                    r.getRecusalDate(),
                    r.getRecusalReasonEn(), r.getRecusalReasonAr(),
                    r.getAgendaItems(), r.getRecordedInMinutes(),
                    r.getCreatedAt());
        }
    }
}
