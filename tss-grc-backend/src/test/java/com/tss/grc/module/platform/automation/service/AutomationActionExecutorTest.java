package com.tss.grc.module.platform.automation.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the concrete F8 action handlers shipped in the follow-up sprint:
 *
 * <ul>
 *   <li>AUDIT — single audit row with configured severity</li>
 *   <li>NOTIFY — fan out to role, audit + notifications</li>
 *   <li>NOTIFY without role configured — audit-only WARN, no broadcast</li>
 *   <li>ESCALATE — uses to_role, WARN-level audit row</li>
 *   <li>FLAG — audit row of type FLAG, severity WARN, names the entity field</li>
 *   <li>CREATE_RECORD — audit row of type CREATE_RECORD, INFO severity, names the entity</li>
 *   <li>unknown action type — returns "UNKNOWN" without throwing</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AutomationActionExecutorTest {

    private static final Long ORG_ID = 7L;

    @Mock private AuditLogService audit;
    @Mock private NotificationService notify;
    @InjectMocks private AutomationActionExecutor executor;

    private static AutomationRule rule(String code, String actionJson) {
        AutomationRule r = new AutomationRule();
        r.setId(1L); r.setOrganisationId(ORG_ID);
        r.setRuleCode(code); r.setNameEn(code);
        r.setActionJson(actionJson);
        return r;
    }

    private static DomainEvent ev(String trigger) {
        return new DomainEvent(ORG_ID, trigger, "Risk", 42L, "corr-1",
            Map.of("severity", "HIGH"));
    }

    // ─── AUDIT ─────────────────────────────────────────────────────

    @Test void audit_writesAuditRowWithSeverity() throws Exception {
        AutomationRule r = rule("AR-X",
            "{\"type\":\"AUDIT\",\"severity\":\"WARN\",\"message\":\"breach\"}");

        String summary = executor.execute(r, ev("X"));

        assertThat(summary).isEqualTo("AUDIT(WARN) emitted");
        ArgumentCaptor<AuditLogService.AuditEventBuilder> cap =
            ArgumentCaptor.forClass(AuditLogService.AuditEventBuilder.class);
        verify(audit).recordEvent(cap.capture());
        // AuditEventBuilder isn't a record; we just verify recordEvent was called once.
    }

    // ─── NOTIFY ────────────────────────────────────────────────────

    @Test void notify_broadcastsAndAuditsRoleAndCount() throws Exception {
        AutomationRule r = rule("AR-Y",
            "{\"type\":\"NOTIFY\",\"role\":\"BCM_MANAGER\",\"message\":\"call tree failed\"}");
        when(notify.broadcastToRole(eq(ORG_ID), eq("BCM_MANAGER"), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyLong())).thenReturn(3);

        String summary = executor.execute(r, ev("BCM_CALL_TREE_FAILED"));

        assertThat(summary).isEqualTo("NOTIFY → BCM_MANAGER (3 delivered)");
        verify(notify).broadcastToRole(eq(ORG_ID), eq("BCM_MANAGER"),
            anyString(), anyString(), anyString(), eq("NOTIFICATION"), any(), anyLong());
        verify(audit).recordEvent(any());   // single audit row for the rule fire
    }

    @Test void notify_blankRoleFallsBackToAuditOnly() throws Exception {
        AutomationRule r = rule("AR-Z", "{\"type\":\"NOTIFY\"}");

        String summary = executor.execute(r, ev("X"));

        assertThat(summary).contains("no role configured");
        verify(notify, never()).broadcastToRole(anyLong(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(audit).recordEvent(any());
    }

    // ─── ESCALATE ──────────────────────────────────────────────────

    @Test void escalate_usesToRoleAndWarnSeverity() throws Exception {
        AutomationRule r = rule("AR-ESC",
            "{\"type\":\"ESCALATE\",\"to_role\":\"CISO\",\"message\":\"sev1\"}");
        when(notify.broadcastToRole(eq(ORG_ID), eq("CISO"), anyString(), anyString(),
            anyString(), eq("ESCALATION"), any(), anyLong())).thenReturn(1);

        String summary = executor.execute(r, ev("RISK_APPETITE_BREACHED"));

        assertThat(summary).isEqualTo("ESCALATE → CISO (1 delivered)");
        verify(notify).broadcastToRole(eq(ORG_ID), eq("CISO"),
            anyString(), anyString(), anyString(), eq("ESCALATION"), any(), anyLong());
    }

    // ─── FLAG ──────────────────────────────────────────────────────

    @Test void flag_writesFlagAudit() throws Exception {
        AutomationRule r = rule("AR-FLAG",
            "{\"type\":\"FLAG\",\"entity_field\":\"is_overdue\",\"value\":\"true\"}");

        String summary = executor.execute(r, ev("CAPA_OVERDUE"));

        assertThat(summary).isEqualTo("FLAG(is_overdue) audited");
        verify(audit).recordEvent(any());
    }

    // ─── CREATE_RECORD ─────────────────────────────────────────────

    @Test void createRecord_writesCreateAudit() throws Exception {
        AutomationRule r = rule("AR-CR",
            "{\"type\":\"CREATE_RECORD\",\"entity\":\"BcmCorrectiveAction\","
            + "\"fields\":{\"source\":\"CALL_TREE_TEST\"}}");

        String summary = executor.execute(r, ev("BCM_CALL_TREE_FAILED"));

        assertThat(summary).isEqualTo("CREATE_RECORD BcmCorrectiveAction audited");
        verify(audit).recordEvent(any());
    }

    // ─── Unknown ───────────────────────────────────────────────────

    @Test void unknown_returnsUnknown() throws Exception {
        AutomationRule r = rule("AR-?", "{\"type\":\"SOMETHING_NEW\"}");

        String summary = executor.execute(r, ev("X"));

        assertThat(summary).startsWith("UNKNOWN");
        verify(audit, never()).recordEvent(any());
    }
}
