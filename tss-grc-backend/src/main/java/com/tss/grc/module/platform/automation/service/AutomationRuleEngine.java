package com.tss.grc.module.platform.automation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.entity.AutomationRuleRun;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRepository;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRunRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Automation Rules engine — Framework Intersection F8.
 *
 * <p>Listens to {@link DomainEvent} broadcasts from any module,
 * finds enabled rules whose {@code triggerEvent} matches, evaluates
 * each rule's condition against the event payload, and fires the
 * matching ones. Every fire is recorded in
 * {@link AutomationRuleRun}; every fire also gets an audit-log row
 * (severity INFO for SUCCESS, WARN for SKIPPED with a mismatch
 * note, ERROR for FAILED with the stack message).
 *
 * <p>This slice ships the engine + dispatch only — concrete action
 * implementations (CREATE_RECORD, ESCALATE, NOTIFY, FLAG, AUDIT)
 * are routed through {@link AutomationActionExecutor}, which is a
 * thin stub for everything except AUDIT until each consuming
 * module wires up its own action handler. That keeps F8
 * shippable on its own — the rules become inspectable and
 * configurable today, the side-effects light up as modules adopt.
 */
@Service
@RequiredArgsConstructor
public class AutomationRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(AutomationRuleEngine.class);
    private static final ObjectMapper M = new ObjectMapper();

    private final AutomationRuleRepository rules;
    private final AutomationRuleRunRepository runs;
    private final AuditLogService audit;
    private final AutomationActionExecutor actions;

    /**
     * Subscribe to every DomainEvent. {@code @Async} is intentional:
     * rules must never slow down the originating service. Failures
     * are caught + logged + recorded as FAILED runs, never re-thrown.
     */
    @Async
    @EventListener
    @Transactional
    public void onDomainEvent(DomainEvent event) {
        if (event.organisationId() == null) {
            log.warn("[automation] event without organisation_id: {}", event);
            return;
        }
        List<AutomationRule> matching = rules
            .findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
                event.organisationId(), event.triggerEvent());
        if (matching.isEmpty()) return;

        for (AutomationRule r : matching) {
            try {
                if (!conditionMatches(r.getConditionJson(), event.payload())) {
                    recordRun(r, event, "SKIPPED", "Condition did not match", null);
                    continue;
                }
                String summary = actions.execute(r, event);
                recordRun(r, event, "SUCCESS", null, summary);

                r.setLastFiredAt(LocalDateTime.now());
                r.setFireCount(r.getFireCount() + 1);
                rules.save(r);

                if (Boolean.TRUE.equals(r.getAuditRequired())) {
                    audit.recordEvent(new AuditLogService.AuditEventBuilder()
                        .actionType("RULE_FIRED")
                        .entityType(event.entityType())
                        .entityId(event.entityId())
                        .entityCode(r.getRuleCode())
                        .sourceModule("automation")
                        .correlationId(event.correlationId())
                        .message("Rule " + r.getRuleCode() + " fired on " + event.triggerEvent()
                                 + (summary == null ? "" : " — " + summary)));
                }
            } catch (Exception ex) {
                log.error("[automation] rule {} failed on {}: {}", r.getRuleCode(),
                    event.triggerEvent(), ex.getMessage(), ex);
                recordRun(r, event, "FAILED", ex.getMessage(), null);
                audit.recordEvent(new AuditLogService.AuditEventBuilder()
                    .actionType("RULE_FIRED")
                    .severity("ERROR")
                    .entityCode(r.getRuleCode())
                    .sourceModule("automation")
                    .correlationId(event.correlationId())
                    .message("Rule " + r.getRuleCode() + " failed: " + ex.getMessage()));
            }
        }
    }

    // ─── Condition evaluation ─────────────────────────────────────

    /**
     * Minimal condition matcher. Today supports:
     *   - null / empty condition → always true.
     *   - JSON object → every key must equal-match the payload value
     *     (string compare, case-sensitive; "true"/"false" coerce to
     *     boolean; numbers compared as doubles).
     *
     * Richer operators (>, <, contains, regex) can come later
     * without changing the call site.
     */
    boolean conditionMatches(String conditionJson, Map<String, Object> payload) {
        if (conditionJson == null || conditionJson.isBlank()) return true;
        try {
            JsonNode root = M.readTree(conditionJson);
            if (!root.isObject()) return true;   // unsupported shape → fail-open

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                Object actual = payload == null ? null : payload.get(f.getKey());
                if (!matches(f.getValue(), actual)) return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[automation] bad condition_json '{}': {}", conditionJson, e.getMessage());
            return false;
        }
    }

    private static boolean matches(JsonNode expected, Object actual) {
        if (expected.isBoolean()) return Boolean.valueOf(expected.asBoolean()).equals(toBool(actual));
        if (expected.isNumber())  return Double.compare(expected.asDouble(), toDouble(actual)) == 0;
        if (expected.isTextual()) return Objects.equals(expected.asText(), String.valueOf(actual));
        return Objects.equals(expected.asText(), String.valueOf(actual));
    }

    private static Boolean toBool(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return Boolean.TRUE;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return Boolean.FALSE;
        return null;
    }

    private static double toDouble(Object v) {
        if (v == null) return Double.NaN;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (NumberFormatException e) { return Double.NaN; }
    }

    // ─── Run record ───────────────────────────────────────────────

    private void recordRun(AutomationRule rule, DomainEvent event,
                           String outcome, String error, String summary) {
        AutomationRuleRun r = new AutomationRuleRun();
        r.setOrganisationId(rule.getOrganisationId());
        r.setRuleId(rule.getId());
        r.setTriggerEvent(event.triggerEvent());
        r.setEntityType(event.entityType());
        r.setEntityId(event.entityId());
        r.setCorrelationId(event.correlationId());
        r.setOutcome(outcome);
        r.setErrorMessage(error);
        r.setResultSummary(summary);
        runs.save(r);
    }
}
