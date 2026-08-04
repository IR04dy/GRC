package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * S7e / CE-5 — a condition being attached to a change.
 *
 * <p><b>Note what is absent.</b> No {@code id}, no {@code organisationId}, no {@code status}, and
 * none of the four closing fields. The imposer is the authenticated principal, the tenant comes from
 * the change, the status is always {@code OPEN}, and the only route to a closed state is
 * satisfy/waive — which stamp their own author.
 *
 * <p>⚠ <b>Why this record exists at all.</b> The first draft bound {@code ChangeCondition} straight
 * from the request body and scrubbed the dangerous fields in the service.
 * {@code MassAssignmentRatchetTest} rejected it, correctly and by name: <i>"take a request DTO
 * carrying only the fields a caller may set — do not raise this baseline. This class has produced
 * four separate authority bypasses by hand (DF-3, DF-4, F2-25, the roster link)."</i> A scrub is a
 * list of fields somebody remembered; a DTO is the set of fields that exist. The service-side guard
 * stays as well, because {@code addCondition} is reachable from inside the module too — but it is no
 * longer the only thing standing between a caller and {@code status = 'SATISFIED'}.
 *
 * <p>Ordering landmine: {@code @Valid} runs <b>before</b> {@code @PreAuthorize}, so an authorisation
 * probe against this endpoint still needs a body that satisfies these constraints.
 */
public record ChangeConditionRequest(

        /** What must be done before implementation may start. */
        @NotBlank @Size(max = 1000) String textEn,

        /** House rule 7 — Arabic is backend-mandatory, and {@code text_ar} is NOT NULL in V352. */
        @NotBlank @Size(max = 1000) String textAr,

        /** Who must discharge it. Optional: a CAB can impose a condition without naming an owner,
         *  and refusing the record until it has one would lose the governance act to keep the
         *  metadata tidy. */
        @Size(max = 255) String ownerName,

        /** Optional deadline. The register sorts undated obligations last, so an unowned, undated
         *  condition still blocks implementation — it just never reads as overdue. */
        LocalDateTime dueAt,

        /** Display order within the change's conditions; defaults to 0. */
        Integer sortOrder
) {}
