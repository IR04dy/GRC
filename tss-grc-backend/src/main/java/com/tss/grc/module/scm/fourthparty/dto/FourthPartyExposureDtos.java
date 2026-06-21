package com.tss.grc.module.scm.fourthparty.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Request / response DTOs for the fourth-party-exposure REST surface.
 *
 * <p>Created for SEC-FIN-004: the controller previously bound the raw JPA
 * {@code @Entity} as the request body and persisted it, letting a caller set
 * server-controlled fields ({@code id}, {@code organisationId}, {@code status},
 * {@code concentrationRisk}, {@code isDeleted}, {@code isActive}, audit columns,
 * {@code validatorName}/{@code lastValidatedBy}, {@code rowVersion}). These DTOs
 * expose only the fields a client may legitimately supply; everything else is
 * stamped server-side by the service.
 */
public final class FourthPartyExposureDtos {

    private FourthPartyExposureDtos() {}

    /** Client-settable fields on create. The service forces status=DISCOVERED,
     *  concentrationRisk=LOW, organisationId from the tenant context, and clears
     *  all audit / validation / soft-delete state. */
    public record CreateRequest(
            Long primaryVendorId,
            @NotBlank @Size(max = 255) String primaryVendorName,
            @NotBlank @Size(max = 255) String fourthPartyName,
            @Size(max = 3) String fourthPartyCountry,
            @NotBlank @Size(max = 30) String dependencyType,
            @NotBlank @Size(max = 20) String downstreamCriticality,
            @NotBlank String serviceDescriptionEn,
            String serviceDescriptionAr,
            String dataCategoriesShared,
            String contractChainClauses,
            Short subContractingAllowed,
            Short flowDownClausesPresent,
            String mitigationNotesEn,
            String mitigationNotesAr
    ) {}

    /** Client-settable fields on update — mirrors the previous update whitelist. */
    public record UpdateRequest(
            @Size(max = 255) String primaryVendorName,
            @Size(max = 255) String fourthPartyName,
            @Size(max = 3) String fourthPartyCountry,
            @Size(max = 30) String dependencyType,
            @Size(max = 20) String downstreamCriticality,
            String serviceDescriptionEn,
            String serviceDescriptionAr,
            String dataCategoriesShared,
            String contractChainClauses,
            Short subContractingAllowed,
            Short flowDownClausesPresent,
            String mitigationNotesEn,
            String mitigationNotesAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            Long id,
            String externalId,
            Long primaryVendorId,
            String primaryVendorName,
            String fourthPartyName,
            String fourthPartyCountry,
            String dependencyType,
            String downstreamCriticality,
            String concentrationRisk,
            String serviceDescriptionEn,
            String serviceDescriptionAr,
            String dataCategoriesShared,
            String contractChainClauses,
            Short subContractingAllowed,
            Short flowDownClausesPresent,
            LocalDateTime lastValidatedAt,
            String validatorName,
            LocalDate nextValidationDue,
            String status,
            String mitigationNotesEn,
            String mitigationNotesAr,
            Short isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Response from(FourthPartyExposure e) {
            return new Response(
                    e.getId(), e.getExternalId(),
                    e.getPrimaryVendorId(), e.getPrimaryVendorName(),
                    e.getFourthPartyName(), e.getFourthPartyCountry(),
                    e.getDependencyType(), e.getDownstreamCriticality(),
                    e.getConcentrationRisk(),
                    e.getServiceDescriptionEn(), e.getServiceDescriptionAr(),
                    e.getDataCategoriesShared(), e.getContractChainClauses(),
                    e.getSubContractingAllowed(), e.getFlowDownClausesPresent(),
                    e.getLastValidatedAt(), e.getValidatorName(), e.getNextValidationDue(),
                    e.getStatus(),
                    e.getMitigationNotesEn(), e.getMitigationNotesAr(),
                    e.getIsActive(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
