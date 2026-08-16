package com.tss.grc.module.bcm.mtpd_inheritance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the Slice 10-E Vendor MTPD inheritance endpoints.
 *
 * <p>Grouped in one file the same way Slice 10-D groups its
 * request/response shapes — controller signatures stay compact and
 * discoverable.
 */
public final class MtpdInheritanceDtos {
    private MtpdInheritanceDtos() {}

    // ─── Process ↔ Vendor dependency ────────────────────────────────

    public record DependencyCreateRequest(
        @NotNull @Positive Long businessProcessId,
        @NotNull @Positive Long vendorId,
        @NotBlank @Pattern(regexp = "REQUIRED|RECOMMENDED|FALLBACK") String dependencyType,
        @NotBlank @Pattern(regexp = "TIGHT|LOOSE|TRANSIENT") String dependencyStrength,
        @Size(max = 1000) String notesEn,
        @Size(max = 1000) String notesAr
    ) {}

    public record DependencyUpdateRequest(
        @Pattern(regexp = "REQUIRED|RECOMMENDED|FALLBACK") String dependencyType,
        @Pattern(regexp = "TIGHT|LOOSE|TRANSIENT") String dependencyStrength,
        @Size(max = 1000) String notesEn,
        @Size(max = 1000) String notesAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DependencyResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long businessProcessId,
        String businessProcessNameEn,
        String businessProcessNameAr,
        Long vendorId,
        String vendorName,
        String vendorNameAr,
        String dependencyType,
        String dependencyStrength,
        String notesEn,
        String notesAr,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Compute request ────────────────────────────────────────────

    public record ComputeRequest(
        @NotNull @Positive Integer cycleYear,
        @Pattern(regexp = "Q1|Q2|Q3|Q4|") String cycleQuarter,
        @Size(max = 2000) String notesEn,
        @Size(max = 2000) String notesAr
    ) {}

    // ─── Assessment response ────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssessmentResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String assessmentCode,
        Integer cycleYear,
        String cycleQuarter,
        LocalDateTime computedAt,
        Long computedByUserId,
        Integer totalProcesses,
        Integer totalBreaches,
        Integer totalWarnings,
        Integer totalOk,
        Integer totalNoAttestation,
        Boolean isPublished,
        LocalDateTime publishedAt,
        Long publishedByUserId,
        String notesEn,
        String notesAr,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    /** Assessment header + grouped findings (returned by detail endpoint). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssessmentDetailResponse(
        AssessmentResponse assessment,
        List<FindingResponse> findings
    ) {}

    // ─── Finding response ───────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FindingResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long assessmentId,
        Long businessProcessId,
        String businessProcessNameEn,
        String businessProcessNameAr,
        Long vendorId,
        String vendorName,
        Integer processMtpdHours,
        Integer vendorAttestedMtpdHours,
        Integer varianceHours,
        String findingType,
        String severity,
        String dependencyStrength,
        String dependencyType,
        String recommendationEn,
        String recommendationAr,
        Long capaTaskId,
        String capaExternalId,
        LocalDateTime capaCreatedAt,
        Long sourceAttestationId,
        String sourceCampaignCode,
        LocalDateTime sourceSubmittedAt,
        LocalDateTime createdAt
    ) {}

    // ─── Publish + CAPA actions ─────────────────────────────────────

    /**
     * F2-224 — {@code publishedByUserId} is gone: the publisher is the authenticated principal,
     * resolved in {@code MtpdInheritanceService#publishAssessment}.
     */
    public record PublishRequest(
        @Size(max = 2000) String notesEn,
        @Size(max = 2000) String notesAr
    ) {}

    public record CapaCreateRequest(
        @Size(max = 255) String owner,
        @Size(max = 10) String severityOverride,
        @Size(max = 1000) String notesEn,
        @Size(max = 1000) String notesAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CapaCreateResponse(
        Long findingId,
        Long capaTaskId,
        String capaExternalId,
        LocalDateTime capaCreatedAt
    ) {}
}
