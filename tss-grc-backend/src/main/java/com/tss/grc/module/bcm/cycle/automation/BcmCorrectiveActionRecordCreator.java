package com.tss.grc.module.bcm.cycle.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmCorrectiveAction;
import com.tss.grc.module.bcm.cycle.service.BcmRemediationService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.automation.service.RecordCreator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * F8 {@link RecordCreator} for the {@code BcmCorrectiveAction} entity.
 *
 * <p>Wires the audit Tier-1 fix "DR test fail → auto-CAPA" (spec §7 rule 9) and
 * closes the audit's complaint that the CREATE_RECORD rule action was stubbed.
 *
 * <h3>Rule action shape</h3>
 * <pre>{@code
 * {
 *   "type": "CREATE_RECORD",
 *   "entity": "BcmCorrectiveAction",
 *   "fields": {
 *     "priority": "HIGH",
 *     "title_template": "DR test failed: %plan_code%",
 *     "source_hint": "DR_TEST"
 *   }
 * }
 * }</pre>
 *
 * <p>{@code title_template} supports {@code %key%} placeholders that get
 * substituted from the {@link DomainEvent#payload()} map. If no template is
 * supplied, the creator builds a default title from the trigger event +
 * entity reference.
 *
 * <h3>Tenant context</h3>
 * <p>The F8 engine runs {@code @Async}, so the originating request's
 * {@code TenantContext} thread-local is not visible to this creator. We
 * therefore explicitly set the tenant from {@code event.organisationId()}
 * before delegating to {@code BcmRemediationService.createCapa}, then clear
 * it afterwards so we don't leak across async boundaries.
 */
@Component
@RequiredArgsConstructor
public class BcmCorrectiveActionRecordCreator implements RecordCreator {

    private static final Logger log = LoggerFactory.getLogger(BcmCorrectiveActionRecordCreator.class);

    private final BcmRemediationService remediation;

    @Override
    public String entityType() {
        return "BcmCorrectiveAction";
    }

    @Override
    public String create(JsonNode action, DomainEvent event) {
        JsonNode fields = action.path("fields");
        String priority   = fields.path("priority").asText("MEDIUM");
        String template   = fields.path("title_template").asText("");
        Integer dueDays   = fields.path("due_in_days").isMissingNode() ? null : fields.path("due_in_days").asInt();

        String title = template.isBlank()
            ? defaultTitle(event)
            : substitutePayload(template, event);

        BcmCorrectiveAction body = new BcmCorrectiveAction();
        body.setTitleEn(title);
        body.setPriority(priority);
        body.setActionPlan("Auto-created by automation rule on event " + event.triggerEvent()
            + (event.entityType() == null ? "" : " from " + event.entityType()
                + (event.entityId() == null ? "" : "#" + event.entityId())));
        if (dueDays != null) {
            body.setDueDate(LocalDate.now().plusDays(dueDays));
        }
        // BcmRemediationService.createCapa requires the tenant context — set it
        // explicitly because we're on an async event-handler thread.
        Long prevTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(event.organisationId());
        try {
            BcmCorrectiveAction saved = remediation.createCapa(body);
            log.info("[automation] CREATE_RECORD BcmCorrectiveAction#{} for event {} ({})",
                saved.getId(), event.triggerEvent(), event.entityType());
            return "Created BcmCorrectiveAction#" + saved.getId() + " (" + title + ")";
        } finally {
            if (prevTenant != null) TenantContext.setCurrentTenant(prevTenant);
            else                    TenantContext.clear();
        }
    }

    private static String defaultTitle(DomainEvent event) {
        StringBuilder b = new StringBuilder("CAPA for ").append(event.triggerEvent());
        if (event.entityType() != null) {
            b.append(" — ").append(event.entityType());
            if (event.entityId() != null) b.append("#").append(event.entityId());
        }
        return b.toString();
    }

    /**
     * Replace {@code %key%} placeholders in the template with payload values.
     * Unknown keys are left as-is (the regulator may want to know what was missing).
     */
    private static String substitutePayload(String template, DomainEvent event) {
        if (event.payload() == null || event.payload().isEmpty()) return template;
        String out = template;
        for (var entry : event.payload().entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return out;
    }
}
