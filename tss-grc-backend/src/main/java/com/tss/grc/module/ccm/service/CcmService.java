package com.tss.grc.module.ccm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ccm.dto.CcmDtos.DashboardSnapshot;
import com.tss.grc.module.ccm.dto.CcmDtos.FailingTestSummary;
import com.tss.grc.module.ccm.dto.CcmDtos.TestDefinitionRequest;
import com.tss.grc.module.ccm.dto.CcmDtos.TestDefinitionResponse;
import com.tss.grc.module.ccm.dto.CcmDtos.TestRunResponse;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import com.tss.grc.module.ccm.entity.ControlTestRun;
import com.tss.grc.module.ccm.repository.ControlTestDefinitionRepository;
import com.tss.grc.module.ccm.repository.ControlTestRunRepository;
import com.tss.grc.module.ccm.runner.ControlTestRunner;
import com.tss.grc.module.ccm.runner.ControlTestRunnerRegistry;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.entity.Issue;
import com.tss.grc.module.platform.service.IssueService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Continuous Control Monitoring service — Sprint 11.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>CRUD on {@link ControlTestDefinition} (operator surface)</li>
 *   <li>Enable / disable + "run now" controls</li>
 *   <li>Recording {@link ControlTestRun} rows with tamper-evident
 *       {@code evidence_hash}</li>
 *   <li>Auto-opening an {@link Issue} after three consecutive
 *       {@code CRITICAL}-severity failures</li>
 *   <li>Dashboard rollups</li>
 * </ul>
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F7 audit_log entries: CREATE, UPDATE, ENABLE, DISABLE, RUN_NOW,
 *       RUN_COMPLETED</li>
 *   <li>F8 events: {@code CCM_TEST_DEFINED}, {@code CCM_TEST_ENABLED},
 *       {@code CCM_TEST_DISABLED}, {@code CCM_TEST_RUN_COMPLETED}
 *       (severity in payload)</li>
 * </ul>
 *
 * <p>The {@code CCM_TEST_RUN_COMPLETED} event fires for every recorded
 * run regardless of outcome, so automation rules can subscribe to the
 * subset they care about (e.g. "SAMA-reportable on CRITICAL FAIL").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CcmService {

    /** Threshold of consecutive CRITICAL-severity failures that opens an Issue. */
    static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;

    private final ControlTestDefinitionRepository defRepo;
    private final ControlTestRunRepository runRepo;
    private final ControlTestRunnerRegistry runners;
    private final IssueService issueService;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Definition CRUD ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TestDefinitionResponse> listDefinitions() {
        Long orgId = TenantContext.getCurrentTenant();
        return defRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId)
                .stream()
                .map(TestDefinitionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestDefinitionResponse getDefinition(UUID externalId) {
        return TestDefinitionResponse.from(mustGetDefinition(externalId));
    }

    @Transactional
    public TestDefinitionResponse createDefinition(TestDefinitionRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        ControlTestDefinition d = new ControlTestDefinition();
        d.setOrganisationId(orgId);
        d.setTestCode(req.testCode() == null || req.testCode().isBlank()
                ? generateCode(orgId, req.testType())
                : req.testCode());
        applyFields(d, req);
        d.setIsEnabled(Boolean.FALSE);
        d.setConsecutiveFailures(0);
        d = defRepo.save(d);

        audit.log("CREATE", "ControlTestDefinition", d.getId(),
                "CCM test defined: " + d.getTestCode() + " — " + safe(d.getTitleEn())
                        + " · severity=" + d.getSeverity() + " · type=" + d.getTestType());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "CCM_TEST_DEFINED",
                "ControlTestDefinition", d.getId(), d.getTestCode(),
                payload(d, null)));
        return TestDefinitionResponse.from(d);
    }

    @Transactional
    public TestDefinitionResponse updateDefinition(UUID externalId, TestDefinitionRequest req) {
        ControlTestDefinition d = mustGetDefinition(externalId);
        applyFields(d, req);
        d = defRepo.save(d);
        audit.log("UPDATE", "ControlTestDefinition", d.getId(),
                "CCM test updated: " + d.getTestCode());
        return TestDefinitionResponse.from(d);
    }

    @Transactional
    public TestDefinitionResponse enableTest(UUID externalId) {
        ControlTestDefinition d = mustGetDefinition(externalId);
        if (Boolean.TRUE.equals(d.getIsEnabled())) {
            return TestDefinitionResponse.from(d);
        }
        d.setIsEnabled(Boolean.TRUE);
        d.setConsecutiveFailures(0);
        d = defRepo.save(d);
        audit.log("ENABLE", "ControlTestDefinition", d.getId(),
                "CCM test enabled: " + d.getTestCode());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "CCM_TEST_ENABLED",
                "ControlTestDefinition", d.getId(), d.getTestCode(),
                payload(d, null)));
        return TestDefinitionResponse.from(d);
    }

    @Transactional
    public TestDefinitionResponse disableTest(UUID externalId) {
        ControlTestDefinition d = mustGetDefinition(externalId);
        if (Boolean.FALSE.equals(d.getIsEnabled())) {
            return TestDefinitionResponse.from(d);
        }
        d.setIsEnabled(Boolean.FALSE);
        d = defRepo.save(d);
        audit.log("DISABLE", "ControlTestDefinition", d.getId(),
                "CCM test disabled: " + d.getTestCode());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "CCM_TEST_DISABLED",
                "ControlTestDefinition", d.getId(), d.getTestCode(),
                payload(d, null)));
        return TestDefinitionResponse.from(d);
    }

    // ─── Run execution ────────────────────────────────────────────

    /**
     * Synchronous "Run now" — invoked by an operator from the UI. Looks
     * up the runner for the test_type, executes it, and records the run.
     */
    @Transactional
    public TestRunResponse runTestNow(UUID externalId) {
        ControlTestDefinition d = mustGetDefinition(externalId);
        return runDefinitionInternal(d);
    }

    /**
     * Internal entry point shared by "run now" + the scheduler. Always
     * records exactly one {@link ControlTestRun} row (even on runner
     * failure) so the dashboard's run-history is complete.
     */
    @Transactional
    public TestRunResponse runDefinitionInternal(ControlTestDefinition d) {
        Optional<ControlTestRunner> runner = runners.find(d.getTestType());
        ControlTestRunner.RunResult result;
        if (runner.isEmpty()) {
            result = ControlTestRunner.RunResult.error(
                    "No runner registered for test_type=" + d.getTestType(), 0);
        } else {
            try {
                result = runner.get().execute(d);
            } catch (RuntimeException ex) {
                // Defence-in-depth — the runner contract says "never
                // throw" but if a buggy runner does we still record an
                // ERROR row so the scheduler loop survives.
                log.warn("CCM runner threw for test {}", d.getTestCode(), ex);
                result = ControlTestRunner.RunResult.error(
                        ex.getClass().getSimpleName() + ": " + ex.getMessage(), 0);
            }
        }
        return recordRunInternal(d, result);
    }

    /**
     * Public API used by tests + the controller to record a synthetic
     * run (no live runner invocation). Computes evidence_hash, updates
     * the parent definition's last_run_*, and handles the
     * consecutive-failure → Issue ladder.
     */
    @Transactional
    public TestRunResponse recordRun(UUID defExternalId, String status,
                                     String actualJson, String failureReason, Integer durationMs) {
        ControlTestDefinition d = mustGetDefinition(defExternalId);
        return recordRunInternal(d, new ControlTestRunner.RunResult(status, actualJson, failureReason, durationMs));
    }

    private TestRunResponse recordRunInternal(ControlTestDefinition d, ControlTestRunner.RunResult result) {
        Long orgId = d.getOrganisationId();
        LocalDateTime now = LocalDateTime.now();

        ControlTestRun run = new ControlTestRun();
        run.setOrganisationId(orgId);
        run.setTestDefinitionId(d.getId());
        run.setRunAt(now);
        run.setStatus(result.status());
        run.setActualValueJson(result.actualValueJson());
        run.setFailureReason(result.failureReason());
        run.setDurationMs(result.durationMs());
        run.setEvidenceHash(computeEvidenceHash(result.actualValueJson(), now));
        run = runRepo.save(run);

        // Update parent definition counters.
        d.setLastRunAt(now);
        d.setLastRunStatus(result.status());
        boolean isFailureLike = isFailureLike(result.status());
        if (isFailureLike) {
            d.setConsecutiveFailures(d.getConsecutiveFailures() == null
                    ? 1 : d.getConsecutiveFailures() + 1);
        } else if ("PASS".equals(result.status())) {
            d.setConsecutiveFailures(0);
        }
        d = defRepo.save(d);

        // Issue auto-open ladder — only CRITICAL-severity tests trigger
        // the cross-module Issue.
        if (isFailureLike && "CRITICAL".equals(d.getSeverity())
                && d.getConsecutiveFailures() >= CONSECUTIVE_FAILURE_THRESHOLD
                && run.getCapaTaskId() == null) {
            Long issueId = openConsecutiveFailureIssue(d, run);
            if (issueId != null) {
                run.setCapaTaskId(issueId);
                run = runRepo.save(run);
            }
        }

        audit.log("RUN_COMPLETED", "ControlTestDefinition", d.getId(),
                "CCM run " + result.status() + " for " + d.getTestCode()
                        + (result.failureReason() == null ? "" : " — " + result.failureReason()));
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "CCM_TEST_RUN_COMPLETED",
                "ControlTestDefinition", d.getId(), d.getTestCode(),
                runPayload(d, run)));
        return TestRunResponse.from(run, d);
    }

    private Long openConsecutiveFailureIssue(ControlTestDefinition d, ControlTestRun run) {
        try {
            Issue issue = new Issue();
            issue.setOrganisationId(d.getOrganisationId());
            issue.setTitleEn("CCM: " + d.getTestCode() + " failed "
                    + CONSECUTIVE_FAILURE_THRESHOLD + " consecutive runs");
            issue.setTitleAr("المراقبة المستمرة للضوابط: فشل الاختبار "
                    + d.getTestCode() + " في " + CONSECUTIVE_FAILURE_THRESHOLD + " مرات متتالية");
            issue.setDescription("Test " + safe(d.getTestCode()) + " (" + safe(d.getTitleEn())
                    + ") has failed " + d.getConsecutiveFailures() + " consecutive runs. "
                    + "Severity: " + safe(d.getSeverity()) + ". "
                    + "Latest failure reason: " + safe(run.getFailureReason()));
            issue.setSourceModule("ccm");
            issue.setSourceEntityType("ControlTestDefinition");
            issue.setSourceEntityId(d.getId());
            issue.setSeverity("CRITICAL");
            issue.setPriority("URGENT");
            Issue saved = issueService.create(issue);
            return saved.getId();
        } catch (RuntimeException ex) {
            // Auto-open is best-effort — never let an Issue-creation
            // failure poison a run. The audit log still records the
            // bad outcome and the consecutive-failure counter is
            // already incremented.
            log.warn("Failed to auto-open Issue for {}", d.getTestCode(), ex);
            return null;
        }
    }

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TestRunResponse> getRunsForTest(UUID externalId, int limit) {
        Long orgId = TenantContext.getCurrentTenant();
        ControlTestDefinition d = mustGetDefinition(externalId);
        return runRepo.findRecentForTest(orgId, d.getId(), PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(r -> TestRunResponse.from(r, d))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TestRunResponse> listLatestRuns(int limit) {
        Long orgId = TenantContext.getCurrentTenant();
        List<ControlTestRun> runs = runRepo.findRecent(orgId, PageRequest.of(0, Math.max(1, limit)));
        // Bulk-load the matching definitions so each row gets its title /
        // code / severity without an N+1.
        Set<Long> defIds = runs.stream().map(ControlTestRun::getTestDefinitionId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, ControlTestDefinition> defs = defRepo.findAllById(defIds).stream()
                .collect(java.util.stream.Collectors.toMap(ControlTestDefinition::getId, x -> x));
        return runs.stream()
                .map(r -> TestRunResponse.from(r, defs.get(r.getTestDefinitionId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot dashboard() {
        Long orgId = TenantContext.getCurrentTenant();
        List<ControlTestDefinition> defs =
                defRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId);
        long enabled = defs.stream().filter(d -> Boolean.TRUE.equals(d.getIsEnabled())).count();

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long passing = runRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "PASS", since);
        long failing = runRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "FAIL", since);
        long error = runRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "ERROR", since);
        long timeout = runRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(orgId, "TIMEOUT", since);
        long total24 = passing + failing + error + timeout;
        double passRate = total24 == 0 ? 0.0 : (double) passing / (double) total24;

        List<TestRunResponse> recent = listLatestRuns(20);
        List<FailingTestSummary> failingDefs = defs.stream()
                .filter(d -> "FAIL".equals(d.getLastRunStatus()) || "ERROR".equals(d.getLastRunStatus())
                        || "TIMEOUT".equals(d.getLastRunStatus()))
                .map(d -> new FailingTestSummary(
                        d.getExternalId(), d.getTestCode(), d.getTitleEn(), d.getTitleAr(),
                        d.getSeverity(), d.getConsecutiveFailures(),
                        d.getLastRunAt(), d.getLastRunStatus(), d.getControlId()))
                .toList();

        return new DashboardSnapshot(
                defs.size(), enabled,
                passing, failing, error, timeout,
                passRate, recent, failingDefs
        );
    }

    // ─── Helpers ──────────────────────────────────────────────────

    public ControlTestDefinition mustGetDefinition(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("ControlTestDefinition not found: " + externalId));
    }

    private void applyFields(ControlTestDefinition d, TestDefinitionRequest r) {
        if (r.titleEn()        != null) d.setTitleEn(r.titleEn());
        if (r.titleAr()        != null) d.setTitleAr(r.titleAr());
        if (r.descriptionEn()  != null) d.setDescriptionEn(r.descriptionEn());
        if (r.descriptionAr()  != null) d.setDescriptionAr(r.descriptionAr());
        if (r.controlId()      != null) d.setControlId(r.controlId());
        if (r.testType()       != null) d.setTestType(r.testType());
        if (r.targetSystemType() != null) d.setTargetSystemType(r.targetSystemType());
        if (r.targetConfigJson() != null) d.setTargetConfigJson(r.targetConfigJson());
        if (r.expectedConditionJson() != null) d.setExpectedConditionJson(r.expectedConditionJson());
        if (r.severity()       != null) d.setSeverity(r.severity());
        if (r.frequencyCron()  != null) d.setFrequencyCron(r.frequencyCron());
        if (r.timeoutSeconds() != null) d.setTimeoutSeconds(r.timeoutSeconds());
    }

    private String generateCode(Long orgId, String testType) {
        String prefix = "CCM-" + (testType == null ? "TEST" : testType.replace("_", "-"));
        for (int i = 0; i < 10; i++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format("%s-%03d", prefix, seq);
            if (defRepo.countByOrganisationIdAndTestCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * Visible for testing — computes SHA-256(actual || ISO-8601(timestamp)).
     * The resulting hex digest is what we store as {@code evidence_hash}.
     */
    static String computeEvidenceHash(String actualValueJson, LocalDateTime runAt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String src = (actualValueJson == null ? "" : actualValueJson)
                    + "|" + runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships with SHA-256 since Java 1.5.
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    static boolean isFailureLike(String status) {
        return "FAIL".equals(status) || "ERROR".equals(status) || "TIMEOUT".equals(status);
    }

    static Map<String, Object> payload(ControlTestDefinition d, String previousStatus) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("testCode",         safe(d.getTestCode()));
        p.put("externalId",       d.getExternalId() == null ? "" : d.getExternalId().toString());
        p.put("titleEn",          safe(d.getTitleEn()));
        p.put("testType",         safe(d.getTestType()));
        p.put("targetSystem",     safe(d.getTargetSystemType()));
        p.put("severity",         safe(d.getSeverity()));
        p.put("isEnabled",        d.getIsEnabled() != null && d.getIsEnabled());
        p.put("lastRunStatus",    safe(d.getLastRunStatus()));
        p.put("consecutiveFailures", d.getConsecutiveFailures() == null ? 0 : d.getConsecutiveFailures());
        if (previousStatus != null) p.put("previousStatus", previousStatus);
        return p;
    }

    static Map<String, Object> runPayload(ControlTestDefinition d, ControlTestRun run) {
        Map<String, Object> p = new HashMap<>(payload(d, null));
        p.put("runStatus", safe(run.getStatus()));
        p.put("runAt", run.getRunAt() == null ? "" : run.getRunAt().toString());
        p.put("durationMs", run.getDurationMs() == null ? 0 : run.getDurationMs());
        p.put("evidenceHash", safe(run.getEvidenceHash()));
        return p;
    }

    static String safe(String v) { return v == null ? "" : v; }
}
