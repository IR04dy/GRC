package com.tss.grc.module.platform.automation.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.entity.AutomationRuleRun;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRepository;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AutomationRuleEngine — F8 dispatch + condition + run-recording lock.
 *
 * <ul>
 *   <li>Engine fans out to every enabled rule for the trigger.</li>
 *   <li>Condition match → action executor invoked, SUCCESS run recorded,
 *       audit row emitted, rule fire-count bumped.</li>
 *   <li>Condition mismatch → SKIPPED run, no action executed, no rule update.</li>
 *   <li>Executor throws → FAILED run recorded, audit ERROR emitted,
 *       exception swallowed (other rules keep firing).</li>
 *   <li>Null condition → always matches.</li>
 *   <li>Boolean condition: payload string "true"/"false" coerce correctly.</li>
 *   <li>Missing organisationId on event → engine returns early, no work.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AutomationRuleEngineTest {

    private static final Long ORG_ID = 7L;

    @Mock private AutomationRuleRepository rules;
    @Mock private AutomationRuleRunRepository runs;
    @Mock private AuditLogService audit;
    @Mock private AutomationActionExecutor actions;

    @InjectMocks private AutomationRuleEngine engine;

    private static AutomationRule rule(String code, String trigger, String condition, String action) {
        AutomationRule r = new AutomationRule();
        r.setId(1L); r.setOrganisationId(ORG_ID);
        r.setRuleCode(code); r.setNameEn(code);
        r.setTriggerEvent(trigger); r.setConditionJson(condition); r.setActionJson(action);
        r.setEnabled(true); r.setAuditRequired(true);
        r.setPriority(50); r.setFireCount(0L);
        return r;
    }

    private static DomainEvent ev(String trigger, Map<String, Object> payload) {
        return new DomainEvent(ORG_ID, trigger, "Risk", 42L, "corr-1",
            payload == null ? Map.of() : payload);
    }

    // ─── Condition matching (unit-level, no DB) ────────────────────

    @Test void condition_nullAlwaysMatches() {
        boolean ok = engine.conditionMatches(null, Map.of("any", "value"));
        assertThat(ok).isTrue();
    }

    @Test void condition_emptyJsonAlwaysMatches() {
        assertThat(engine.conditionMatches("{}", Map.of())).isTrue();
    }

    @Test void condition_booleanCoerces() {
        assertThat(engine.conditionMatches("{\"affects_personal_data\":true}",
            Map.of("affects_personal_data", "true"))).isTrue();
        assertThat(engine.conditionMatches("{\"affects_personal_data\":true}",
            Map.of("affects_personal_data", false))).isFalse();
    }

    @Test void condition_stringEqualityCaseSensitive() {
        assertThat(engine.conditionMatches("{\"severity\":\"CRITICAL\"}",
            Map.of("severity", "CRITICAL"))).isTrue();
        assertThat(engine.conditionMatches("{\"severity\":\"CRITICAL\"}",
            Map.of("severity", "critical"))).isFalse();
    }

    @Test void condition_missingPayloadKeyFailsMatch() {
        assertThat(engine.conditionMatches("{\"k\":\"v\"}", Map.of())).isFalse();
    }

    // ─── Dispatch behaviour (full event flow) ─────────────────────

    @Test void onEvent_matchingConditionFiresAndRecordsSuccess() throws Exception {
        AutomationRule r = rule("AR-04",
            "INCIDENT_CREATED",
            "{\"affects_personal_data\":true}",
            "{\"type\":\"AUDIT\",\"severity\":\"WARN\",\"message\":\"breach\"}");
        when(rules.findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
            ORG_ID, "INCIDENT_CREATED")).thenReturn(List.of(r));
        when(actions.execute(eq(r), any())).thenReturn("AUDIT(WARN) emitted");

        engine.onDomainEvent(ev("INCIDENT_CREATED", Map.of("affects_personal_data", true)));

        ArgumentCaptor<AutomationRuleRun> runCap = ArgumentCaptor.forClass(AutomationRuleRun.class);
        verify(runs).save(runCap.capture());
        assertThat(runCap.getValue().getOutcome()).isEqualTo("SUCCESS");
        assertThat(runCap.getValue().getResultSummary()).contains("AUDIT(WARN)");

        verify(rules).save(any(AutomationRule.class));
        verify(audit, times(1)).recordEvent(any());   // rule-fired audit row
        assertThat(r.getFireCount()).isEqualTo(1L);
        assertThat(r.getLastFiredAt()).isNotNull();
    }

    @Test void onEvent_conditionMissesRecordsSkipped() {
        AutomationRule r = rule("AR-04",
            "INCIDENT_CREATED",
            "{\"affects_personal_data\":true}",
            "{\"type\":\"AUDIT\"}");
        when(rules.findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
            ORG_ID, "INCIDENT_CREATED")).thenReturn(List.of(r));

        engine.onDomainEvent(ev("INCIDENT_CREATED", Map.of("affects_personal_data", false)));

        ArgumentCaptor<AutomationRuleRun> runCap = ArgumentCaptor.forClass(AutomationRuleRun.class);
        verify(runs).save(runCap.capture());
        assertThat(runCap.getValue().getOutcome()).isEqualTo("SKIPPED");
        verify(rules, never()).save(any(AutomationRule.class));
        verify(audit, never()).recordEvent(any());
    }

    @Test void onEvent_actionThrowsRecordsFailed() throws Exception {
        AutomationRule r = rule("AR-06",
            "RISK_APPETITE_BREACHED",
            null,
            "{\"type\":\"ESCALATE\"}");
        when(rules.findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
            ORG_ID, "RISK_APPETITE_BREACHED")).thenReturn(List.of(r));
        when(actions.execute(any(), any())).thenThrow(new RuntimeException("boom"));

        engine.onDomainEvent(ev("RISK_APPETITE_BREACHED", null));

        ArgumentCaptor<AutomationRuleRun> runCap = ArgumentCaptor.forClass(AutomationRuleRun.class);
        verify(runs).save(runCap.capture());
        assertThat(runCap.getValue().getOutcome()).isEqualTo("FAILED");
        assertThat(runCap.getValue().getErrorMessage()).contains("boom");
        verify(audit, times(1)).recordEvent(any());   // ERROR audit row
    }

    @Test void onEvent_noMatchingRulesIsNoop() {
        when(rules.findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
            anyLong(), anyString())).thenReturn(List.of());

        engine.onDomainEvent(ev("UNHEARD_EVENT", null));
        verify(runs, never()).save(any());
        verify(audit, never()).recordEvent(any());
    }

    @Test void onEvent_missingOrgIdShortCircuits() {
        DomainEvent e = new DomainEvent(null, "X", "Risk", 1L, null, Map.of());
        engine.onDomainEvent(e);
        verify(rules, never()).findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
            anyLong(), anyString());
    }

    // ── F2-319: the tenant is anchored at the async thread boundary ─────────────────────────────

    /**
     * ⚠⚠ F2-319 — {@code onDomainEvent} is {@code @Async} and {@code @EnableAsync} is present, so it
     * runs on a <b>pool thread</b> where {@code TenantContext} — a {@code ThreadLocal} — is absent.
     * {@code AuditLogService.recordEvent} then falls back to organisation 1, so before this every
     * audit row the engine and {@code AutomationActionExecutor} wrote (nine sites) was filed under
     * tenant 1 whatever tenant's rule had fired.
     *
     * <p>🔑 <b>A mock that records only its arguments cannot see this</b>: the organisation is never
     * an argument to {@code recordEvent}, it is read from the thread. So the stub captures
     * {@code TenantContext} at the moment of the call — the same technique [[F2-317]] needed.
     *
     * <p>🔑 And unlike F2-317/318, no publisher could have helped: the ThreadLocal does not cross the
     * async boundary, which is why one anchor at the entry point is the fix rather than nine.
     */
    @Test
    @DisplayName("F2-319: every audit row the engine writes carries the event's own organisation")
    void theAuditRowCarriesTheEventsOrganisationNotTheTenantOneFallback() throws Exception {
        java.util.List<Long> seen = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            seen.add(com.tss.grc.common.tenant.TenantContext.getCurrentTenant());
            return null;
        }).when(audit).recordEvent(org.mockito.ArgumentMatchers.any());

        AutomationRule r = rule("AR-901", "RISK_CREATED", null, "NOTIFY");
        r.setAuditRequired(true);
        when(rules.findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
                ORG_ID, "RISK_CREATED")).thenReturn(java.util.List.of(r));
        when(actions.execute(org.mockito.ArgumentMatchers.any(),
                             org.mockito.ArgumentMatchers.any())).thenReturn("ok");

        engine.onDomainEvent(ev("RISK_CREATED", java.util.Map.of()));

        assertThat(seen).as("""
            An audit row from the automation engine was written under the wrong organisation.             AuditLogService falls back to tenant 1 when TenantContext is unset, and this listener             runs on a pool thread where it always is — so before F2-319 this read [1].""")
            .containsExactly(ORG_ID);
    }

    /**
     * ⚠ A <b>pool thread is reused</b>, so a tenant left set would leak into the next event's
     * processing — a rule for organisation 2 auditing as organisation 7 because that was the previous
     * task. This matters more here than for a scheduler, which owns its thread for the run.
     */
    @Test
    @DisplayName("F2-319: the pool thread is left with no tenant, so nothing leaks to the next event")
    void thePoolThreadIsLeftCleanForTheNextEvent() {
        com.tss.grc.common.tenant.TenantContext.clear();
        when(rules.findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
                ORG_ID, "RISK_CREATED")).thenReturn(java.util.List.of());

        engine.onDomainEvent(ev("RISK_CREATED", java.util.Map.of()));

        assertThat(com.tss.grc.common.tenant.TenantContext.getCurrentTenant()).as("""
            The tenant survived the listener, so the next event handled on this pooled thread would             be audited under this event's organisation.""")
            .isNull();
    }

    /**
     * ⚠ And an event with no organisation is still refused before the anchor, which is the ordering
     * the fix relies on: {@code setCurrentTenant(null)} would put the thread back into the state the
     * fallback exists for.
     */
    @Test
    @DisplayName("F2-319: an event with no organisation is refused before anything is anchored")
    void anEventWithNoOrganisationIsStillRefusedFirst() {
        com.tss.grc.common.tenant.TenantContext.clear();
        engine.onDomainEvent(new DomainEvent(null, "RISK_CREATED", "Risk", 1L, "R-1",
                                             java.util.Map.of()));
        assertThat(com.tss.grc.common.tenant.TenantContext.getCurrentTenant()).isNull();
        org.mockito.Mockito.verify(rules, org.mockito.Mockito.never())
            .findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
