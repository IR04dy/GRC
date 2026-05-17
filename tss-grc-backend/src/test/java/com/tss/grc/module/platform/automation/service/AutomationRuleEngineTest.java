package com.tss.grc.module.platform.automation.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.entity.AutomationRuleRun;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRepository;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRunRepository;
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
}
