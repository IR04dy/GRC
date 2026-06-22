package com.tss.grc.module.data_officer.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.EventResponse;
import com.tss.grc.module.data_officer.entity.Entities.DoEvent;
import com.tss.grc.module.data_officer.repository.DoEventRepository;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared infrastructure for the Data Officer toolkit services.
 *
 * <p>Extracted from the former {@code DataOfficerService} god-class
 * (QUAL-ESG-001): the per-domain services (DSR/Consent/Notice, DPIA/Breach/
 * Ethics, Retention/Disposal/NDR/Catalog/Quality) all share a single
 * tenant-scoped event timeline writer, a domain-event emitter and a
 * state-machine validator. Behaviour is unchanged.
 */
@Component
@RequiredArgsConstructor
public class DataOfficerSupport {

    private final DoEventRepository eventRepo;
    private final ApplicationEventPublisher publisher;

    /** Appends a row to the tenant-scoped Data Officer event timeline. */
    @Transactional
    public void writeTimeline(String entityType, Long entityId, String eventType, String statusAfter,
                              String noteEn, String noteAr) {
        DoEvent e = new DoEvent();
        e.setOrganisationId(org());
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    /** Publishes an automation {@link DomainEvent} for the current tenant. */
    public void emit(String trigger, String entityType, Long entityId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.getCurrentTenant(),
            trigger, entityType, entityId, null,
            payload == null ? Map.of() : new HashMap<>(payload)));
    }

    /** Reads the timeline for one entity, newest first. */
    @Transactional(readOnly = true)
    public List<EventResponse> readTimeline(String entityType, Long entityId) {
        return eventRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(org(), entityType, entityId)
            .stream().map(e -> new EventResponse(
                e.getId(), e.getEntityType(), e.getEntityId(),
                e.getOccurredAt(), e.getEventType(),
                e.getActorUserId(), e.getStatusAfter(),
                e.getNoteEn(), e.getNoteAr()
            )).toList();
    }

    /** Validates a state-machine transition; throws if {@code from → to} is not allowed. */
    public static void validate(Map<String, Set<String>> allowed, String from, String to, String label) {
        if (!allowed.getOrDefault(from, Set.of()).contains(to))
            throw new IllegalStateException(label + " cannot transition " + from + " → " + to);
    }

    public static Long org() { return TenantContext.requireOrganisationId(); }

    public static String nullSafe(String s) { return s == null ? "" : s; }

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }
}
