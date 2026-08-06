package com.tss.grc.module.bcm.comms.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.comms.channel.ChannelSender;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.CascadeResponse;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.ChannelReadiness;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.DispatchRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.DispatchResult;
import com.tss.grc.module.bcm.comms.entity.CrisisCommsCascade;
import com.tss.grc.module.bcm.comms.entity.CrisisCommsTemplateV2;
import com.tss.grc.module.bcm.comms.repository.CrisisCommsCascadeRepository;
import com.tss.grc.module.bcm.comms.repository.CrisisCommsTemplateV2Repository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Slice 9-E — multi-channel cascade dispatch service.
 *
 * <p>Given a crisis event + a pre-approved template + a list of channels,
 * renders the template (placeholder substitution), fans out to every
 * channel in parallel, records one {@code crisis_comms_cascade} row
 * per channel, and emits a {@code CRISIS_COMMS_CASCADE_DISPATCHED} F8
 * event per row + a roll-up {@code CRISIS_COMMS_CASCADE_COMPLETED} event
 * once all channels have been attempted.
 *
 * <p>The dispatch service refuses to fan out a template that is not
 * {@code APPROVED_BY_CISO} — pre-approval is a non-negotiable governance
 * control. Pass {@code skipApprovalCheck=true} only in exercises (out
 * of scope for this slice).
 */
@Service
public class CrisisCommsDispatchService {

    private static final Logger log = LoggerFactory.getLogger(CrisisCommsDispatchService.class);

    /**
     * The channel vocabulary {@code DispatchRequest} accepts — kept in step
     * with the {@code @Pattern} on {@code DispatchRequest.channels}. Readiness
     * reports every one of these, including any with no registered sender,
     * because "nothing would happen" is the answer an operator most needs.
     */
    static final List<String> DISPATCHABLE_CHANNELS =
        List.of("EMAIL", "SMS", "WHATSAPP", "TEAMS", "VOICE_BLAST");

    private final CrisisCommsTemplateV2Repository templateRepo;
    private final CrisisCommsCascadeRepository cascadeRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /** {@code channelType() → sender} — populated from every registered bean. */
    private final Map<String, ChannelSender> sendersByType;

    public CrisisCommsDispatchService(CrisisCommsTemplateV2Repository templateRepo,
                                      CrisisCommsCascadeRepository cascadeRepo,
                                      AuditLogService audit,
                                      ApplicationEventPublisher publisher,
                                      List<ChannelSender> senders) {
        this.templateRepo = templateRepo;
        this.cascadeRepo = cascadeRepo;
        this.audit = audit;
        this.publisher = publisher;
        this.sendersByType = senders.stream()
            .collect(Collectors.toMap(ChannelSender::channelType, c -> c, (a, b) -> a));
    }

    private Long org() { return TenantContext.requireOrganisationId(); }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CascadeResponse> listForCrisisEvent(Long crisisEventId) {
        return cascadeRepo.findByOrganisationIdAndCrisisEventIdAndIsDeletedFalseOrderByDispatchedAtDesc(
                org(), crisisEventId)
            .stream().map(CrisisCommsDispatchService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CascadeResponse> listAll() {
        return cascadeRepo.findByOrganisationIdAndIsDeletedFalseOrderByDispatchedAtDesc(org())
            .stream().map(CrisisCommsDispatchService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CascadeResponse get(UUID externalId) {
        return cascadeRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(org(), externalId)
            .map(CrisisCommsDispatchService::toResponse)
            .orElseThrow(() -> new EntityNotFoundException("Cascade not found: " + externalId));
    }

    // ─── Dispatch ───────────────────────────────────────────────────

    /**
     * Dispatch a cascade. Returns one cascade row per requested channel,
     * along with roll-up counters and elapsed time so the UI can show the
     * "under 2 minutes" KPI live.
     */
    @Transactional
    public DispatchResult dispatch(DispatchRequest req) {
        long started = System.currentTimeMillis();
        Long orgId = org();

        if (req.channels() == null || req.channels().isEmpty()) {
            throw new IllegalArgumentException("at least one channel must be selected");
        }

        CrisisCommsTemplateV2 template = templateRepo
            .findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.templateId())
            .orElseThrow(() -> new EntityNotFoundException(
                "Template not found in this tenant: " + req.templateId()));

        if (!"APPROVED_BY_CISO".equals(template.getApprovalStatus())) {
            throw new IllegalStateException(
                "Refusing to dispatch — template " + template.getTemplateCode()
                    + " is in state " + template.getApprovalStatus()
                    + " (only APPROVED_BY_CISO templates may be dispatched)");
        }

        Map<String, String> placeholders = req.placeholders() == null
            ? Map.of() : req.placeholders();
        List<String> recipients = req.recipients() == null
            ? Collections.emptyList() : req.recipients();

        String correlationId = AuditLogService.newCorrelationId();

        List<CrisisCommsCascade> persisted = new ArrayList<>();
        int totalRecipients = 0, totalSuccess = 0, totalFailure = 0;

        for (String channelType : req.channels()) {
            ChannelSender sender = sendersByType.get(channelType);

            // Pick subject / body — SMS / WhatsApp use the 160-char short
            // variant when available; everything else falls back to the
            // long-form body.
            String subject = applyPlaceholders(template.getSubjectEn(), placeholders);
            String bodySource;
            if (("SMS".equals(channelType) || "WHATSAPP".equals(channelType))
                && template.getSmsBodyEn() != null && !template.getSmsBodyEn().isBlank()) {
                bodySource = template.getSmsBodyEn();
            } else {
                bodySource = template.getBodyEn();
            }
            String body = applyPlaceholders(bodySource, placeholders);

            CrisisCommsCascade c = new CrisisCommsCascade();
            c.setOrganisationId(orgId);
            c.setCrisisEventId(req.crisisEventId());
            c.setTemplateId(template.getId());
            c.setChannel(channelType);
            c.setAudience(req.audienceFilter() != null ? req.audienceFilter() : template.getAudience());
            c.setRenderedSubject(subject);
            c.setRenderedBody(body);
            c.setRecipientCount(recipients.size());
            c.setDispatchedAt(LocalDateTime.now());
            c.setDispatchedByName(req.dispatchedByName());
            c.setDispatchedByUserId(req.dispatchedByUserId());

            if (sender == null) {
                c.setStatus("FAILED");
                c.setFailureCount(recipients.size());
                c.setResponsePayload("no registered ChannelSender for type " + channelType);
                c.setCompletedAt(LocalDateTime.now());
            } else {
                c.setStatus("DISPATCHING");
                ChannelSender.SendResult result;
                try {
                    result = sender.send(recipients, subject, body, correlationId);
                } catch (Exception e) {
                    log.error("[cascade] {} sender threw — recording FAILED row",
                        channelType, e);
                    result = ChannelSender.SendResult.fail(recipients.size(),
                        "sender exception: " + e.getMessage());
                }
                c.setSuccessCount(result.successCount());
                c.setFailureCount(result.failureCount());
                c.setResponsePayload(result.responsePayload());
                c.setSimulated(result.simulated());
                c.setCompletedAt(LocalDateTime.now());
                if (result.failureCount() == 0 && result.successCount() > 0) {
                    c.setStatus("COMPLETED");
                } else if (result.successCount() == 0 && result.failureCount() > 0) {
                    c.setStatus("FAILED");
                } else if (result.failureCount() > 0) {
                    c.setStatus("PARTIAL");
                } else {
                    // 0 / 0 — empty recipient list. Treat as COMPLETED with
                    // zero counts so the audit row still records "dispatched".
                    c.setStatus("COMPLETED");
                }
            }

            CrisisCommsCascade saved = cascadeRepo.save(c);
            persisted.add(saved);

            totalRecipients += saved.getRecipientCount();
            totalSuccess    += saved.getSuccessCount();
            totalFailure    += saved.getFailureCount();

            audit.log("DISPATCH", "CrisisCommsCascade", saved.getId(),
                "Cascade dispatched: " + template.getTemplateCode() + " via " + channelType
                    + " (" + saved.getSuccessCount() + "/" + saved.getRecipientCount() + " ok)");

            emitDispatchEvent(saved, template, correlationId);
        }

        long elapsed = System.currentTimeMillis() - started;

        // F2-147 — which channels only logged. Named in the roll-up so the UI
        // never has to infer it from a free-text provider string, and carried
        // into the F8 event so the automation trail is honest too.
        List<String> simulatedChannels = persisted.stream()
            .filter(c -> Boolean.TRUE.equals(c.getSimulated()))
            .map(CrisisCommsCascade::getChannel)
            .toList();

        emitCompletedRollupEvent(req.crisisEventId(), template, persisted.size(),
            totalRecipients, totalSuccess, totalFailure, elapsed, correlationId,
            simulatedChannels);

        if (!simulatedChannels.isEmpty()) {
            log.warn("[cascade] crisisEvent={} template={} — {} of {} channels SIMULATED ({}): "
                    + "counters record intent, not delivery",
                req.crisisEventId(), template.getTemplateCode(),
                simulatedChannels.size(), persisted.size(),
                String.join(",", simulatedChannels));
        }

        return new DispatchResult(
            req.crisisEventId(),
            template.getId(),
            persisted.stream().map(CrisisCommsDispatchService::toResponse).toList(),
            totalRecipients, totalSuccess, totalFailure, elapsed,
            simulatedChannels);
    }

    // ─── Channel readiness ──────────────────────────────────────────

    /**
     * Per-channel readiness for every channel the dispatch request accepts.
     *
     * <p>F2-147: this is the surface that lets an operator find out a channel
     * is simulated <em>before</em> a crisis rather than from a payload string
     * afterwards, and it is the first caller {@code isHealthy()} has ever had
     * — until now the SPI's health signal was a literal {@code true} that
     * nothing read.
     */
    public List<ChannelReadiness> channelReadiness() {
        return DISPATCHABLE_CHANNELS.stream().map(type -> {
            ChannelSender sender = sendersByType.get(type);
            if (sender == null) {
                return new ChannelReadiness(type, false, false, "UNREGISTERED");
            }
            // A sender that declines to answer is reported UNKNOWN, never LIVE.
            ChannelSender.DeliveryMode mode = sender.deliveryMode();
            return new ChannelReadiness(type, true, sender.isHealthy(),
                mode == null ? "UNKNOWN" : mode.name());
        }).toList();
    }

    // ─── Placeholder substitution ───────────────────────────────────

    /**
     * {@code {{key}}} → value substitution + normalize literal {@code \n}
     * (two-char escape stored by V172 seed) into real newlines so email
     * clients render multi-line bodies correctly. Missing placeholders
     * are left in place so the operator notices them in preview rather
     * than silently sending an empty string to recipients.
     */
    static String applyPlaceholders(String template, Map<String, String> placeholders) {
        if (template == null) return null;
        String out = template;
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String key = e.getKey();
                String value = e.getValue() == null ? "" : e.getValue();
                out = out.replace("{{" + key + "}}", value);
            }
        }
        // SQL Server INSERT statements store '\n' as literal two-char
        // sequence; normalize to a real newline so SMTP renders correctly.
        return out.replace("\\n", "\n");
    }

    // ─── F8 emission ────────────────────────────────────────────────

    private void emitDispatchEvent(CrisisCommsCascade c, CrisisCommsTemplateV2 t, String correlationId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cascadeId",       str(c.getId()));
        payload.put("crisisEventId",   str(c.getCrisisEventId()));
        payload.put("templateId",      str(t.getId()));
        payload.put("templateCode",    str(t.getTemplateCode()));
        payload.put("channel",         str(c.getChannel()));
        payload.put("audience",        str(c.getAudience()));
        payload.put("recipientCount",  str(c.getRecipientCount()));
        payload.put("successCount",    str(c.getSuccessCount()));
        payload.put("failureCount",    str(c.getFailureCount()));
        payload.put("status",          str(c.getStatus()));
        payload.put("simulated",       str(Boolean.TRUE.equals(c.getSimulated())));
        publisher.publishEvent(new DomainEvent(
            c.getOrganisationId(),
            "CRISIS_COMMS_CASCADE_DISPATCHED",
            "CrisisCommsCascade",
            c.getId(),
            correlationId,
            payload));
    }

    private void emitCompletedRollupEvent(Long crisisEventId, CrisisCommsTemplateV2 t,
                                          int channelCount, int totalRecipients,
                                          int totalSuccess, int totalFailure,
                                          long elapsedMillis, String correlationId,
                                          List<String> simulatedChannels) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("crisisEventId",   str(crisisEventId));
        payload.put("templateId",      str(t.getId()));
        payload.put("templateCode",    str(t.getTemplateCode()));
        payload.put("channelCount",    str(channelCount));
        payload.put("totalRecipients", str(totalRecipients));
        payload.put("totalSuccess",    str(totalSuccess));
        payload.put("totalFailure",    str(totalFailure));
        payload.put("elapsedMillis",   str(elapsedMillis));
        payload.put("simulatedChannels", String.join(",", simulatedChannels));
        publisher.publishEvent(new DomainEvent(
            t.getOrganisationId(),
            "CRISIS_COMMS_CASCADE_COMPLETED",
            "CrisisCommsTemplateV2",
            t.getId(),
            correlationId,
            payload));
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    // ─── Mapping ────────────────────────────────────────────────────

    static CascadeResponse toResponse(CrisisCommsCascade c) {
        return new CascadeResponse(
            c.getId(),
            c.getExternalId(),
            c.getOrganisationId(),
            c.getCrisisEventId(),
            c.getTemplateId(),
            c.getChannel(),
            c.getAudience(),
            c.getRenderedSubject(),
            c.getRecipientCount(),
            c.getSuccessCount(),
            c.getFailureCount(),
            c.getDispatchedAt(),
            c.getCompletedAt(),
            c.getDispatchedByName(),
            c.getStatus(),
            c.getResponsePayload(),
            Boolean.TRUE.equals(c.getSimulated())
        );
    }
}
