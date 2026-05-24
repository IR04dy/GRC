package com.tss.grc.module.cyber.asm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceAsset;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceFinding;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceShadowIt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request / response DTOs for the ASM REST surface — Sprint 11.
 */
public final class AsmDtos {

    private AsmDtos() {}

    // ─── Asset ────────────────────────────────────────────────────

    public record AssetRequest(
            String assetCode,
            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String descriptionEn,
            String descriptionAr,
            @NotBlank @Size(max = 40) String assetType,
            @Size(max = 255) String hostname,
            @Size(max = 64) String ipAddress,
            Integer port,
            @Size(max = 20) String protocol,
            @Size(max = 500) String certSubject,
            @Size(max = 255) String certIssuer,
            LocalDateTime certExpiresAt,
            @NotNull Long assetOwnerUserId,
            @Size(max = 255) String businessUnitEn,
            @Size(max = 255) String businessUnitAr,
            @NotBlank @Size(max = 20) String exposure,
            @NotBlank @Size(max = 20) String criticality,
            @Size(max = 40) String lifecycleStage,
            @NotBlank @Size(max = 40) String discoverySource,
            @Size(max = 500) String tags
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssetResponse(
            Long id,
            UUID externalId,
            String assetCode,
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            String assetType,
            String hostname,
            String ipAddress,
            Integer port,
            String protocol,
            String certSubject,
            String certIssuer,
            LocalDateTime certExpiresAt,
            Long assetOwnerUserId,
            String businessUnitEn,
            String businessUnitAr,
            String exposure,
            String criticality,
            String lifecycleStage,
            String discoverySource,
            LocalDateTime discoveredAt,
            LocalDateTime approvedAt,
            LocalDateTime retiredAt,
            String tags,
            BigDecimal riskScore,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static AssetResponse from(AttackSurfaceAsset a) {
            return new AssetResponse(
                    a.getId(), a.getExternalId(), a.getAssetCode(),
                    a.getTitleEn(), a.getTitleAr(),
                    a.getDescriptionEn(), a.getDescriptionAr(),
                    a.getAssetType(),
                    a.getHostname(), a.getIpAddress(), a.getPort(), a.getProtocol(),
                    a.getCertSubject(), a.getCertIssuer(), a.getCertExpiresAt(),
                    a.getAssetOwnerUserId(),
                    a.getBusinessUnitEn(), a.getBusinessUnitAr(),
                    a.getExposure(), a.getCriticality(),
                    a.getLifecycleStage(), a.getDiscoverySource(),
                    a.getDiscoveredAt(), a.getApprovedAt(), a.getRetiredAt(),
                    a.getTags(), a.getRiskScore(),
                    a.getCreatedAt(), a.getUpdatedAt()
            );
        }
    }

    /** Body for explicit lifecycle transitions ({@code POST .../transition}). */
    public record LifecycleTransitionRequest(
            @NotBlank @Size(max = 40) String targetStage,
            @Size(max = 500) String note
    ) {}

    // ─── Finding ──────────────────────────────────────────────────

    public record FindingRequest(
            @NotNull UUID assetExternalId,
            @NotBlank @Size(max = 40) String findingType,
            @NotBlank @Size(max = 20) String severity,
            @NotBlank @Size(max = 40) String source,
            @Size(max = 255) String sourceRef,
            @NotBlank @Size(max = 2000) String descriptionEn,
            @Size(max = 2000) String descriptionAr,
            @Size(max = 1000) String recommendationEn,
            @Size(max = 1000) String recommendationAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FindingResponse(
            Long id,
            UUID externalId,
            Long assetId,
            UUID assetExternalId,
            String assetCode,
            String findingType,
            String severity,
            String source,
            String sourceRef,
            String descriptionEn,
            String descriptionAr,
            String recommendationEn,
            String recommendationAr,
            LocalDateTime openedAt,
            LocalDateTime closedAt,
            String status,
            Long capaTaskId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static FindingResponse from(AttackSurfaceFinding f, AttackSurfaceAsset asset) {
            return new FindingResponse(
                    f.getId(), f.getExternalId(),
                    f.getAssetId(),
                    asset == null ? null : asset.getExternalId(),
                    asset == null ? null : asset.getAssetCode(),
                    f.getFindingType(), f.getSeverity(),
                    f.getSource(), f.getSourceRef(),
                    f.getDescriptionEn(), f.getDescriptionAr(),
                    f.getRecommendationEn(), f.getRecommendationAr(),
                    f.getOpenedAt(), f.getClosedAt(),
                    f.getStatus(), f.getCapaTaskId(),
                    f.getCreatedAt(), f.getUpdatedAt()
            );
        }
    }

    public record FindingTransitionRequest(
            @NotBlank @Size(max = 20) String targetStatus,
            @Size(max = 500) String note
    ) {}

    // ─── Shadow IT ────────────────────────────────────────────────

    public record ShadowItRequest(
            @NotBlank @Size(max = 255) String serviceName,
            @Size(max = 255) String serviceNameAr,
            @Size(max = 255) String vendorDomain,
            @NotBlank @Size(max = 40) String discoveredVia,
            Integer observedUserCount,
            @Size(max = 20) String dataClassification,
            Boolean pdplDataProcessed,
            Long assignedToUserId,
            LocalDate deadline,
            @Size(max = 2000) String notesEn,
            @Size(max = 2000) String notesAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShadowItResponse(
            Long id,
            UUID externalId,
            String serviceName,
            String serviceNameAr,
            String vendorDomain,
            String discoveredVia,
            LocalDateTime firstObservedAt,
            Integer observedUserCount,
            String dataClassification,
            Boolean pdplDataProcessed,
            String status,
            Long sanctionedVendorId,
            Long promotedAssetId,
            Long assignedToUserId,
            LocalDate deadline,
            String notesEn,
            String notesAr,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ShadowItResponse from(AttackSurfaceShadowIt s) {
            return new ShadowItResponse(
                    s.getId(), s.getExternalId(),
                    s.getServiceName(), s.getServiceNameAr(), s.getVendorDomain(),
                    s.getDiscoveredVia(), s.getFirstObservedAt(), s.getObservedUserCount(),
                    s.getDataClassification(), s.getPdplDataProcessed(),
                    s.getStatus(),
                    s.getSanctionedVendorId(), s.getPromotedAssetId(),
                    s.getAssignedToUserId(), s.getDeadline(),
                    s.getNotesEn(), s.getNotesAr(),
                    s.getCreatedAt(), s.getUpdatedAt()
            );
        }
    }

    public record ShadowItSanctionRequest(
            @NotNull Long sanctionedVendorId,
            @Size(max = 500) String note
    ) {}

    public record ShadowItBlockRequest(
            @Size(max = 500) String note
    ) {}

    // ─── Dashboard snapshot ───────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardSnapshot(
            long totalAssets,
            long approvedAssets,
            long shadowItPendingAssets,
            long publicCriticalAssets,
            long openFindings,
            long criticalOpenFindings,
            long pendingShadowIt,
            long pdplShadowIt,
            List<AssetResponse> topRiskAssets,
            List<FindingResponse> recentFindings,
            List<ShadowItResponse> pendingShadowItList
    ) {}
}
