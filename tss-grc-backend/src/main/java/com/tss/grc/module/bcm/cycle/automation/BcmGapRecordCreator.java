package com.tss.grc.module.bcm.cycle.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import com.tss.grc.module.bcm.cycle.repository.BcmGapRepository;
import com.tss.grc.module.bcm.cycle.service.BcmRemediationService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.automation.service.RecordCreator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * F8 {@link RecordCreator} for the {@code BcmGap} entity — closes review epic
 * <b>B-D</b> (evidence lifecycle: reject → gap).
 *
 * <p>Fires from the {@code EVIDENCE_REQUEST_REJECTED} event published by
 * {@code EvidenceRequestService.updateStatus}. That event is module-agnostic;
 * this creator is the BCM-side reactor and only acts when the rejected request
 * was anchored to a BCM record (the {@code anchorType} payload value is one of
 * {@link #BCM_ANCHORS}). For non-BCM rejections it no-ops, so the seeded rule
 * can stay unconditional.
 *
 * <h3>Rule action shape</h3>
 * <pre>{@code
 * {
 *   "type": "CREATE_RECORD",
 *   "entity": "BcmGap",
 *   "fields": { "severity": "HIGH", "due_in_days": 30 }
 * }
 * }</pre>
 *
 * <h3>De-duplication</h3>
 * <p>The gap links back to the source evidence request via
 * {@code relatedRecordType="EvidenceRequest"} + {@code relatedRecordId}. Before
 * creating, the creator skips if a non-CLOSED gap already references the same
 * request — so a re-reject of the same request never doubles up.
 *
 * <h3>Tenant context</h3>
 * <p>The F8 engine runs {@code @Async}; the originating request's
 * {@code TenantContext} is not visible here, so we set it from
 * {@code event.organisationId()} and restore it afterwards (mirrors
 * {@code BcmCorrectiveActionRecordCreator}).
 */
@Component
@RequiredArgsConstructor
public class BcmGapRecordCreator implements RecordCreator {

    private static final Logger log = LoggerFactory.getLogger(BcmGapRecordCreator.class);

    /** Anchor types this creator owns — the BCM entity types used by B-B/B-C. */
    private static final Set<String> BCM_ANCHORS = Set.of(
        "BCP_PLAN", "DR_PLAN", "BIA_ASSESSMENT", "BCM_GOVERNANCE", "CRISIS_EVENT", "BCM_CAPA");

    private final BcmRemediationService remediation;
    private final BcmGapRepository gaps;

    @Override
    public String entityType() {
        return "BcmGap";
    }

    @Override
    public String create(JsonNode action, DomainEvent event) {
        String anchorType = str(event, "anchorType");
        // Only act on BCM-anchored rejections; other modules' rejections no-op.
        if (anchorType == null || !BCM_ANCHORS.contains(anchorType)) {
            return "Not a BCM-anchored rejection (" + anchorType + ") — skipped";
        }

        JsonNode fields = action.path("fields");
        String severity = fields.path("severity").asText("HIGH");
        Integer dueDays = fields.path("due_in_days").isMissingNode() ? null : fields.path("due_in_days").asInt();

        Long requestId = event.entityId();                 // the EvidenceRequest id
        String anchorId = str(event, "anchorId");
        String reqCode  = str(event, "requestCode");
        String reqTitle = str(event, "title");

        Long prevTenant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(event.organisationId());
        try {
            // Dedup against re-rejection of the same evidence request.
            if (requestId != null) {
                for (BcmGap existing : gaps.findByOrganisationIdAndRelatedRecordTypeAndRelatedRecordId(
                        event.organisationId(), "EvidenceRequest", requestId)) {
                    if (!"CLOSED".equalsIgnoreCase(existing.getStatus())) {
                        log.info("[automation] BcmGap already open for evidence request #{} (#{}) — skip",
                            requestId, existing.getId());
                        return "BcmGap already open for evidence request #" + requestId + " — skipped";
                    }
                }
            }

            BcmGap gap = new BcmGap();
            gap.setSource("EVIDENCE");
            gap.setSeverity(severity);
            gap.setStatus("OPEN");
            gap.setRelatedRecordType("EvidenceRequest");
            gap.setRelatedRecordId(requestId);
            gap.setTitleEn("Evidence rejected — " + anchorType
                + (anchorId == null ? "" : "#" + anchorId)
                + (reqTitle == null || reqTitle.isBlank() ? "" : ": " + reqTitle));
            gap.setTitleAr("رفض دليل — " + anchorType
                + (anchorId == null ? "" : "#" + anchorId));
            gap.setDescription("Auto-created from rejected evidence request "
                + (reqCode == null ? "#" + requestId : reqCode)
                + " on event " + event.triggerEvent() + ". Provide compliant evidence to close.");
            if (dueDays != null) gap.setDueDate(LocalDate.now().plusDays(dueDays));

            BcmGap saved = remediation.createGap(gap);
            log.info("[automation] CREATE_RECORD BcmGap#{} for {} (request #{})",
                saved.getId(), anchorType, requestId);
            return "Created BcmGap#" + saved.getId() + " (" + saved.getTitleEn() + ")";
        } finally {
            if (prevTenant != null) TenantContext.setCurrentTenant(prevTenant);
            else                    TenantContext.clear();
        }
    }

    private static String str(DomainEvent event, String key) {
        if (event.payload() == null) return null;
        Object v = event.payload().get(key);
        return v == null ? null : String.valueOf(v);
    }
}
