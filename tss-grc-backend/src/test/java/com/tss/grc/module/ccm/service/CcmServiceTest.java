package com.tss.grc.module.ccm.service;

import com.tss.grc.common.tenant.TenantContext;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CcmService — Sprint 11 Continuous Control Monitoring Engine.
 *
 * <p>Locks the operator-surface state machine (CRUD + enable/disable +
 * run-now), tenant isolation, the F8 event contract, evidence-hash
 * determinism, and the consecutive-failure → Issue auto-open ladder.
 */
@ExtendWith(MockitoExtension.class)
class CcmServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long DEF_ID = 42L;
    private static final UUID DEF_EX = UUID.randomUUID();

    @Mock private ControlTestDefinitionRepository defRepo;
    @Mock private ControlTestRunRepository runRepo;
    @Mock private ControlTestRunnerRegistry runners;
    @Mock private IssueService issueService;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CcmService service;

    @BeforeEach
    void setUp() {
        service = new CcmService(defRepo, runRepo, runners, issueService, audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static ControlTestDefinition def(String severity) {
        ControlTestDefinition d = new ControlTestDefinition();
        d.setId(DEF_ID);
        d.setExternalId(DEF_EX);
        d.setOrganisationId(ORG_ID);
        d.setTestCode("CCM-AD-PRIVGROUP-001");
        d.setTitleEn("AD Domain Admins ≤ 5");
        d.setTitleAr("عدد مسؤولي النطاق لا يتجاوز 5");
        d.setTestType("LDAP_GROUP_MEMBERSHIP");
        d.setTargetSystemType("LDAP_AD");
        d.setSeverity(severity);
        d.setFrequencyCron("0 0 6 * * *");
        d.setTimeoutSeconds(60);
        d.setIsEnabled(Boolean.FALSE);
        d.setConsecutiveFailures(0);
        d.setCreatedAt(LocalDateTime.now().minusDays(1));
        return d;
    }

    private static TestDefinitionRequest req() {
        return new TestDefinitionRequest(
                null,
                "AD Domain Admins ≤ 5",
                "عدد مسؤولي النطاق لا يتجاوز 5",
                "Continuously verify the AD Domain Admins group has no more than five members.",
                "التحقّق المستمر من أن مجموعة \"مسؤولي النطاق\" لا تضم أكثر من خمسة أعضاء.",
                null,
                "LDAP_GROUP_MEMBERSHIP",
                "LDAP_AD",
                "{\"url\":\"${LDAP_URL}\"}",
                "{\"groupDn\":\"CN=Domain Admins\",\"maxMembers\":5}",
                "CRITICAL",
                "0 0 6 * * *",
                60
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── Create / Update ─────────────────────────────────────────

    @Test
    void create_stampsTenantAndDefaultsToDisabled() {
        AtomicLong seq = new AtomicLong(100);
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(assignId(seq));
        when(defRepo.countByOrganisationIdAndTestCode(eq(ORG_ID), anyString())).thenReturn(0L);

        TestDefinitionResponse out = service.createDefinition(req());

        assertThat(out.isEnabled()).isFalse();
        assertThat(out.testCode()).startsWith("CCM-");
        assertThat(out.testType()).isEqualTo("LDAP_GROUP_MEMBERSHIP");

        ArgumentCaptor<ControlTestDefinition> cap = ArgumentCaptor.forClass(ControlTestDefinition.class);
        verify(defRepo).save(cap.capture());
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(cap.getValue().getIsEnabled()).isFalse();
        verify(audit).log(eq("CREATE"), eq("ControlTestDefinition"), anyLong(), anyString());
    }

    @Test
    void create_emitsCcmTestDefinedEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(assignId(seq));
        when(defRepo.countByOrganisationIdAndTestCode(eq(ORG_ID), anyString())).thenReturn(0L);

        service.createDefinition(req());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("CCM_TEST_DEFINED");
        assertThat(ev.entityType()).isEqualTo("ControlTestDefinition");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("severity", "CRITICAL");
        assertThat(ev.payload()).containsEntry("testType", "LDAP_GROUP_MEMBERSHIP");
    }

    @Test
    void update_appliesNewFields() {
        ControlTestDefinition d = def("CRITICAL");
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        TestDefinitionRequest r = new TestDefinitionRequest(
                null, "Updated title", null, null, null, null,
                "LDAP_GROUP_MEMBERSHIP", "LDAP_AD",
                null, null, "HIGH", "0 0 8 * * *", 120
        );
        TestDefinitionResponse out = service.updateDefinition(DEF_EX, r);

        assertThat(out.titleEn()).isEqualTo("Updated title");
        assertThat(out.severity()).isEqualTo("HIGH");
        assertThat(out.frequencyCron()).isEqualTo("0 0 8 * * *");
        assertThat(out.timeoutSeconds()).isEqualTo(120);
    }

    // ─── Enable / Disable ────────────────────────────────────────

    @Test
    void enableTest_flipsFlagAndEmitsEvent() {
        ControlTestDefinition d = def("CRITICAL");
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        TestDefinitionResponse out = service.enableTest(DEF_EX);

        assertThat(out.isEnabled()).isTrue();
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("CCM_TEST_ENABLED");
    }

    @Test
    void disableTest_flipsFlagAndEmitsEvent() {
        ControlTestDefinition d = def("CRITICAL");
        d.setIsEnabled(Boolean.TRUE);
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        TestDefinitionResponse out = service.disableTest(DEF_EX);

        assertThat(out.isEnabled()).isFalse();
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("CCM_TEST_DISABLED");
    }

    // ─── Run-now ─────────────────────────────────────────────────

    @Test
    void runTestNow_happyPath_recordsPass() {
        ControlTestDefinition d = def("CRITICAL");
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlTestRunner runner = new TestRunnerStub("LDAP_GROUP_MEMBERSHIP",
                ControlTestRunner.RunResult.pass("{\"members\":4}", 5));
        when(runners.find("LDAP_GROUP_MEMBERSHIP")).thenReturn(Optional.of(runner));

        AtomicLong seq = new AtomicLong(900);
        when(runRepo.save(any(ControlTestRun.class))).thenAnswer(assignId(seq));

        TestRunResponse out = service.runTestNow(DEF_EX);

        assertThat(out.status()).isEqualTo("PASS");
        assertThat(out.evidenceHash()).isNotBlank().hasSize(64);
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("CCM_TEST_RUN_COMPLETED");
        assertThat(cap.getValue().payload()).containsEntry("runStatus", "PASS");
        verify(audit).log(eq("RUN_COMPLETED"), eq("ControlTestDefinition"), anyLong(), anyString());
    }

    @Test
    void runTestNow_recordsFailWhenRunnerFails() {
        ControlTestDefinition d = def("HIGH");
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlTestRunner runner = new TestRunnerStub("LDAP_GROUP_MEMBERSHIP",
                ControlTestRunner.RunResult.fail("{\"members\":12}", "too many members", 7));
        when(runners.find("LDAP_GROUP_MEMBERSHIP")).thenReturn(Optional.of(runner));

        AtomicLong seq = new AtomicLong(900);
        when(runRepo.save(any(ControlTestRun.class))).thenAnswer(assignId(seq));

        TestRunResponse out = service.runTestNow(DEF_EX);

        assertThat(out.status()).isEqualTo("FAIL");
        assertThat(out.failureReason()).contains("too many members");
        // HIGH severity → consecutive failure should increment but should NOT open Issue.
        ArgumentCaptor<ControlTestDefinition> cap = ArgumentCaptor.forClass(ControlTestDefinition.class);
        verify(defRepo, atLeastOnce()).save(cap.capture());
        ControlTestDefinition saved = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(saved.getConsecutiveFailures()).isEqualTo(1);
        verify(issueService, never()).create(any(Issue.class));
    }

    /**
     * F2-228 — the run row must be written EXACTLY ONCE, even on the escalation path.
     *
     * <p>It used to be saved, then re-saved to carry the auto-opened Issue's id. The first save is
     * an IDENTITY INSERT and {@code BaseEntity} maps {@code row_version}
     * {@code @Version insertable=false updatable=false} over a SQL Server ROWVERSION, so the
     * in-memory version was null and the follow-up UPDATE matched zero rows. Probed live: the
     * <b>third consecutive failure of a CRITICAL control test answered HTTP 409 and rolled the
     * whole transaction back</b> — so neither the Issue nor the failing run itself was recorded,
     * on both {@code POST /ccm/tests/{id}/run-now} and the 60-second scheduler tick. F2-175's
     * defect, in another module.
     *
     * <p>⚠ No mock can reproduce the failure — a mocked repository has no {@code row_version} —
     * so the assertion is the structure the fix guarantees: the Issue is opened first and its id
     * is present on the one and only save.
     */
    @Test
    void runTestNow_atTheConsecutiveFailureThreshold_savesTheRunOnceCarryingTheIssueId() {
        ControlTestDefinition d = def("CRITICAL");
        // Read the threshold off the service, never a second copy of the number (F2-196).
        d.setConsecutiveFailures(CcmService.CONSECUTIVE_FAILURE_THRESHOLD - 1);
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlTestRunner runner = new TestRunnerStub("LDAP_GROUP_MEMBERSHIP",
                ControlTestRunner.RunResult.fail("{\"members\":99}", "way too many", 9));
        when(runners.find("LDAP_GROUP_MEMBERSHIP")).thenReturn(Optional.of(runner));

        AtomicLong seq = new AtomicLong(900);
        when(runRepo.save(any(ControlTestRun.class))).thenAnswer(assignId(seq));
        Issue opened = new Issue();
        opened.setId(4242L);
        when(issueService.create(any(Issue.class))).thenReturn(opened);

        ArgumentCaptor<ControlTestRun> runs = ArgumentCaptor.forClass(ControlTestRun.class);
        TestRunResponse out = service.runTestNow(DEF_EX);

        assertThat(out.status()).isEqualTo("FAIL");
        verify(issueService).create(any(Issue.class));
        verify(runRepo, times(1)).save(runs.capture());
        assertThat(runs.getValue().getCapaTaskId())
                .as("the Issue must be opened BEFORE the single save, so its id rides on the "
                    + "INSERT rather than needing a second UPDATE of a row whose ROWVERSION "
                    + "Hibernate does not yet hold")
                .isEqualTo(4242L);
    }

    @Test
    void runTestNow_recordsErrorWhenNoRunnerRegistered() {
        ControlTestDefinition d = def("MEDIUM");
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(runners.find("LDAP_GROUP_MEMBERSHIP")).thenReturn(Optional.empty());

        AtomicLong seq = new AtomicLong(900);
        when(runRepo.save(any(ControlTestRun.class))).thenAnswer(assignId(seq));

        TestRunResponse out = service.runTestNow(DEF_EX);

        assertThat(out.status()).isEqualTo("ERROR");
        assertThat(out.failureReason()).contains("No runner registered");
    }

    @Test
    void runTestNow_throwsWhenDefinitionMissing() {
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runTestNow(DEF_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── Consecutive failure ladder ──────────────────────────────

    @Test
    void thirdConsecutiveCriticalFailureOpensIssue() {
        ControlTestDefinition d = def("CRITICAL");
        d.setConsecutiveFailures(2);  // next FAIL hits threshold of 3
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlTestRunner runner = new TestRunnerStub("LDAP_GROUP_MEMBERSHIP",
                ControlTestRunner.RunResult.fail("{\"members\":99}", "way over", 5));
        when(runners.find("LDAP_GROUP_MEMBERSHIP")).thenReturn(Optional.of(runner));

        AtomicLong seq = new AtomicLong(900);
        when(runRepo.save(any(ControlTestRun.class))).thenAnswer(assignId(seq));
        Issue created = new Issue();
        created.setId(7777L);
        when(issueService.create(any(Issue.class))).thenReturn(created);

        TestRunResponse out = service.runTestNow(DEF_EX);

        assertThat(out.status()).isEqualTo("FAIL");
        ArgumentCaptor<Issue> issueCap = ArgumentCaptor.forClass(Issue.class);
        verify(issueService).create(issueCap.capture());
        Issue issue = issueCap.getValue();
        assertThat(issue.getSourceModule()).isEqualTo("ccm");
        assertThat(issue.getSeverity()).isEqualTo("CRITICAL");
        assertThat(issue.getPriority()).isEqualTo("URGENT");
        assertThat(issue.getTitleEn()).contains("CCM-AD-PRIVGROUP-001");
        assertThat(issue.getTitleAr()).contains("المراقبة المستمرة للضوابط");
    }

    @Test
    void passResetsConsecutiveFailureCounter() {
        ControlTestDefinition d = def("CRITICAL");
        d.setConsecutiveFailures(2);
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlTestRunner runner = new TestRunnerStub("LDAP_GROUP_MEMBERSHIP",
                ControlTestRunner.RunResult.pass("{\"members\":3}", 3));
        when(runners.find("LDAP_GROUP_MEMBERSHIP")).thenReturn(Optional.of(runner));

        AtomicLong seq = new AtomicLong(900);
        when(runRepo.save(any(ControlTestRun.class))).thenAnswer(assignId(seq));

        service.runTestNow(DEF_EX);

        ArgumentCaptor<ControlTestDefinition> cap = ArgumentCaptor.forClass(ControlTestDefinition.class);
        verify(defRepo, atLeastOnce()).save(cap.capture());
        ControlTestDefinition saved = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(saved.getConsecutiveFailures()).isZero();
    }

    /**
     * F2-371 — a failure PAST the threshold-crossing must NOT open a duplicate Issue. It fired on
     * {@code consecutiveFailures >= THRESHOLD}, calling an UNCONDITIONAL {@code issueService.create()}
     * with no dedup, so a persistently failing CRITICAL control — driven by the 60-second scheduler
     * tick — opened a new CRITICAL/URGENT Issue on the 4th, 5th, 6th … run: one per minute. The fix
     * fires at exactly the crossing ({@code == THRESHOLD}), so a streak opens exactly one Issue.
     */
    @Test
    void aFailurePastTheThresholdDoesNotOpenADuplicateIssue() {
        ControlTestDefinition d = def("CRITICAL");
        // Already at the threshold — the Issue was opened when the streak first crossed it. This is
        // the 4th consecutive failure (counter THRESHOLD → THRESHOLD+1), which the old `>=` re-escalated.
        d.setConsecutiveFailures(CcmService.CONSECUTIVE_FAILURE_THRESHOLD);
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, DEF_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(ControlTestDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlTestRunner runner = new TestRunnerStub("LDAP_GROUP_MEMBERSHIP",
                ControlTestRunner.RunResult.fail("{\"members\":99}", "still failing", 5));
        when(runners.find("LDAP_GROUP_MEMBERSHIP")).thenReturn(Optional.of(runner));

        AtomicLong seq = new AtomicLong(900);
        when(runRepo.save(any(ControlTestRun.class))).thenAnswer(assignId(seq));

        TestRunResponse out = service.runTestNow(DEF_EX);

        assertThat(out.status()).isEqualTo("FAIL");
        verify(issueService, never()).create(any(Issue.class));   // no duplicate escalation

        ArgumentCaptor<ControlTestDefinition> cap = ArgumentCaptor.forClass(ControlTestDefinition.class);
        verify(defRepo, atLeastOnce()).save(cap.capture());
        ControlTestDefinition saved = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(saved.getConsecutiveFailures())
                .as("the streak counter still increments past the threshold")
                .isEqualTo(CcmService.CONSECUTIVE_FAILURE_THRESHOLD + 1);
    }

    // ─── Evidence hash determinism ───────────────────────────────

    @Test
    void evidenceHashIsDeterministic() {
        LocalDateTime t = LocalDateTime.of(2026, 5, 1, 10, 30, 0);
        String hash1 = CcmService.computeEvidenceHash("{\"a\":1}", t);
        String hash2 = CcmService.computeEvidenceHash("{\"a\":1}", t);
        assertThat(hash1).isEqualTo(hash2).hasSize(64);
    }

    @Test
    void evidenceHashChangesWithPayload() {
        LocalDateTime t = LocalDateTime.of(2026, 5, 1, 10, 30, 0);
        String hashA = CcmService.computeEvidenceHash("{\"a\":1}", t);
        String hashB = CcmService.computeEvidenceHash("{\"a\":2}", t);
        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void evidenceHashChangesWithTimestamp() {
        LocalDateTime t1 = LocalDateTime.of(2026, 5, 1, 10, 30, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 5, 1, 10, 31, 0);
        String hashA = CcmService.computeEvidenceHash("{\"a\":1}", t1);
        String hashB = CcmService.computeEvidenceHash("{\"a\":1}", t2);
        assertThat(hashA).isNotEqualTo(hashB);
    }

    // ─── Tenant isolation ────────────────────────────────────────

    @Test
    void getDefinition_otherTenantThrows() {
        TenantContext.setCurrentTenant(999L);
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(999L, DEF_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDefinition(DEF_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void list_appliesTenantScope() {
        when(defRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of(def("HIGH")));
        List<TestDefinitionResponse> out = service.listDefinitions();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).testCode()).isEqualTo("CCM-AD-PRIVGROUP-001");
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /** Trivial in-test runner used as a mocking double. */
    private static final class TestRunnerStub implements ControlTestRunner {
        private final String type;
        private final RunResult result;

        TestRunnerStub(String type, RunResult result) {
            this.type = type;
            this.result = result;
        }
        @Override public String testType() { return type; }
        @Override public RunResult execute(ControlTestDefinition def) { return result; }
    }
}
