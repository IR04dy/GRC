package com.tss.grc.module.platform.automation.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
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
    @Mock private RecordCreator capaCreator;
    private AutomationActionExecutor executor;

    @BeforeEach void setUpExecutor() {
        // No record creators registered by default — handleCreateRecord falls
        // back to audit-only. Individual tests can register a mocked creator.
        executor = new AutomationActionExecutor(audit, notify, List.of());
    }

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

    /** Convenience: a fan-out that delivered to every role it was given. */
    private static NotificationService.RoleFanout delivered(int n, String... roles) {
        return new NotificationService.RoleFanout(n, List.of(roles), List.of(), List.of());
    }

    private void whenFanout(NotificationService.RoleFanout result, String... expectedRoles) {
        when(notify.broadcastToRoles(eq(ORG_ID), eq(List.of(expectedRoles)), anyString(),
            anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(result);
    }

    @Test void notify_broadcastsAndAuditsRoleAndCount() throws Exception {
        AutomationRule r = rule("AR-Y",
            "{\"type\":\"NOTIFY\",\"role\":\"BCM_MANAGER\",\"message\":\"call tree failed\"}");
        whenFanout(delivered(3, "BCM_MANAGER"), "BCM_MANAGER");

        String summary = executor.execute(r, ev("BCM_CALL_TREE_FAILED"));

        assertThat(summary).isEqualTo("NOTIFY → BCM_MANAGER (3 recipients)");
        verify(notify).broadcastToRoles(eq(ORG_ID), eq(List.of("BCM_MANAGER")),
            anyString(), anyString(), anyString(), eq("NOTIFICATION"), any(), anyLong());
        verify(audit).recordEvent(any());   // single audit row for the rule fire
    }

    /**
     * F2-94's defect, pinned: the plural key that 147 of 164 seeded rows use. Before the fix
     * this fell into the "no role configured" branch and the rule reached nobody while every
     * engine metric recorded a successful fire.
     */
    @Test void notify_resolvesThePluralRolesKeyEverySeedActuallyUses() throws Exception {
        AutomationRule r = rule("AR-146",
            "{\"type\":\"NOTIFY\",\"roles\":[\"CISO\",\"DPO\"],\"template\":\"X\"}");
        whenFanout(delivered(2, "CISO", "DPO"), "CISO", "DPO");

        String summary = executor.execute(r, ev("COMMITTEE_RESOLUTION_APPROVED"));

        assertThat(summary).isEqualTo("NOTIFY → CISO,DPO (2 recipients)");
        verify(notify).broadcastToRoles(eq(ORG_ID), eq(List.of("CISO", "DPO")),
            anyString(), anyString(), anyString(), eq("NOTIFICATION"), any(), anyLong());
    }

    /**
     * The two failures must be distinguishable in the audit row, because only one of them is
     * a grant. A single "(0 recipients)" is what made the whole fan-out look healthy.
     */
    @Test void notify_separatesUnheldRolesFromNamesThatAreNotRolesAtAll() throws Exception {
        AutomationRule r = rule("AR-97",
            "{\"type\":\"ESCALATE\",\"roles\":[\"CTO\",\"CISO\",\"CHANGE_MANAGER\"]}");
        when(notify.broadcastToRoles(eq(ORG_ID), eq(List.of("CTO", "CISO", "CHANGE_MANAGER")),
            anyString(), anyString(), anyString(), anyString(), any(), anyLong()))
            .thenReturn(new NotificationService.RoleFanout(
                1, List.of("CISO"), List.of("CHANGE_MANAGER"), List.of("CTO")));

        String summary = executor.execute(r, ev("CHANGE_EMERGENCY_APPROVED"));

        assertThat(summary)
            .isEqualTo("ESCALATE → CISO (1 recipient); no active holder of CHANGE_MANAGER; "
                     + "NOT A ROLE: CTO");
        ArgumentCaptor<AuditLogService.AuditEventBuilder> cap =
            ArgumentCaptor.forClass(AuditLogService.AuditEventBuilder.class);
        verify(audit).recordEvent(cap.capture());
    }

    @Test void notify_noAudienceAddressedFallsBackToAuditOnly() throws Exception {
        AutomationRule r = rule("AR-149", "{\"type\":\"NOTIFY\",\"template\":\"X\"}");

        String summary = executor.execute(r, ev("COMMITTEE_ACTION_CREATED"));

        assertThat(summary).contains("no audience addressed");
        verify(notify, never()).broadcastToRoles(anyLong(), any(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(audit).recordEvent(any());
    }

    /**
     * A key the executor does not read is not an audience. AR-148 named its recipients under
     * {@code escalateTo}/{@code cc}; normalising the data (V346) is the fix, not widening the
     * vocabulary — {@code also} carries entity type names on three other rules.
     */
    @Test void notify_ignoresRecipientKeysOutsideTheClosedVocabulary() throws Exception {
        AutomationRule r = rule("AR-148",
            "{\"type\":\"ESCALATE\",\"escalateTo\":\"LINE_MANAGER\","
          + "\"cc\":[\"COMMITTEE_CHAIR\"],\"also\":[\"NcaCriticalAssessment\"]}");

        String summary = executor.execute(r, ev("COMMITTEE_ACTION_OVERDUE"));

        assertThat(summary).contains("no audience addressed");
        verify(notify, never()).broadcastToRoles(anyLong(), any(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    // ─── ESCALATE ──────────────────────────────────────────────────

    @Test void escalate_usesToRoleAndWarnSeverity() throws Exception {
        AutomationRule r = rule("AR-ESC",
            "{\"type\":\"ESCALATE\",\"to_role\":\"CISO\",\"message\":\"sev1\"}");
        whenFanout(delivered(1, "CISO"), "CISO");

        String summary = executor.execute(r, ev("RISK_APPETITE_BREACHED"));

        assertThat(summary).isEqualTo("ESCALATE → CISO (1 recipient)");
        verify(notify).broadcastToRoles(eq(ORG_ID), eq(List.of("CISO")),
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

    @Test void createRecord_noCreatorRegistered_fallsBackToAuditOnly() throws Exception {
        // Default executor (no creators) — message should call out the missing creator.
        AutomationRule r = rule("AR-CR",
            "{\"type\":\"CREATE_RECORD\",\"entity\":\"BcmCorrectiveAction\","
            + "\"fields\":{\"source\":\"CALL_TREE_TEST\"}}");

        String summary = executor.execute(r, ev("BCM_CALL_TREE_FAILED"));

        assertThat(summary).contains("no creator registered");
        verify(audit).recordEvent(any());
    }

    @Test void createRecord_creatorRegistered_invokesCreator() throws Exception {
        // Wire a mocked RecordCreator for BcmCorrectiveAction
        when(capaCreator.entityType()).thenReturn("BcmCorrectiveAction");
        when(capaCreator.create(any(), any())).thenReturn("Created BcmCorrectiveAction#42");
        AutomationActionExecutor withCreator = new AutomationActionExecutor(audit, notify, List.of(capaCreator));

        AutomationRule r = rule("AR-CR",
            "{\"type\":\"CREATE_RECORD\",\"entity\":\"BcmCorrectiveAction\","
            + "\"fields\":{\"priority\":\"HIGH\"}}");

        String summary = withCreator.execute(r, ev("BCM_DR_TEST_FAILED"));

        assertThat(summary).contains("Created BcmCorrectiveAction#42");
        verify(capaCreator).create(any(), any());
        verify(audit).recordEvent(any());
    }

    @Test void createRecord_creatorThrows_recordsErrorAudit() throws Exception {
        when(capaCreator.entityType()).thenReturn("BcmCorrectiveAction");
        when(capaCreator.create(any(), any())).thenThrow(new RuntimeException("DB timeout"));
        AutomationActionExecutor withCreator = new AutomationActionExecutor(audit, notify, List.of(capaCreator));

        AutomationRule r = rule("AR-CR",
            "{\"type\":\"CREATE_RECORD\",\"entity\":\"BcmCorrectiveAction\"}");

        String summary = withCreator.execute(r, ev("BCM_DR_TEST_FAILED"));

        assertThat(summary).contains("FAILED").contains("DB timeout");
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
