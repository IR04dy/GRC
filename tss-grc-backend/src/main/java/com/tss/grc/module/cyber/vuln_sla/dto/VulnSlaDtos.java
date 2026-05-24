package com.tss.grc.module.cyber.vuln_sla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.cyber.vuln_sla.entity.VulnerabilitySlaFinding;
import com.tss.grc.module.cyber.vuln_sla.entity.VulnerabilitySlaPolicy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request / response DTOs for the Risk-Based Vulnerability SLA REST
 * surface — Sprint 11.
 */
public final class VulnSlaDtos {

    private VulnSlaDtos() {}

    // ─── Policy ───────────────────────────────────────────────────

    public record PolicyRequest(
            @Size(max = 64) String policyCode,
            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String descriptionEn,
            String descriptionAr,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.00") BigDecimal criticalSlaHours,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.00") BigDecimal highSlaHours,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.00") BigDecimal mediumSlaHours,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999.00") BigDecimal lowSlaHours,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal exposurePublicMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal exposurePartnerMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal exposureInternalMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal exposureRestrictedMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal criticalityCriticalMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal criticalityHighMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal criticalityMediumMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("9.999") BigDecimal criticalityLowMul,
            @NotNull @DecimalMin("0.001") @DecimalMax("1.000") BigDecimal escalationWarningPct,
            @NotNull @DecimalMin("0.001") @DecimalMax("1.500") BigDecimal escalationCriticalPct,
            Boolean isDefault
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PolicyResponse(
            Long id,
            UUID externalId,
            String policyCode,
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            BigDecimal criticalSlaHours,
            BigDecimal highSlaHours,
            BigDecimal mediumSlaHours,
            BigDecimal lowSlaHours,
            BigDecimal exposurePublicMul,
            BigDecimal exposurePartnerMul,
            BigDecimal exposureInternalMul,
            BigDecimal exposureRestrictedMul,
            BigDecimal criticalityCriticalMul,
            BigDecimal criticalityHighMul,
            BigDecimal criticalityMediumMul,
            BigDecimal criticalityLowMul,
            BigDecimal escalationWarningPct,
            BigDecimal escalationCriticalPct,
            Boolean isDefault,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static PolicyResponse from(VulnerabilitySlaPolicy p) {
            return new PolicyResponse(
                    p.getId(), p.getExternalId(), p.getPolicyCode(),
                    p.getTitleEn(), p.getTitleAr(),
                    p.getDescriptionEn(), p.getDescriptionAr(),
                    p.getCriticalSlaHours(), p.getHighSlaHours(), p.getMediumSlaHours(), p.getLowSlaHours(),
                    p.getExposurePublicMul(), p.getExposurePartnerMul(),
                    p.getExposureInternalMul(), p.getExposureRestrictedMul(),
                    p.getCriticalityCriticalMul(), p.getCriticalityHighMul(),
                    p.getCriticalityMediumMul(), p.getCriticalityLowMul(),
                    p.getEscalationWarningPct(), p.getEscalationCriticalPct(),
                    p.getIsDefault(),
                    p.getCreatedAt(), p.getUpdatedAt()
            );
        }
    }

    // ─── SLA Finding ──────────────────────────────────────────────

    /**
     * Body used to seed a new SLA finding for an externally-recorded
     * vulnerability. Asset binding + exposure / criticality are
     * optional — when missing the compute service applies the
     * INTERNAL / MEDIUM defaults.
     */
    public record ComputeRequest(
            @NotNull Long vulnerabilityId,
            @NotBlank @Size(max = 20) String cvssSeverity,
            Long assetId,
            @Size(max = 20) String assetExposure,
            @Size(max = 20) String assetCriticality,
            Long policyId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FindingResponse(
            Long id,
            UUID externalId,
            Long vulnerabilityId,
            Long policyId,
            String cvssSeverity,
            Long assetId,
            String assetExposure,
            String assetCriticality,
            BigDecimal baseSlaHours,
            BigDecimal effectiveSlaHours,
            LocalDateTime openedAt,
            LocalDateTime dueAt,
            LocalDateTime lastRecomputedAt,
            LocalDateTime lastEscalationAt,
            String status,
            LocalDateTime remediatedAt,
            Long remediatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static FindingResponse from(VulnerabilitySlaFinding f) {
            return new FindingResponse(
                    f.getId(), f.getExternalId(),
                    f.getVulnerabilityId(), f.getPolicyId(),
                    f.getCvssSeverity(),
                    f.getAssetId(), f.getAssetExposure(), f.getAssetCriticality(),
                    f.getBaseSlaHours(), f.getEffectiveSlaHours(),
                    f.getOpenedAt(), f.getDueAt(),
                    f.getLastRecomputedAt(), f.getLastEscalationAt(),
                    f.getStatus(),
                    f.getRemediatedAt(), f.getRemediatedBy(),
                    f.getCreatedAt(), f.getUpdatedAt()
            );
        }
    }

    public record RemediatedRequest(
            @Size(max = 500) String note
    ) {}

    // ─── Dashboard snapshot ───────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardSnapshot(
            long totalFindings,
            long openFindings,
            long warningFindings,
            long breachedFindings,
            long remediatedFindings,
            long acceptedRiskFindings,
            long falsePositiveFindings,
            List<FindingResponse> breached,
            List<FindingResponse> warning,
            PolicyResponse defaultPolicy
    ) {}
}
