package com.tss.grc.module.platform.automation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.entity.AutomationRule;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executes the {@code action_json} of an {@link AutomationRule}.
 *
 * <p>F8 ships a thin "dispatcher" implementation that:
 * <ul>
 *   <li>Handles the AUDIT action fully (writes to platform.audit_log).</li>
 *   <li>Records every other action type (CREATE_RECORD, ESCALATE,
 *       NOTIFY, FLAG) as an audit-log row with the action payload,
 *       so QA can prove the rule fired and review what it
 *       <i>would</i> do.</li>
 *   <li>Returns a short summary string that the engine stores on
 *       the AutomationRuleRun.</li>
 * </ul>
 *
 * <p>Concrete side-effects (actually creating the DPIA record,
 * actually sending the notification) light up as each consuming
 * module subscribes to its own typed event from the rule run. That
 * staging is intentional — it lets us ship the rules engine end-
 * to-end now without touching every module's service layer in one
 * mega-PR.
 */
@Service
@RequiredArgsConstructor
public class AutomationActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutomationActionExecutor.class);
    private static final ObjectMapper M = new ObjectMapper();

    private final AuditLogService audit;

    public String execute(AutomationRule rule, DomainEvent event) throws Exception {
        JsonNode action = M.readTree(rule.getActionJson());
        String type = action.path("type").asText("");
        switch (type) {
            case "AUDIT" -> {
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
            case "CREATE_RECORD" -> {
                String entity = action.path("entity").asText("?");
                log.info("[automation] (stub) CREATE_RECORD {} for rule {}", entity, rule.getRuleCode());
                return "CREATE_RECORD " + entity + " (stub — module wiring pending)";
            }
            case "ESCALATE" -> {
                String toRole = action.path("to_role").asText("?");
                return "ESCALATE → " + toRole + " (stub — notification wiring pending)";
            }
            case "NOTIFY" -> {
                String role = action.path("role").asText("?");
                return "NOTIFY " + role + " (stub — notification wiring pending)";
            }
            case "FLAG" -> {
                String field = action.path("entity_field").asText("?");
                return "FLAG " + field + " (stub — module wiring pending)";
            }
            default -> {
                log.warn("[automation] unknown action type '{}' on rule {}", type, rule.getRuleCode());
                return "UNKNOWN action type: " + type;
            }
        }
    }
}
