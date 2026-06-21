package com.tss.grc.module.integrations.servicenow.webhook;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Inbound webhook receiver — ServiceNow's outbound REST step posts here
 * when an INC the GRC platform is tracking changes state.
 *
 * <h3>Auth model</h3>
 *
 * <p>The platform does not yet have a dedicated "machine-to-machine" auth
 * scheme distinct from user JWT. Until one lands (a shared HMAC header is
 * the most likely follow-up), this endpoint is guarded by
 * {@code hasAnyRole('PLATFORM_ADMIN','SOC_AUTOMATION','INTEGRATION_SERVICE')}
 * so the ServiceNow side has to authenticate as a dedicated service
 * account that has been granted one of those roles. Documented in
 * {@code SERVICENOW.md} so the operator knows how to provision it.
 *
 * <h3>Depth — initial slice</h3>
 *
 * <p>Right now we accept the payload, locate the matching
 * {@link IntegrationExternalLink} row, update its
 * {@code lastKnownState} and {@code lastPulledAt}, and emit an F8
 * domain event {@code SNOW_INCIDENT_UPDATED}. The actual side-effect of
 * propagating "Resolved on SNow → RESOLVED on GRC" is left to an
 * automation rule (which can be wired without code changes) or to a
 * follow-up that touches the cyber/audit/capa modules directly.
 */
@RestController
@RequestMapping("/api/v1/integrations/servicenow/webhook")
@Tag(name = "ServiceNow Webhook")
@SecurityRequirement(name = "bearerAuth")
public class ServiceNowWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowWebhookController.class);

    private final IntegrationExternalLinkRepository linkRepo;
    private final ApplicationEventPublisher publisher;

    public ServiceNowWebhookController(
        IntegrationExternalLinkRepository linkRepo,
        ApplicationEventPublisher publisher
    ) {
        this.linkRepo = linkRepo;
        this.publisher = publisher;
    }

    @PostMapping("/incident-updated")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SOC_AUTOMATION','INTEGRATION_SERVICE')")
    @Operation(summary = "ServiceNow → GRC: incident state changed")
    @Transactional
    public ResponseEntity<Map<String, Object>> incidentUpdated(@RequestBody Map<String, Object> payload) {
        String number = str(payload.get("number"));
        String state = str(payload.get("state"));
        log.info("[ServiceNow-Webhook] incident-updated number={} state={}", number, state);

        Map<String, Object> ack = new HashMap<>();
        ack.put("received", true);
        ack.put("at", LocalDateTime.now().toString());

        if (number == null || number.isBlank()) {
            ack.put("matched", false);
            ack.put("reason", "missing 'number' in payload");
            return ResponseEntity.badRequest().body(ack);
        }

        // Scope the lookup to the authenticated caller's tenant. Never act on,
        // mutate, or attribute events to a link owned by another organisation:
        // ServiceNow INC numbers are sequential/predictable, so an org-blind
        // by-natural-key lookup is a cross-tenant write/IDOR (SEC-INT-002).
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            ack.put("matched", false);
            ack.put("reason", "missing tenant context");
            return ResponseEntity.badRequest().body(ack);
        }

        Optional<IntegrationExternalLink> match = linkRepo
            .findFirstByOrganisationIdAndIntegrationAndExternalNumber(orgId, "SERVICENOW", number);
        if (match.isEmpty()) {
            ack.put("matched", false);
            ack.put("reason", "no GRC record linked to " + number);
            return ResponseEntity.ok(ack);
        }

        IntegrationExternalLink link = match.get();
        link.setLastPulledAt(LocalDateTime.now());
        link.setLastKnownState(state);
        link.setUpdatedAt(LocalDateTime.now());
        linkRepo.save(link);

        // Emit F8 event so automation rules can react (e.g. close GRC item on SNow Closed).
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("snowNumber", number);
        eventPayload.put("snowState", state);
        eventPayload.put("grcModule", link.getGrcModule());
        eventPayload.put("grcRecordId", link.getGrcRecordId());
        publisher.publishEvent(DomainEvent.of(
            link.getOrganisationId(),
            "SNOW_INCIDENT_UPDATED",
            link.getGrcModule(),
            link.getId(),
            eventPayload
        ));

        ack.put("matched", true);
        ack.put("grcModule", link.getGrcModule());
        ack.put("grcRecordId", link.getGrcRecordId());
        ack.put("newState", state);
        return ResponseEntity.ok(ack);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
