package com.tss.grc.module.audit_committee.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.audit_committee.dashboard.entity.DashboardAttestation;
import com.tss.grc.module.audit_committee.dashboard.entity.DashboardSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request / response DTOs for the Audit Committee Dashboard surface.
 *
 * <p>Live dashboard shape: 10 sections, each a small typed record
 * with a primary KPI + 2-3 supporting tiles. Sections are deliberately
 * shallow so the frontend can lay them out as side-by-side cards.
 */
public final class AuditCommitteeDashboardDtos {

    private AuditCommitteeDashboardDtos() {}

    // ─── Top-level live dashboard ────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LiveDashboardResponse(
            LocalDateTime computedAt,
            RiskSection risk,
            ControlSection controls,
            FindingSection findings,
            CapaSection capa,
            AiSection ai,
            ClimateSection climate,
            ObligationSection obligations,
            IncidentSection incidents,
            VendorBcpSection vendorBcp,
            ThreeLinesSection threeLines
    ) {}

    // ─── Section: Risk posture ───────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RiskSection(
            long totalRisks,
            long criticalCount,
            long highCount,
            long mediumCount,
            long lowCount,
            long aboveAppetiteCount,
            List<RiskTopRow> topAboveAppetite
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RiskTopRow(
            Long id,
            String externalId,
            String titleEn,
            String titleAr,
            String riskLevel,
            Integer residualScore,
            String status
    ) {}

    // ─── Section: Control posture ────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ControlSection(
            long totalControls,
            long effectiveCount,
            long failingCount,
            double effectivenessPct,
            // CCM rollup
            long ccmTotalTests,
            long ccmEnabledTests,
            double ccmPassRateLast24h
    ) {}

    // ─── Section: Audit findings ─────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FindingSection(
            long totalOpen,
            long criticalOpen,
            long highOpen,
            long mediumOpen,
            long lowOpen,
            long ageBucket0to30,
            long ageBucket30to90,
            long ageBucketOver90,
            long closedLastQuarter,
            double closureRateLastQuarterPct
    ) {}

    // ─── Section: CAPA progress ──────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CapaSection(
            long totalOpen,
            long overdueCount,
            long closedThisQuarter,
            double onTrackRatioPct
    ) {}

    // ─── Section: AI/ML models ───────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AiSection(
            long totalModels,
            long criticalCount,
            long highCount,
            long mediumCount,
            long lowCount,
            long missingFairnessAuditCount,
            long unacknowledgedDriftCount
    ) {}

    // ─── Section: Climate scenarios ──────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClimateSection(
            long totalScenarios,
            long approvedScenarios,
            double probabilityWeightedExpectedLossSar,
            // Scope 1+2+3 totals, current vs last year
            double financedEmissionsCurrentTco2e,
            double financedEmissionsPreviousTco2e,
            Double financedEmissionsYoyChangePct
    ) {}

    // ─── Section: Obligations ────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObligationSection(
            long totalActive,
            long overdueCount,
            double attestedThisCyclePct,
            Map<String, Long> regulatorBreakdown
    ) {}

    // ─── Section: Incidents ──────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IncidentSection(
            long lastQuarterTotal,
            long lastQuarterCritical,
            long lastQuarterHigh,
            long lastQuarterMedium,
            long lastQuarterLow,
            Double mttrAverageHours,
            long regulatorNotifiableCount
    ) {}

    // ─── Section: Vendor BCP ─────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VendorBcpSection(
            String latestCampaignCode,
            long latestCampaignInvited,
            long latestCampaignSubmitted,
            double latestCampaignResponseRatePct,
            long belowRtoThresholdCount
    ) {}

    // ─── Section: Three Lines of Defense ─────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ThreeLinesSection(
            long line1Count,
            long line2Count,
            long line3Count,
            long boardEscalations
    ) {}

    // ─── Snapshot generate request ───────────────────────────────

    public record GenerateSnapshotRequest(
            @NotNull LocalDate meetingDate,
            @NotBlank @Size(max = 20)
            @Pattern(regexp = "QUARTERLY|ANNUAL|AD_HOC|OFFCYCLE",
                    message = "meetingType must be QUARTERLY, ANNUAL, AD_HOC or OFFCYCLE")
            String meetingType,
            @Size(max = 64) String snapshotCode  // optional — server generates if blank
    ) {}

    // ─── Snapshot response ───────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SnapshotResponse(
            Long id,
            UUID externalId,
            String snapshotCode,
            LocalDate meetingDate,
            String meetingType,
            LocalDateTime generatedAt,
            Long generatedByUserId,
            String kpisJson,
            String executiveSummaryEn,
            String executiveSummaryAr,
            String committeeResolutions,
            Boolean isFinalized,
            LocalDateTime finalizedAt,
            Long finalizedByUserId,
            List<AttestationResponse> attestations
    ) {
        public static SnapshotResponse from(DashboardSnapshot s, List<DashboardAttestation> attestations) {
            return new SnapshotResponse(
                    s.getId(), s.getExternalId(), s.getSnapshotCode(),
                    s.getMeetingDate(), s.getMeetingType(),
                    s.getGeneratedAt(), s.getGeneratedByUserId(),
                    s.getKpisJson(),
                    s.getExecutiveSummaryEn(), s.getExecutiveSummaryAr(),
                    s.getCommitteeResolutions(),
                    s.getIsFinalized(), s.getFinalizedAt(), s.getFinalizedByUserId(),
                    attestations == null ? List.of()
                        : attestations.stream().map(AttestationResponse::from).toList()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SnapshotSummary(
            Long id,
            UUID externalId,
            String snapshotCode,
            LocalDate meetingDate,
            String meetingType,
            LocalDateTime generatedAt,
            Boolean isFinalized,
            Long attestationCount
    ) {
        public static SnapshotSummary from(DashboardSnapshot s, long count) {
            return new SnapshotSummary(
                    s.getId(), s.getExternalId(), s.getSnapshotCode(),
                    s.getMeetingDate(), s.getMeetingType(),
                    s.getGeneratedAt(),
                    s.getIsFinalized(), count
            );
        }
    }

    // ─── Attest request / response ───────────────────────────────

    public record AttestRequest(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "CHAIR|MEMBER|AUDIT_DIRECTOR|EXTERNAL_AUDITOR|COMPANY_SECRETARY",
                    message = "attestedRole must be one of CHAIR, MEMBER, AUDIT_DIRECTOR, EXTERNAL_AUDITOR, COMPANY_SECRETARY")
            String attestedRole,
            @Size(max = 2000) String attestationTextEn,
            @Size(max = 2000) String attestationTextAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttestationResponse(
            Long id,
            UUID externalId,
            Long snapshotId,
            Long attestedByUserId,
            String attestedRole,
            String attestationTextEn,
            String attestationTextAr,
            LocalDateTime attestedAt,
            String signatureHash
    ) {
        public static AttestationResponse from(DashboardAttestation a) {
            return new AttestationResponse(
                    a.getId(), a.getExternalId(),
                    a.getSnapshotId(), a.getAttestedByUserId(),
                    a.getAttestedRole(),
                    a.getAttestationTextEn(), a.getAttestationTextAr(),
                    a.getAttestedAt(), a.getSignatureHash()
            );
        }
    }

    // ─── Resolution + finalize ───────────────────────────────────

    public record RecordResolutionRequest(
            @NotBlank @Size(max = 2000) String textEn,
            @Size(max = 2000) String textAr
    ) {}

    public record FinalizeRequest(
            @NotBlank @Size(max = 8000) String executiveSummaryEn,
            @Size(max = 8000) String executiveSummaryAr
    ) {}
}
