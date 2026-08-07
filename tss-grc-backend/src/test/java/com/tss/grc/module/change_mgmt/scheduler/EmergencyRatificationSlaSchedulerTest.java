package com.tss.grc.module.change_mgmt.scheduler;

import com.tss.grc.module.change_mgmt.entity.ChangeRatificationPolicy;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.model.RatificationStates;
import com.tss.grc.module.change_mgmt.repository.ChangeRatificationPolicyRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A17-D3 — the breach sweep.
 *
 * <p>ADR-017 D-3: "an SLA with no consequence is a report, not a control." These tests
 * assert the consequence actually fires, that it fires once, and that it carries enough
 * to act on — the CISO needs the change, the deadline and how far past it, not a count.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmergencyRatificationSlaSchedulerTest {

    /**
     * F2-160 — a FIXED reference instant, because this class's arithmetic is weekday-sensitive.
     *
     * <p>Every fixture here was built from {@code LocalDateTime.now()}, and one assertion reads "9
     * calendar days past the deadline is at least 6 Saudi business days". That holds on most days and
     * not on all: measured from Saturday 8 August 2026, a 9-calendar-day window contains two Fridays
     * and two Saturdays, so only <b>5</b> business days have elapsed. The suite passed six days a week
     * and went red at midnight with no code change — <b>the clock is an input, and it was an untracked
     * one</b>.
     *
     * <p>Anchored to <b>Sunday</b>, the first day of the Saudi working week, so the numbers below mean
     * what they say. Loosening the assertion to {@code >= 5} was the other option and was rejected: that
     * weakens a real control check to accommodate a test-design flaw.
     */
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 9, 0);

    private static final Long ORG = 1L;
    private static final Long CHANGE = 501L;

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private AuditLogRepository auditRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ChangeRatificationPolicyRepository policyRepo;

    private EmergencyRatificationSlaScheduler scheduler() {
        EmergencyRatificationSlaScheduler s =
            new EmergencyRatificationSlaScheduler(changeRepo, auditRepo, audit, publisher);
        ReflectionTestUtils.setField(s, "policyRepo", policyRepo);
        ChangeRatificationPolicy p = new ChangeRatificationPolicy();
        p.setOrganisationId(ORG);
        p.setEscalationRole("CISO");
        when(policyRepo.findByOrganisationId(ORG)).thenReturn(Optional.of(p));
        return s;
    }

    private static ChangeRequest breached(LocalDateTime due) {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-REVIEW-TEST-D3");
        c.setTitleEn("Emergency firewall rule");
        c.setRiskLevel("HIGH");
        c.setChangeType("EMERGENCY");
        c.setStatus("IMPLEMENTED");
        c.setRatificationState(RatificationStates.PENDING);
        c.setCabDecisionByName("ecab-approver");
        c.setImplementationStarted(due.minusDays(7));
        c.setRatificationDueAt(due);
        return c;
    }

    @Test
    void anUnratifiedEmergencyChangePastItsDeadlineEscalatesToTheCiso() {
        LocalDateTime now = FIXED_NOW;
        LocalDateTime due = now.minusDays(9);
        when(changeRepo.findRatificationBreaches(eq(ORG), any())).thenReturn(List.of(breached(due)));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "ChangeRequest", CHANGE)).thenReturn(List.of());

        int fired = scheduler().sweep(ORG, now);

        assertThat(fired).isEqualTo(1);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo(RatificationStates.EVENT_BREACHED);
        assertThat(ev.getValue().payload())
            .as("the escalation must be actionable — which change, whose approval, how far past")
            .containsEntry("changeCode", "CHG-REVIEW-TEST-D3")
            .containsEntry("escalateTo", "CISO")
            .containsEntry("approvedByName", "ecab-approver")
            .containsKey("businessDaysOverdue")
            .containsKey("ratificationDueAt");
        assertThat(Integer.parseInt(String.valueOf(ev.getValue().payload().get("businessDaysOverdue"))))
            .as("9 calendar days past the deadline is at least 6 Saudi business days")
            .isGreaterThanOrEqualTo(6);

        // The escalation is recorded on the change, so the register and the KPI can read it.
        verify(changeRepo).stampRatificationEscalation(eq(CHANGE), any(LocalDateTime.class));
    }

    @Test
    void theLedgerLineNamesTheConsequenceAndNotJustTheFact() {
        LocalDateTime now = FIXED_NOW;
        when(changeRepo.findRatificationBreaches(eq(ORG), any()))
            .thenReturn(List.of(breached(now.minusDays(3))));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "ChangeRequest", CHANGE)).thenReturn(List.of());

        scheduler().sweep(ORG, now);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(anyString(), eq("ChangeRequest"), eq(CHANGE), msg.capture());
        assertThat(msg.getValue())
            .contains("UNRATIFIED")
            .contains("escalated to CISO")
            .as("the ledger must state what the breach MEANS — the register is read by an examiner")
            .contains("unauthorised production change");
    }

    @Test
    void aRestartDoesNotDoubleEscalateTheSameDeadline() {
        LocalDateTime now = FIXED_NOW;
        LocalDateTime due = now.minusDays(4);
        when(changeRepo.findRatificationBreaches(eq(ORG), any())).thenReturn(List.of(breached(due)));

        AuditLogEntry marker = new AuditLogEntry();
        marker.setActionType("CHG_FIRE:RATIFY_BREACH:" + due.toLocalDate());
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "ChangeRequest", CHANGE)).thenReturn(List.of(marker));

        int fired = scheduler().sweep(ORG, now);

        assertThat(fired).isZero();
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
        verify(changeRepo, never()).stampRatificationEscalation(anyLong(), any());
    }

    @Test
    void aMovedDeadlineReArmsTheEscalation() {
        // Dedup is keyed on the due date, not on "escalated once": if the tenant tightens
        // the SLA and a deadline moves, the new deadline deserves its own escalation.
        LocalDateTime now = FIXED_NOW;
        when(changeRepo.findRatificationBreaches(eq(ORG), any()))
            .thenReturn(List.of(breached(now.minusDays(2))));

        AuditLogEntry oldMarker = new AuditLogEntry();
        oldMarker.setActionType("CHG_FIRE:RATIFY_BREACH:" + now.minusDays(20).toLocalDate());
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "ChangeRequest", CHANGE)).thenReturn(List.of(oldMarker));

        assertThat(scheduler().sweep(ORG, now)).isEqualTo(1);
    }

    @Test
    void aChangeWithNoDueDateIsSkippedRatherThanEscalatedOnANull() {
        // ck_change_ratification_clock makes this unreachable through the API. It is
        // asserted anyway because the constraint is what makes it unreachable, and
        // "unreachable given today's schema" is the reasoning that leaves holes open.
        LocalDateTime now = FIXED_NOW;
        ChangeRequest c = breached(now.minusDays(5));
        c.setRatificationDueAt(null);
        when(changeRepo.findRatificationBreaches(eq(ORG), any())).thenReturn(List.of(c));

        assertThat(scheduler().sweep(ORG, now)).isZero();
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    /**
     * The escalation write happens in its own {@code REQUIRES_NEW} transaction.
     *
     * <p>This test exists because the live probe found what six green unit tests could not:
     * the first version annotated {@code sweep()} with {@code @Transactional} and called it
     * from {@code tick()} — self-invocation, so the proxy was bypassed, so the native
     * {@code @Modifying} stamp threw <em>"Executing an update/delete query"</em> on every
     * run while {@code tick()} logged one WARN and moved on. A mocked repository cannot
     * fail that way, so the mocks were green against a control that had never once fired.
     *
     * <p>Asserting the propagation is therefore the point, not decoration: it is the one
     * property whose absence was invisible to every other test in this class.
     */
    @Test
    void theEscalationWriteRunsInItsOwnTransaction() {
        LocalDateTime now = FIXED_NOW;
        when(changeRepo.findRatificationBreaches(eq(ORG), any()))
            .thenReturn(List.of(breached(now.minusDays(3))));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "ChangeRequest", CHANGE)).thenReturn(List.of());

        org.springframework.transaction.PlatformTransactionManager tx =
            org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(
            org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class));

        EmergencyRatificationSlaScheduler s = scheduler();
        ReflectionTestUtils.setField(s, "txManager", tx);

        assertThat(s.sweep(ORG, now)).isEqualTo(1);

        ArgumentCaptor<org.springframework.transaction.TransactionDefinition> def =
            ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(tx).getTransaction(def.capture());
        assertThat(def.getValue().getPropagationBehavior())
            .as("the stamp is a native @Modifying query — it needs a transaction, and "
              + "REQUIRES_NEW per change so one unwritable row cannot roll back the "
              + "escalations already recorded for the others")
            .isEqualTo(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(tx).commit(any());
    }

    @Test
    void aQuietTenantProducesNoEscalations() {
        when(changeRepo.findRatificationBreaches(eq(ORG), any())).thenReturn(List.of());

        assertThat(scheduler().sweep(ORG, FIXED_NOW)).isZero();
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }
}
