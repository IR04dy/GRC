package com.tss.grc.module.platform.automation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes the {@code action_json} of an {@link AutomationRule}.
 *
 * <h3>Action types</h3>
 * <ul>
 *   <li><b>AUDIT</b> — writes an audit-log row with the configured severity + message.</li>
 *   <li><b>NOTIFY</b> — fans a notification out to every active user holding <em>any</em>
 *       role named under {@link #RECIPIENT_KEYS}, recipients de-duplicated. Writes one
 *       audit row for the rule fire and one notification per recipient (capped at 50).
 *       The audit row distinguishes delivered / unheld / not-a-role, because those have
 *       different remedies (F2-94).</li>
 *   <li><b>ESCALATE</b> — same fan-out as NOTIFY but with severity HIGH on the
 *       notification and WARN on the audit row (ERROR if a name is not a role).</li>
 *   <li><b>FLAG</b> — writes an audit row with action type {@code FLAG} that names
 *       {@code action.entity_field} so a follow-up scheduled job (per consuming
 *       module) can flip the field. Audit-only by design — flipping a column on an
 *       arbitrary entity from a generic dispatcher would require a giant entity
 *       registry; cheaper to let each module subscribe to {@link DomainEvent} and
 *       handle its own FLAG semantics.</li>
 *   <li><b>CREATE_RECORD</b> — writes an audit row with action type {@code CREATE_RECORD}
 *       and the full {@code action.fields} payload. Same rationale as FLAG: a generic
 *       dispatcher cannot validate every entity's required fields. The audit entry
 *       carries enough context for a per-module subscriber to materialise the record,
 *       or a human to do it manually.</li>
 * </ul>
 *
 * <p>The summary string returned from {@link #execute} is stored on the
 * AutomationRuleRun row so the audit-log review UI can show what each fire did
 * without joining back to the notification table.
 */
@Service
public class AutomationActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutomationActionExecutor.class);
    private static final ObjectMapper M = new ObjectMapper();

    /**
     * The <b>closed</b> set of {@code action_json} keys that may name a NOTIFY/ESCALATE
     * audience, in resolution order. This is the single definition — the executor reads
     * it and {@code AutomationRecipientKeyRatchetTest} scans every migration against it,
     * so a rule written with an eighth key fails the build instead of silently notifying
     * nobody.
     *
     * <p><b>F2-94.</b> The executor used to read only {@code role} and {@code to_role},
     * asserting in its own javadoc that this matched "the conventional naming in the seed
     * rules". Measured 2026-08-03: {@code roles} carried 147 of the 164 NOTIFY/ESCALATE
     * rows and was never read, so 127 of 140 rule codes — among them HIGH-risk emergency
     * change approval, expired policy, and declined attestation — fired, audited,
     * incremented {@code fire_count} and reached no one.
     *
     * <p><b>Why closed rather than generous.</b> The seeds had invented seven recipient-ish
     * keys. Reading all of them looks safer and is not: {@code also} carries role names on
     * two rules ({@code CONTROL_OWNER}) and <em>entity type</em> names on three
     * ({@code DRP}, {@code NcaCriticalAssessment}), so a generous union would broadcast to
     * a "role" named after an assessment. An unbounded vocabulary is the defect; reading
     * more of it is not the fix.
     */
    static final List<String> RECIPIENT_KEYS = List.of("roles", "role", "to_role");

    private final AuditLogService audit;
    private final NotificationService notify;

    /**
     * All {@link RecordCreator} beans on the classpath, keyed by their declared
     * {@link RecordCreator#entityType()}. Wired by Spring via the list constructor —
     * each consuming module ships its own creator(s) and they self-register at boot.
     * Empty (legacy audit-only) if no module has wired one for a given entity.
     */
    private final Map<String, RecordCreator> creatorsByType;

    @Autowired
    public AutomationActionExecutor(AuditLogService audit,
                                    NotificationService notify,
                                    List<RecordCreator> creators) {
        this.audit = audit;
        this.notify = notify;
        this.creatorsByType = creators.stream()
            .collect(Collectors.toMap(RecordCreator::entityType, c -> c, (a, b) -> a));
        log.info("[automation] AutomationActionExecutor ready with {} record creators: {}",
            creatorsByType.size(), creatorsByType.keySet());
    }

    public String execute(AutomationRule rule, DomainEvent event) throws Exception {
        JsonNode action = M.readTree(rule.getActionJson());
        String type = action.path("type").asText("");
        return switch (type) {
            case "AUDIT"         -> handleAudit(rule, event, action);
            case "NOTIFY"        -> handleNotify(rule, event, action, /*escalate*/ false);
            case "ESCALATE"      -> handleNotify(rule, event, action, /*escalate*/ true);
            case "FLAG"          -> handleFlag(rule, event, action);
            case "CREATE_RECORD" -> handleCreateRecord(rule, event, action);
            default -> {
                log.warn("[automation] unknown action type '{}' on rule {}", type, rule.getRuleCode());
                yield "UNKNOWN action type: " + type;
            }
        };
    }

    // ─── AUDIT ────────────────────────────────────────────────────────

    private String handleAudit(AutomationRule rule, DomainEvent event, JsonNode action) {
        String severity = action.path("severity").asText("INFO");
        String message  = action.path("message").asText("Rule " + rule.getRuleCode() + " fired");
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("RULE_FIRED")
            .severity(severity)
            .entityType(event.entityType())
            .entityId(event.entityId())
            .entityCode(rule.getRuleCode())
            .correlationId(event.correlationId())
            .message(message));
        return "AUDIT(" + severity + ") emitted";
    }

    // ─── NOTIFY / ESCALATE ────────────────────────────────────────────

    /**
     * Every {@code action_json} key in {@link #RECIPIENT_KEYS} that carries a value, in
     * order, flattened and de-duplicated. Each key may hold a scalar or an array — the
     * seeds do both — so both are accepted rather than making the shape a second way to
     * silently address nobody.
     *
     * <p>Package-private so the ratchet can assert that what the executor reads is exactly
     * what the migrations are checked against.
     */
    static List<String> recipientRoles(JsonNode action) {
        List<String> out = new ArrayList<>();
        for (String key : RECIPIENT_KEYS) {
            JsonNode node = action.path(key);
            if (node.isMissingNode() || node.isNull()) continue;
            if (node.isArray()) {
                for (JsonNode el : node) addRole(out, el.asText(""));
            } else {
                addRole(out, node.asText(""));
            }
        }
        return out;
    }

    private static void addRole(List<String> out, String raw) {
        if (raw == null) return;
        String role = raw.trim();
        if (role.isEmpty()) return;
        if (out.stream().noneMatch(r -> r.equalsIgnoreCase(role))) out.add(role);
    }

    /**
     * NOTIFY and ESCALATE share the same fan-out logic. ESCALATE bumps severity so the
     * recipient inbox sorts it higher; both resolve their audience through
     * {@link #RECIPIENT_KEYS}.
     *
     * <p><b>F2-94 — what changed and why the audit row now says more.</b> The old row read
     * {@code "ESCALATE → CISO (0 recipients)"} whether the role was unheld, misspelt, or
     * nonexistent, and {@code "has no role configured"} whenever the rule used the plural
     * key. Both are now separated, because the remedies are different and only one of them
     * is the operator's to apply: an <b>unheld</b> role needs a grant, an <b>unknown</b>
     * role needs the rule or the role catalogue fixed. Severity rises to ERROR for an
     * unknown role — it cannot come right on its own.
     */
    private String handleNotify(AutomationRule rule, DomainEvent event, JsonNode action,
                                boolean escalate) {
        String verb = escalate ? "ESCALATE" : "NOTIFY";
        List<String> roles = recipientRoles(action);

        if (roles.isEmpty()) {
            audit.recordEvent(new AuditLogService.AuditEventBuilder()
                .actionType("RULE_FIRED")
                .severity("WARN")
                .entityCode(rule.getRuleCode())
                .correlationId(event.correlationId())
                .message(verb + " rule " + rule.getRuleCode()
                    + " addresses no audience — expected one of " + RECIPIENT_KEYS
                    + " in action_json"));
            return verb + " (no audience addressed — audit-only)";
        }

        String titleEn = action.path("title").asText(
            (escalate ? "Escalation: " : "Notification: ") + rule.getRuleCode());
        String titleAr = action.path("title_ar").asText(
            (escalate ? "تصعيد: " : "إشعار: ") + rule.getRuleCode());
        String messageEn = buildMessage(rule, event, action);
        String notificationType = escalate ? "ESCALATION" : "NOTIFICATION";

        NotificationService.RoleFanout fanout = notify.broadcastToRoles(
            event.organisationId(), roles,
            titleEn, titleAr, messageEn,
            notificationType,
            event.entityType(), event.entityId());

        String outcome = describe(fanout);
        // One audit row for the rule fire — the engine's trail stays consistent whether or
        // not anyone was reached, but it no longer *reads* the same in both cases.
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("RULE_FIRED")
            .severity(fanout.hasConfigurationDefect() ? "ERROR" : escalate ? "WARN" : "INFO")
            .entityType(event.entityType())
            .entityId(event.entityId())
            .entityCode(rule.getRuleCode())
            .correlationId(event.correlationId())
            .message(verb + " → " + outcome));

        if (fanout.reachedNobody()) {
            log.warn("[automation] rule {} fired and reached nobody: {}",
                rule.getRuleCode(), outcome);
        }
        return verb + " → " + outcome;
    }

    /** Render a fan-out so the three outcomes are distinguishable in one line of audit. */
    private static String describe(NotificationService.RoleFanout f) {
        StringBuilder b = new StringBuilder();
        b.append(f.deliveredRoles().isEmpty() ? "nobody" : String.join(",", f.deliveredRoles()));
        b.append(" (").append(f.delivered())
         .append(" recipient").append(f.delivered() == 1 ? "" : "s").append(")");
        if (!f.unheldRoles().isEmpty()) {
            b.append("; no active holder of ").append(String.join(",", f.unheldRoles()));
        }
        if (!f.unknownRoles().isEmpty()) {
            b.append("; NOT A ROLE: ").append(String.join(",", f.unknownRoles()));
        }
        return b.toString();
    }

    // ─── FLAG ────────────────────────────────────────────────────────

    private String handleFlag(AutomationRule rule, DomainEvent event, JsonNode action) {
        String field = action.path("entity_field").asText("");
        String value = action.path("value").asText("");
        String payload = "field=" + (field.isBlank() ? "?" : field)
                       + (value.isBlank() ? "" : " value=" + value);
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("FLAG")
            .severity("WARN")
            .entityType(event.entityType())
            .entityId(event.entityId())
            .entityCode(rule.getRuleCode())
            .correlationId(event.correlationId())
            .message("FLAG " + payload
                + (event.entityType() == null ? "" : " on " + event.entityType()
                    + (event.entityId() == null ? "" : "#" + event.entityId()))));
        return "FLAG(" + (field.isBlank() ? "?" : field) + ") audited";
    }

    // ─── CREATE_RECORD ───────────────────────────────────────────────

    /**
     * Look up a registered {@link RecordCreator} for {@code action.entity} and
     * delegate. If no creator is wired (i.e. the consuming module hasn't
     * provided one yet), fall back to the original audit-only behaviour so
     * the rule fire is still inspectable.
     *
     * <p>Failures inside a creator are caught and recorded as an ERROR audit
     * row — they do not throw out of this method because that would mark the
     * whole rule run FAILED and miss the audit trail of what was attempted.
     */
    private String handleCreateRecord(AutomationRule rule, DomainEvent event, JsonNode action) {
        String entity = action.path("entity").asText("?");
        JsonNode fields = action.path("fields");
        String fieldsRepr = fields.isMissingNode() || fields.isNull()
            ? "{}"
            : fields.toString();

        RecordCreator creator = creatorsByType.get(entity);
        if (creator == null) {
            audit.recordEvent(new AuditLogService.AuditEventBuilder()
                .actionType("CREATE_RECORD")
                .severity("INFO")
                .entityType(entity)
                .entityCode(rule.getRuleCode())
                .correlationId(event.correlationId())
                .message("CREATE_RECORD " + entity + " — no creator registered (audit-only). fields=" + fieldsRepr));
            return "CREATE_RECORD " + entity + " (no creator registered — audit-only)";
        }

        try {
            String summary = creator.create(action, event);
            audit.recordEvent(new AuditLogService.AuditEventBuilder()
                .actionType("CREATE_RECORD")
                .severity("INFO")
                .entityType(entity)
                .entityCode(rule.getRuleCode())
                .correlationId(event.correlationId())
                .message("CREATE_RECORD " + entity + " — " + summary));
            return "CREATE_RECORD " + entity + " — " + summary;
        } catch (Exception ex) {
            log.error("[automation] CREATE_RECORD {} creator failed: {}", entity, ex.getMessage(), ex);
            audit.recordEvent(new AuditLogService.AuditEventBuilder()
                .actionType("CREATE_RECORD")
                .severity("ERROR")
                .entityType(entity)
                .entityCode(rule.getRuleCode())
                .correlationId(event.correlationId())
                .message("CREATE_RECORD " + entity + " FAILED: " + ex.getMessage()));
            return "CREATE_RECORD " + entity + " FAILED: " + ex.getMessage();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * Build a short human-readable message for NOTIFY / ESCALATE notifications.
     * Prefers an explicit {@code message} field on the rule action; otherwise
     * stitches together the trigger event, entity reference, and a flattened
     * peek at the payload so the recipient has enough context to act.
     */
    private static String buildMessage(AutomationRule rule, DomainEvent event, JsonNode action) {
        String explicit = action.path("message").asText("");
        if (!explicit.isBlank()) return explicit;

        StringBuilder b = new StringBuilder();
        b.append("Rule ").append(rule.getRuleCode()).append(" fired on ").append(event.triggerEvent());
        if (event.entityType() != null) {
            b.append(" — ").append(event.entityType());
            if (event.entityId() != null) b.append("#").append(event.entityId());
        }
        Map<String, Object> payload = event.payload();
        if (payload != null && !payload.isEmpty()) {
            b.append(" [");
            Iterator<Map.Entry<String, Object>> it = payload.entrySet().iterator();
            int i = 0;
            while (it.hasNext() && i++ < 4) {  // first 4 keys is enough — keep notification short
                Map.Entry<String, Object> e = it.next();
                if (i > 1) b.append(", ");
                b.append(e.getKey()).append("=").append(e.getValue());
            }
            if (payload.size() > 4) b.append(", …");
            b.append("]");
        }
        return b.toString();
    }
}
