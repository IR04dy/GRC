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
 *   <li><b>NOTIFY</b> — fans a notification out to every active user holding the role
 *       named in {@code action.role}. Writes one audit row for the rule fire and one
 *       notification per recipient (capped at 50). Falls back to an audit-only entry
 *       if no users hold the role — the rule fire is still inspectable.</li>
 *   <li><b>ESCALATE</b> — same fan-out as NOTIFY but with severity HIGH on the
 *       notification and severity WARN on the audit row. Uses {@code action.to_role}.</li>
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
            .sourceModule("automation")
            .correlationId(event.correlationId())
            .message(message));
        return "AUDIT(" + severity + ") emitted";
    }

    // ─── NOTIFY / ESCALATE ────────────────────────────────────────────

    /**
     * NOTIFY and ESCALATE share the same fan-out logic. ESCALATE bumps severity
     * (so the recipient inbox sorts it higher) and uses the {@code to_role} field
     * instead of {@code role}, matching the conventional naming in the seed rules.
     */
    private String handleNotify(AutomationRule rule, DomainEvent event, JsonNode action,
                                boolean escalate) {
        String role = escalate
            ? action.path("to_role").asText(action.path("role").asText(""))
            : action.path("role").asText("");

        if (role.isBlank()) {
            audit.recordEvent(new AuditLogService.AuditEventBuilder()
                .actionType("RULE_FIRED")
                .severity("WARN")
                .entityCode(rule.getRuleCode())
                .sourceModule("automation")
                .correlationId(event.correlationId())
                .message((escalate ? "ESCALATE" : "NOTIFY") + " rule " + rule.getRuleCode()
                    + " has no role configured"));
            return (escalate ? "ESCALATE" : "NOTIFY") + " (no role configured — audit-only)";
        }

        String titleEn = action.path("title").asText(
            (escalate ? "Escalation: " : "Notification: ") + rule.getRuleCode());
        String titleAr = action.path("title_ar").asText(
            (escalate ? "تصعيد: " : "إشعار: ") + rule.getRuleCode());
        String messageEn = buildMessage(rule, event, action);
        String notificationType = escalate ? "ESCALATION" : "NOTIFICATION";

        int delivered = notify.broadcastToRole(
            event.organisationId(), role,
            titleEn, titleAr, messageEn,
            notificationType,
            event.entityType(), event.entityId());

        // One audit row for the rule fire — keeps the engine's audit trail consistent
        // whether or not anyone held the role.
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("RULE_FIRED")
            .severity(escalate ? "WARN" : "INFO")
            .entityType(event.entityType())
            .entityId(event.entityId())
            .entityCode(rule.getRuleCode())
            .sourceModule("automation")
            .correlationId(event.correlationId())
            .message((escalate ? "ESCALATE → " : "NOTIFY → ") + role
                + " (" + delivered + " recipient" + (delivered == 1 ? "" : "s") + ")"));

        return (escalate ? "ESCALATE" : "NOTIFY") + " → " + role
            + " (" + delivered + " delivered)";
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
            .sourceModule("automation")
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
                .sourceModule("automation")
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
                .sourceModule("automation")
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
                .sourceModule("automation")
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
