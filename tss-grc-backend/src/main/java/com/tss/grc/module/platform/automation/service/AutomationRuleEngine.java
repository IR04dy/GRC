package com.tss.grc.module.platform.automation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.entity.AutomationRuleRun;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRepository;
import com.tss.grc.module.platform.automation.repository.AutomationRuleRunRepository;
import com.tss.grc.common.tenant.TenantContext;
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
     *
     * <p>⚠⚠ F2-319 — the tenant is anchored here, at the <b>thread boundary</b>, and that is the
     * whole point of doing it in this method rather than at each writer.
     *
     * <p>{@code @Async} plus {@code @EnableAsync} on {@code GrcApplication} means this runs on a
     * <b>pool thread</b>, and {@code TenantContext} is a {@code ThreadLocal}. So
     * {@code AuditLogService.recordEvent}'s {@code org != null ? org : 1L} fallback applied to
     * <b>every</b> audit row this engine and {@link AutomationActionExecutor} write — nine sites —
     * <b>unconditionally</b>. 🔑 <b>Unlike [[F2-317]] and [[F2-318]], where the tenant was missing
     * because nobody set it, here no publisher could ever have helped: the ThreadLocal does not cross
     * the async boundary.</b> That is what makes one anchor at the entry point the right fix rather
     * than nine.
     *
     * <p>🔑 The tell sat three lines from the defect: {@link #recordRun} does
     * {@code r.setOrganisationId(rule.getOrganisationId())} explicitly while the
     * {@code audit.recordEvent} beside it took the fallback — the run row org-correct, the audit row
     * not. Fifth instance of that asymmetry in this family.
     *
     * <p>Latent when found: all 673 automation runs in dev and 217 in prod are organisation 1, so no
     * mismatch is visible. The ten organisation-2 {@code RULE_FIRED} rows are seeded (four share one
     * timestamp to the second, and there are <b>zero</b> organisation-2 runs), so the thirteen rules
     * each in organisations 2 and 3 have never fired. The observable, when one does, is that its run
     * row lands in its own tenant and its audit row does not — the two tables disagreeing.
     *
     * <p>⚠ The ambient value is restored in a {@code finally}, and here that matters more than in
     * F2-317: a <b>pool thread is reused</b>, so a tenant left set would leak into the next event's
     * processing. On a pool thread the ambient value is null, so restoring is clearing — but writing
     * it as a restore keeps the idiom identical at all four sites in this family.
     */
    @Async
    @EventListener
    @Transactional
    public void onDomainEvent(DomainEvent event) {
        if (event.organisationId() == null) {
            log.warn("[automation] event without organisation_id: {}", event);
            return;
        }
        Long ambient = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(event.organisationId());
        try {
            dispatch(event);
        } finally {
            TenantContext.setCurrentTenant(ambient);
        }
    }

    private void dispatch(DomainEvent event) {
        List<AutomationRule> matching = rules
            .findByOrganisationIdAndTriggerEventAndEnabledTrueOrderByPriorityAsc(
                event.organisationId(), event.triggerEvent());
        if (matching.isEmpty()) return;

        for (AutomationRule r : matching) {
            try {
                ConditionVerdict verdict = evaluateCondition(r.getConditionJson(), event.payload());
                if (!verdict.matched()) {
                    // F2-118 — the two reasons for not firing are recorded differently now. They
                    // used to be the same string, so 293 run rows could not tell "considered the
                    // event and declined" from "cannot express its own condition", and the run log
                    // read as a working engine while 43 rules were inert.
                    if (verdict.wasEvaluated()) {
                        recordRun(r, event, "SKIPPED", "Condition did not match", null);
                    } else {
                        log.warn("[automation] rule {} cannot evaluate its condition on {}: {}",
                            r.getRuleCode(), event.triggerEvent(), verdict.unevaluable());
                        recordRun(r, event, "SKIPPED",
                            "Condition could not be evaluated: " + verdict.unevaluable(), null);
                    }
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
     * The verdict on one rule's condition — <b>three-valued on purpose</b> (F2-118).
     *
     * <p>"Declined the event" and "could not express its own condition" used to be the same answer,
     * and that is what hid the defect for months: every broken rule recorded
     * {@code SKIPPED · "Condition did not match"}, byte-identical to a correctly-evaluated negative,
     * across 293 run rows. Anyone auditing the log saw a functioning engine. So the engine now knows
     * the difference and says so in the run record.
     *
     * @param unevaluable {@code null} when the condition was genuinely evaluated; otherwise why it
     *                    could not be, which is recorded and always fails <b>closed</b>
     */
    record ConditionVerdict(boolean matched, String unevaluable) {
        static final ConditionVerdict MATCHED = new ConditionVerdict(true, null);
        static final ConditionVerdict DECLINED = new ConditionVerdict(false, null);

        static ConditionVerdict cannotEvaluate(String why) {
            return new ConditionVerdict(false, why);
        }

        boolean wasEvaluated() {
            return unevaluable == null;
        }
    }

    /**
     * Condition matcher. Supports:
     * <ul>
     *   <li>null / empty condition → always true (134 of 214 rules; 112 are {@code {}} and 22 NULL).</li>
     *   <li>a scalar value → equal-match against the payload (case-sensitive strings,
     *       {@code "true"}/{@code "false"} coerced to boolean, numbers compared as doubles).</li>
     *   <li>an <b>operator object</b> — {@code {"eq":…}}, {@code ne}, {@code gt}/{@code >},
     *       {@code gte}/{@code >=}, {@code lt}/{@code <}, {@code lte}/{@code <=}, {@code in}.
     *       Several operators in one object are AND-ed.</li>
     * </ul>
     *
     * <p><b>F2-118 — operator objects used to be unmatchable, and 43 enabled rules used them.</b>
     * The old matcher tested only {@code isBoolean}/{@code isNumber}/{@code isTextual} and fell back
     * to {@code Objects.equals(expected.asText(), String.valueOf(actual))}. An {@code ObjectNode}
     * fails all three tests, and {@code ObjectNode.asText()} returns the <b>empty string</b> — so the
     * comparison was {@code "" == "HIGH"} and the rule could never match, for any payload. Measured
     * on 2026-08-05 and re-measured before this fix: 214 rules, 43 operator-object, **all enabled,
     * zero ever fired**, using exactly {@code eq}(33) {@code in}(3) {@code >}(3) {@code gte}(2)
     * {@code gt}(2) {@code ne}(2) {@code lt}(1). Among them: fraud loss > SAR 1M, confirmed loss >
     * SAR 500k, >100 affected customers, NCA and CMA inspection notices, and material SNCI > SAR
     * 500k — a threshold rule that skips looks correct while being unconditional, so AR-255 declining
     * an 18,500 SAR event was the right answer for the wrong reason, and 750,000 would have declined
     * identically.
     *
     * <p>{@code lte} and {@code <=}/{@code <} are implemented although no current rule uses them:
     * the set is the closure of a comparison vocabulary, and half a vocabulary is how the next author
     * writes an unmatchable rule.
     *
     * <p><b>Unknown operators fail closed</b> — the one thing the old code got right, and the reason
     * this returns a verdict rather than a boolean: a condition the engine cannot understand must not
     * fire, and must not be recorded as though it had been considered and declined.
     */
    boolean conditionMatches(String conditionJson, Map<String, Object> payload) {
        return evaluateCondition(conditionJson, payload).matched();
    }

    ConditionVerdict evaluateCondition(String conditionJson, Map<String, Object> payload) {
        if (conditionJson == null || conditionJson.isBlank()) return ConditionVerdict.MATCHED;
        try {
            JsonNode root = M.readTree(conditionJson);
            if (!root.isObject()) return ConditionVerdict.MATCHED;   // unsupported shape → fail-open

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                Object actual = payload == null ? null : payload.get(f.getKey());
                ConditionVerdict v = matchField(f.getKey(), f.getValue(), actual);
                if (!v.matched()) return v;
            }
            return ConditionVerdict.MATCHED;
        } catch (Exception e) {
            log.warn("[automation] bad condition_json '{}': {}", conditionJson, e.getMessage());
            return ConditionVerdict.cannotEvaluate("condition_json is not valid JSON");
        }
    }

    private static ConditionVerdict matchField(String field, JsonNode expected, Object actual) {
        if (expected.isObject()) return matchOperators(field, expected, actual);
        return matchScalar(expected, actual) ? ConditionVerdict.MATCHED : ConditionVerdict.DECLINED;
    }

    /** Every operator in the object must hold (AND). An unrecognised one is not evaluable. */
    private static ConditionVerdict matchOperators(String field, JsonNode ops, Object actual) {
        if (ops.isEmpty()) {
            return ConditionVerdict.cannotEvaluate("'" + field + "' has an empty operator object");
        }
        Iterator<Map.Entry<String, JsonNode>> it = ops.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> op = it.next();
            String name = op.getKey();
            JsonNode arg = op.getValue();
            Boolean held = switch (name) {
                case "eq"            -> matchScalar(arg, actual);
                case "ne"            -> !matchScalar(arg, actual);
                case "gt",  ">"      -> compareNumeric(arg, actual, c -> c > 0);
                case "gte", ">="     -> compareNumeric(arg, actual, c -> c >= 0);
                case "lt",  "<"      -> compareNumeric(arg, actual, c -> c < 0);
                case "lte", "<="     -> compareNumeric(arg, actual, c -> c <= 0);
                case "in"            -> containedIn(arg, actual);
                default              -> null;
            };
            if (held == null) {
                return ConditionVerdict.cannotEvaluate(
                    "unknown operator '" + name + "' on field '" + field + "'");
            }
            if (!held) return ConditionVerdict.DECLINED;
        }
        return ConditionVerdict.MATCHED;
    }

    /**
     * Numeric comparison of the ACTUAL against the EXPECTED, i.e. {@code gt} means
     * <i>actual &gt; expected</i> — which is how every rule reads ("loss > SAR 1M"). Goes through
     * {@link #toDouble} so a decimal held as a string still compares, and a non-numeric value on
     * either side declines rather than throwing: a threshold cannot be applied to a word.
     */
    private static boolean compareNumeric(JsonNode expected, Object actual,
                                          java.util.function.IntPredicate holds) {
        double want = expected.isNumber() ? expected.asDouble() : toDouble(expected.asText());
        double have = toDouble(actual);
        if (Double.isNaN(want) || Double.isNaN(have)) return false;
        return holds.test(Double.compare(have, want));
    }

    /** {@code in} against a JSON array; a non-array argument is a malformed rule, not a decline. */
    private static Boolean containedIn(JsonNode arg, Object actual) {
        if (!arg.isArray()) return null;
        for (JsonNode candidate : arg) {
            if (matchScalar(candidate, actual)) return true;
        }
        return false;
    }

    private static boolean matchScalar(JsonNode expected, Object actual) {
        if (expected.isBoolean()) return Boolean.valueOf(expected.asBoolean()).equals(toBool(actual));
        if (expected.isNumber())  return Double.compare(expected.asDouble(), toDouble(actual)) == 0;
        if (expected.isNull())    return actual == null;
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
