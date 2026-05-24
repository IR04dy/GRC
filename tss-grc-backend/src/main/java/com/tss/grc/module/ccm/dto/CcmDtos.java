package com.tss.grc.module.ccm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import com.tss.grc.module.ccm.entity.ControlTestRun;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request / response DTOs for the CCM REST surface.
 */
public final class CcmDtos {

    private CcmDtos() {}

    // ─── Test definition ──────────────────────────────────────────

    public record TestDefinitionRequest(
            String testCode,
            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String descriptionEn,
            String descriptionAr,
            Long controlId,
            @NotBlank @Size(max = 40) String testType,
            @NotBlank @Size(max = 40) String targetSystemType,
            String targetConfigJson,
            String expectedConditionJson,
            @Size(max = 20) String severity,
            @NotBlank @Size(max = 40) String frequencyCron,
            Integer timeoutSeconds
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TestDefinitionResponse(
            Long id,
            UUID externalId,
            String testCode,
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            Long controlId,
            String testType,
            String targetSystemType,
            String targetConfigJson,
            String expectedConditionJson,
            String severity,
            String frequencyCron,
            Integer timeoutSeconds,
            Boolean isEnabled,
            LocalDateTime lastRunAt,
            String lastRunStatus,
            Integer consecutiveFailures,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static TestDefinitionResponse from(ControlTestDefinition d) {
            return new TestDefinitionResponse(
                    d.getId(), d.getExternalId(), d.getTestCode(),
                    d.getTitleEn(), d.getTitleAr(),
                    d.getDescriptionEn(), d.getDescriptionAr(),
                    d.getControlId(),
                    d.getTestType(), d.getTargetSystemType(),
                    d.getTargetConfigJson(), d.getExpectedConditionJson(),
                    d.getSeverity(), d.getFrequencyCron(),
                    d.getTimeoutSeconds(),
                    d.getIsEnabled(),
                    d.getLastRunAt(), d.getLastRunStatus(),
                    d.getConsecutiveFailures(),
                    d.getCreatedAt(), d.getUpdatedAt()
            );
        }
    }

    // ─── Run ──────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TestRunResponse(
            Long id,
            UUID externalId,
            Long testDefinitionId,
            String testCode,
            String titleEn,
            String titleAr,
            String testType,
            String targetSystemType,
            String severity,
            LocalDateTime runAt,
            Integer durationMs,
            String status,
            String actualValueJson,
            String failureReason,
            String evidenceHash,
            Long capaTaskId
    ) {
        public static TestRunResponse from(ControlTestRun r, ControlTestDefinition d) {
            return new TestRunResponse(
                    r.getId(), r.getExternalId(),
                    r.getTestDefinitionId(),
                    d == null ? null : d.getTestCode(),
                    d == null ? null : d.getTitleEn(),
                    d == null ? null : d.getTitleAr(),
                    d == null ? null : d.getTestType(),
                    d == null ? null : d.getTargetSystemType(),
                    d == null ? null : d.getSeverity(),
                    r.getRunAt(), r.getDurationMs(),
                    r.getStatus(), r.getActualValueJson(),
                    r.getFailureReason(), r.getEvidenceHash(),
                    r.getCapaTaskId()
            );
        }
    }

    // ─── Dashboard snapshot ───────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardSnapshot(
            long totalTests,
            long enabledTests,
            long passingLast24h,
            long failingLast24h,
            long errorLast24h,
            long timeoutLast24h,
            double passRateLast24h,
            List<TestRunResponse> recentRuns,
            List<FailingTestSummary> failingTests
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FailingTestSummary(
            UUID externalId,
            String testCode,
            String titleEn,
            String titleAr,
            String severity,
            Integer consecutiveFailures,
            LocalDateTime lastRunAt,
            String lastRunStatus,
            Long controlId
    ) {}
}
