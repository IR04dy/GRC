package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * S8e / CE-2 — a change model being authored or edited.
 *
 * <p><b>Note what is absent, because it is the whole design.</b> No {@code status}, no
 * {@code preApproved}, and none of the four {@code authorised*} fields. A caller cannot post a model
 * that already confers pre-approval: authoring produces a {@code DRAFT}, and the pre-approval is a
 * <b>separate act</b> by a different person through {@code POST /models/{id}/approve}, which stamps
 * the authority and the expiry itself.
 *
 * <p>If those fields were on this record, {@code ck_cmodel_preapproval} would be the only thing
 * standing between a {@code CHANGE:WRITE} holder and a self-issued pre-approval — and it would let
 * it through, because the constraint checks that the four fields <em>agree</em>, not who set them.
 * The DTO is what makes the constraint sufficient.
 *
 * <p>⚠ {@code MassAssignmentRatchetTest} would reject binding {@link
 * com.tss.grc.module.change_mgmt.entity.ChangeModel} directly, by name and with the reason: a scrub
 * is a list of fields somebody remembered; a DTO is the set of fields that exist. S7e learned this
 * the same way.
 *
 * <p>Ordering landmine: {@code @Valid} runs <b>before</b> {@code @PreAuthorize}, so a permission
 * probe against these endpoints needs a body that satisfies these constraints.
 */
public record ChangeModelRequest(

        /** Catalogue key, unique per tenant among live rows. */
        @NotBlank @Size(max = 64) String code,

        @NotBlank @Size(max = 300) String nameEn,

        /** House rule 7 — Arabic is backend-mandatory, and {@code name_ar} is NOT NULL in V354. */
        @NotBlank @Size(max = 300) String nameAr,

        String descriptionEn,
        String descriptionAr,

        /** {@code STANDARD} | {@code NORMAL} | {@code EMERGENCY}; validated against V354's CHECK. */
        @NotBlank @Size(max = 24) String changeType,

        /** {@code LOW} | {@code MEDIUM} | {@code HIGH} | {@code CRITICAL}, or null. */
        @Size(max = 16) String riskDefault,

        /** {@code AUTO} | {@code CAB} | {@code ECAB}, or null to leave the matrix in charge. */
        @Size(max = 8) String gateOverride,

        /** The procedure itself: steps, back-out and test templates. Prose on purpose. */
        String standardSteps,
        String rollbackTemplate,
        String testTemplate,

        /** Lead time this model expects. Must be positive if given — 0 hours is not a lead time. */
        @Positive Integer maxLeadTimeHours,

        @Size(max = 32) String reviewCadence
) {}
