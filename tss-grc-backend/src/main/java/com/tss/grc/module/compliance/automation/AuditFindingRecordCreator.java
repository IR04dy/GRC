package com.tss.grc.module.compliance.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.automation.service.RecordCreator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * F8 {@link RecordCreator} for the {@code AuditFinding} (CAPA / control-gap) entity.
 *
 * <p>Closes review item 20 — "create a gap when requested evidence is rejected or
 * missing". It complements the seeded rule <b>AR-17</b> ("evidence rejected →
 * reopen the related control gap", which {@code FLAG}s an <i>existing</i> gap to
 * OPEN): this creator <i>opens a new finding when none exists yet</i>.
 *
 * <h3>Rule action shape</h3>
 * <pre>{@code
 * {
 *   "type": "CREATE_RECORD",
 *   "entity": "AuditFinding",
 *   "fields": {
 *     "severity": "HIGH",
 *     "title_template": "Evidence rejected — control gap (sub-control %subControlId%)",
 *     "due_in_days": 30
 *   }
 * }
 * }</pre>
 *
 * <h3>De-duplication</h3>
 * <p>The created finding stores the event's source marker (its correlation id, e.g.
 * {@code "evidence:55"}) in {@code clause_ref}. Before creating, the creator skips
 * if a non-CLOSED finding already carries that marker — so it never doubles up with
 * AR-17 (which reopens the existing one) or with a repeated rejection of the same item.
 *
 * <h3>Tenant context</h3>
 * <p>The F8 engine runs {@code @Async}; the originating request's
 * {@code TenantContext} is not visible here, so we set it explicitly from
 * {@code event.organisationId()} and restore it afterwards (mirrors
 * {@code BcmCorrectiveActionRecordCreator}).
 */
@Component
@RequiredArgsConstructor
public class AuditFindingRecordCreator implements RecordCreator {

    private static final Logger log = LoggerFactory.getLogger(AuditFindingRecordCreator.class);

    private final AuditFindingRepository findings;

    @Override
    public String entityType() {
        return "AuditFinding";
    }

    @Override
    public String create(JsonNode action, DomainEvent event) {
        JsonNode fields = action.path("fields");
        String severity  = fields.path("severity").asText("HIGH");
        String template  = fields.path("title_template").asText("");
        Integer dueDays  = fields.path("due_in_days").isMissingNode() ? null : fields.path("due_in_days").asInt();
        String marker    = sourceMarker(event);

        Long prevTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(event.organisationId());
        try {
            // Dedup: complement AR-17 (which reopens an existing gap). If a finding
            // for this source is already open, don't create a duplicate.
            for (AuditFinding existing : findings.findByOrganisationIdAndClauseRef(event.organisationId(), marker)) {
                if (!"CLOSED".equalsIgnoreCase(existing.getStatus())) {
                    log.info("[automation] AuditFinding already open for {} (#{}) — skip create",
                        marker, existing.getId());
                    return "AuditFinding already open for " + marker + " — skipped";
                }
            }

            AuditFinding f = new AuditFinding();
            f.setOrganisationId(event.organisationId());
            f.setTitle(template.isBlank() ? defaultTitle(event) : substitutePayload(template, event));
            f.setSeverity(severity);
            f.setStatus("OPEN");
            f.setClauseRef(marker);
            f.setCapa("Auto-created by automation rule on event " + event.triggerEvent()
                + (event.entityType() == null ? "" : " from " + event.entityType()
                    + (event.entityId() == null ? "" : "#" + event.entityId())));
            if (dueDays != null) f.setDueDate(LocalDate.now().plusDays(dueDays));

            AuditFinding saved = findings.save(f);
            log.info("[automation] CREATE_RECORD AuditFinding#{} for event {} ({})",
                saved.getId(), event.triggerEvent(), marker);
            return "Created AuditFinding#" + saved.getId() + " (" + f.getTitle() + ")";
        } finally {
            if (prevTenant != null) TenantContext.setCurrentTenant(prevTenant);
            else                    TenantContext.clear();
        }
    }

    /** Stable per-source key so repeat events on the same subject don't double-create. */
    private static String sourceMarker(DomainEvent event) {
        if (event.correlationId() != null && !event.correlationId().isBlank()) return event.correlationId();
        return (event.entityType() == null ? "EVENT" : event.entityType())
            + ":" + (event.entityId() == null ? "?" : event.entityId());
    }

    private static String defaultTitle(DomainEvent event) {
        StringBuilder b = new StringBuilder("Gap from ").append(event.triggerEvent());
        if (event.entityType() != null) {
            b.append(" — ").append(event.entityType());
            if (event.entityId() != null) b.append("#").append(event.entityId());
        }
        return b.toString();
    }

    /** Replace {@code %key%} placeholders from the event payload; unknown keys stay as-is. */
    private static String substitutePayload(String template, DomainEvent event) {
        if (event.payload() == null || event.payload().isEmpty()) return template;
        String out = template;
        for (var entry : event.payload().entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return out;
    }
}
