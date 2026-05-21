package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the compliance-side CAPA-stage state machine.
 *
 * <p>This service drives the highest-traffic remediation workflow in the
 * platform — every finding crosses six stages on its way to CLOSED. PR
 * audit-logs every transition; these tests pin both the state-machine
 * semantics and the audit-trail shape so neither can regress.
 *
 * <p>State flow:
 * <pre>
 *   IDENTIFIED → IMPLEMENTING → OWNER_TESTED → INDEPENDENT_TESTED
 *              → AUDIT_SIGNED_OFF → CLOSED
 * </pre>
 *
 * <p>Failure semantics worth pinning:
 * <ul>
 *   <li>owner FAIL keeps stage at IMPLEMENTING (rework loop)</li>
 *   <li>independent FAIL sends back to IMPLEMENTING (not OWNER_TESTED)</li>
 *   <li>cross-tenant 404 must leave NO audit row under the requesting tenant</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapaWorkflowServiceTest {

    private static final Long ORG_ID    = 7L;
    private static final Long OTHER_ORG = 99L;
    private static final Long FID       = 555L;

    @Mock private AuditFindingRepository   findingRepo;
    @Mock private AuditLogService          audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private CapaWorkflowService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(findingRepo.save(any(AuditFinding.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── Happy-path transitions ─────────────────────────────────────────

    @Test void markImplementing_advancesStage_andAuditsTransition() {
        AuditFinding f = finding("IDENTIFIED");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        AuditFinding out = service.markImplementing(FID);

        assertThat(out.getCapaStage()).isEqualTo("IMPLEMENTING");
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("IMPLEMENTING"), eq("AuditFinding"), eq(FID), msg.capture());
        assertThat(msg.getValue()).contains("Finding #" + FID);
        assertThat(msg.getValue()).contains("IDENTIFIED → IMPLEMENTING");
    }

    @Test void ownerTest_pass_advancesToOwnerTested_andAuditsResult() {
        AuditFinding f = finding("IMPLEMENTING");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        AuditFinding out = service.ownerTest(FID, Map.of("result", "PASS", "notes", "Reviewed evidence"));

        assertThat(out.getCapaStage()).isEqualTo("OWNER_TESTED");
        assertThat(out.getOwnerTestResult()).isEqualTo("PASS");

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        // actionType encodes the result, not the stage — buyer reading the
        // timeline sees PASS/FAIL/PARTIAL prominently colour-coded.
        verify(audit).log(eq("OWNER_TEST_PASS"), eq("AuditFinding"), eq(FID), msg.capture());
        assertThat(msg.getValue()).contains("owner test PASS");
        assertThat(msg.getValue()).contains("IMPLEMENTING → OWNER_TESTED");
        assertThat(msg.getValue()).contains("Reviewed evidence");
    }

    @Test void ownerTest_fail_keepsStage_andAuditsFail() {
        // Owner FAIL forces a rework loop: stage stays at IMPLEMENTING,
        // but the audit row needs to make the failure visible. Otherwise
        // a reviewer scrolling the timeline can't tell PASS from FAIL.
        AuditFinding f = finding("IMPLEMENTING");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        AuditFinding out = service.ownerTest(FID, Map.of("result", "FAIL"));

        assertThat(out.getCapaStage()).isEqualTo("IMPLEMENTING");
        verify(audit).log(eq("OWNER_TEST_FAIL"), eq("AuditFinding"), eq(FID), anyString());
    }

    @Test void ownerTest_invalidResult_rejected_noAudit() {
        AuditFinding f = finding("IMPLEMENTING");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.ownerTest(FID, Map.of("result", "MAYBE")))
            .isInstanceOf(IllegalArgumentException.class);

        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test void independentTest_fail_sendsBackToImplementing() {
        // Independent FAIL is the only path from OWNER_TESTED back to
        // IMPLEMENTING (skips OWNER_TESTED to avoid an infinite re-test
        // loop). State-machine guard worth pinning.
        AuditFinding f = finding("OWNER_TESTED");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        AuditFinding out = service.independentTest(FID, Map.of("result", "FAIL", "byUserId", 42));

        assertThat(out.getCapaStage()).isEqualTo("IMPLEMENTING");
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("INDEPENDENT_TEST_FAIL"), eq("AuditFinding"), eq(FID), msg.capture());
        assertThat(msg.getValue()).contains("OWNER_TESTED → IMPLEMENTING");
        assertThat(msg.getValue()).contains("by user 42");
    }

    @Test void auditSignoff_advancesStage_andAuditsTransition() {
        AuditFinding f = finding("INDEPENDENT_TESTED");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        service.auditSignoff(FID, Map.of("byUserId", 99));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("AUDIT_SIGNED_OFF"), eq("AuditFinding"), eq(FID), msg.capture());
        assertThat(msg.getValue()).contains("INDEPENDENT_TESTED → AUDIT_SIGNED_OFF");
        assertThat(msg.getValue()).contains("signed off by user 99");
    }

    @Test void close_setsTerminal_andStatusClosed_andAudits() {
        AuditFinding f = finding("AUDIT_SIGNED_OFF");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        AuditFinding out = service.close(FID);

        assertThat(out.getCapaStage()).isEqualTo("CLOSED");
        assertThat(out.getStatus()).isEqualTo("CLOSED");
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("CLOSED"), eq("AuditFinding"), eq(FID), msg.capture());
        assertThat(msg.getValue()).contains("AUDIT_SIGNED_OFF → CLOSED");
        assertThat(msg.getValue()).contains("finding closed");
    }

    // ── State-machine guards ───────────────────────────────────────────

    @Test void wrongStageRejected_noAudit() {
        // Calling markImplementing on a finding already past IDENTIFIED
        // must throw. No audit row should land under the requesting
        // tenant for a guard-rejected transition.
        AuditFinding f = finding("OWNER_TESTED");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.markImplementing(FID))
            .isInstanceOf(IllegalArgumentException.class);

        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        // F8 must also stay silent — a guard-rejected transition has no
        // semantic content for downstream automation.
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── Cross-tenant guarantees ────────────────────────────────────────

    @Test void crossTenantFinding_throwsNotFound_noAudit() {
        // require() does findById then filters by orgId — a finding
        // belonging to OTHER_ORG must be invisible to ORG_ID and must
        // NOT leave an audit trail under ORG_ID either, which would
        // otherwise be a cross-tenant info leak.
        AuditFinding foreign = finding("IDENTIFIED");
        foreign.setOrganisationId(OTHER_ORG);
        when(findingRepo.findById(FID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.markImplementing(FID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        // Belt-and-braces: F8 must also stay silent on a cross-tenant 404.
        // Otherwise the engine would let a rule fire under the requesting
        // tenant for a finding the requester never had access to.
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ── F8 automation events ──────────────────────────────────────────
    // CapaWorkflowService publishes FINDING_CAPA_STAGE_CHANGED on every
    // transition. Single event type with rich payload (newStage,
    // previousStage, result, byUserId, notes) keeps engine matchers
    // simple — rules can target closure via {newStage:"CLOSED"}, rework
    // via {result:"OWNER_TEST_FAIL"}, etc.

    @Test void close_publishesStageChangedEvent_withClosedNewStage() {
        AuditFinding f = finding("AUDIT_SIGNED_OFF");
        f.setSeverity("CRITICAL");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        service.close(FID);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("FINDING_CAPA_STAGE_CHANGED");
        assertThat(ev.entityType()).isEqualTo("AuditFinding");
        assertThat(ev.entityId()).isEqualTo(FID);
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        // Payload carries the fields F8 rules match against. The "filing
        // milestone on CLOSED" rule (analogous to AR-29 from V128) needs
        // newStage to be exactly "CLOSED", not "CAPA_CLOSED" or "Closed".
        assertThat(ev.payload()).containsEntry("newStage", "CLOSED");
        assertThat(ev.payload()).containsEntry("previousStage", "AUDIT_SIGNED_OFF");
        assertThat(ev.payload()).containsEntry("status", "CLOSED");
        assertThat(ev.payload()).containsEntry("severity", "CRITICAL");
        // result is "" (not null) on stage-only transitions — no NPE in
        // the F8 engine's equality check on rules that don't care.
        assertThat(ev.payload()).containsEntry("result", "");
    }

    @Test void ownerTest_fail_publishesEvent_carryingResultForReworkRules() {
        // Owner FAIL keeps stage at IMPLEMENTING. A rule that wants to
        // escalate rework loops must match on the result, not the
        // newStage (which equals previousStage on FAIL). Pin that the
        // event payload actually carries the result.
        AuditFinding f = finding("IMPLEMENTING");
        when(findingRepo.findById(FID)).thenReturn(Optional.of(f));

        service.ownerTest(FID, Map.of("result", "FAIL", "notes", "rollback failed"));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.payload()).containsEntry("result", "OWNER_TEST_FAIL");
        assertThat(ev.payload()).containsEntry("newStage", "IMPLEMENTING");
        assertThat(ev.payload()).containsEntry("previousStage", "IMPLEMENTING");
        assertThat(ev.payload()).containsEntry("notes", "rollback failed");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static AuditFinding finding(String stage) {
        AuditFinding f = new AuditFinding();
        f.setId(FID);
        f.setOrganisationId(ORG_ID);
        f.setCapaStage(stage);
        f.setStatus("OPEN");
        return f;
    }
}
