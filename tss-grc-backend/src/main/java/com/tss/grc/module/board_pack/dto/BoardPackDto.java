package com.tss.grc.module.board_pack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tss.grc.common.model.CommitteeTypes;
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
    /**
     * The committee this pack is for. ⚠ <b>The accepted set is a MIRROR of
     * {@code admin.committees.committee_type}</b>, and mirrors go stale — that is exactly how
     * {@code AttachmentAccessPolicy}'s {@code FRAUD_CASE} entry outlived the guard it copied. It is a
     * mirror rather than a lookup because validating against the tenant's live committees needs a
     * cross-module read, and ADR-012 wants that behind a port; building the port is the follow-up
     * slice that also lands ADR-017 D-11's read filter.
     *
     * <p>What it replaced was worse than stale: {@code BOARD|AUDIT_COMMITTEE|RISK_COMMITTEE|EXEC},
     * a four-value set against five stored values. One pack held {@code CYBER_COMMITTEE},
     * <b>which this API could not recreate</b>, and neither {@code CYBER_COMMITTEE} nor {@code EXEC}
     * matched any {@code committee_type} — so joining audience to committee resolved 3 of 5 and
     * silently mis-scoped the rest. V331 re-points those two onto the real vocabulary and throws if
     * any stored audience fails to resolve.
     *
     * <p>⚠ <b>W1-5 correction.</b> V331 widened this to "the real vocabulary" and landed
     * <b>17 of 22</b>: {@code OTHER}, {@code ARCH_REVIEW}, {@code INVESTMENT}, {@code ALCO} and
     * {@code AML} were missing, and all five have been in {@code CK_com_type} since {@code V154} —
     * 177 migrations earlier. So a tenant with an ALCO or an Investment committee could not scope a
     * pack to it, and the refusal said the value "must be an {@code admin.committees.committee_type}
     * value" while being exactly that. The guard D-11 relied on — {@code V331} throwing when a
     * <em>stored</em> audience fails to resolve — points the other way and could never see this.
     * The vocabulary now lives in {@link CommitteeTypes}, with a ratchet tying every Java copy
     * together and an integration-test assertion tying it to {@code CK_com_type} itself. Sharing the
     * constant is not the cross-module read ADR-012 governs; {@code common.model} is kernel, and a
     * live per-tenant committee lookup is still the follow-up slice.
     */
    @NotBlank
    @Pattern(regexp = CommitteeTypes.REGEX,
        message = "audience " + CommitteeTypes.MESSAGE)
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
    /**
     * F2-22b(vi) missed this one. That slice stamped {@code reviewedBy} and {@code approvedBy}
     * from the principal — and the note explaining why sits in {@code TransitionRequest} just
     * below — but {@code draftedBy} stayed {@code @NotBlank} on the CREATE path, so the drafter
     * of the board's governance artefact was whatever the caller typed. Live-proven forgeable:
     * a pack was created with {@code draftedBy="IMPERSONATED-admin"} and stored it verbatim.
     * The field is real and in use (4 of 16 packs name the board secretary), which is what made
     * the forgery worth something.
     *
     * <p>{@code READ_ONLY}, not {@code @JsonIgnore} — the same choice {@code BaseEntity} makes
     * for F2-22a: Jackson must keep serialising the drafter OUT (every list row shows it) while
     * refusing to bind it IN.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String draftedBy;
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
    private String cancellationReasonAr;

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
            .cancelledAt(e.getCancelledAt()).cancellationReasonEn(e.getCancellationReasonEn()).cancellationReasonAr(e.getCancellationReasonAr())
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
        private String cancellationReasonAr;
    }
}
