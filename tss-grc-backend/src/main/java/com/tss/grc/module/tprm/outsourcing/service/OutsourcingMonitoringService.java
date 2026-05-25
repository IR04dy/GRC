package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventResolveRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventResponse;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingMonitoringEvent;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingMonitoringEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 13 — Outsourcing monitoring-event service.
 *
 * <p>Records ongoing-monitoring events (SLA breach, audit report,
 * financial-health flag, etc.). SLA_BREACH events additionally fire
 * the {@code OUTSOURCING_SLA_BREACH} F8 event so they fan out to the
 * Audit Committee dashboard and the action-item queue.
 */
@Service
@RequiredArgsConstructor
public class OutsourcingMonitoringService {

    public static final String SLA_BREACH = "SLA_BREACH";

    private final OutsourcingMonitoringEventRepository repo;
    private final OutsourcingArrangementRepository arrangementRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    @Transactional(readOnly = true)
    public List<MonitoringEventResponse> listForArrangement(Long arrangementId) {
        return repo.findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByEventDateDesc(org(), arrangementId)
            .stream().map(OutsourcingMonitoringService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MonitoringEventResponse> listOpen() {
        return repo.findByOrganisationIdAndResolvedAndIsDeletedFalseOrderByEventDateDesc(org(), false)
            .stream().map(OutsourcingMonitoringService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MonitoringEventResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public MonitoringEventResponse record(MonitoringEventCreateRequest req) {
        Long orgId = org();
        OutsourcingArrangement arr = arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.arrangementId())
            .orElseThrow(() -> new EntityNotFoundException("Arrangement not found: " + req.arrangementId()));

        OutsourcingMonitoringEvent e = new OutsourcingMonitoringEvent();
        e.setOrganisationId(orgId);
        e.setArrangementId(arr.getId());
        e.setEventType(req.eventType());
        e.setEventDate(req.eventDate());
        e.setSeverity(req.severity() != null ? req.severity() : "INFO");
        e.setDescriptionEn(req.descriptionEn());
        e.setDescriptionAr(req.descriptionAr());
        e.setActionTakenEn(req.actionTakenEn());
        e.setActionTakenAr(req.actionTakenAr());
        e.setResolved(Boolean.TRUE.equals(req.resolved()));
        if (Boolean.TRUE.equals(req.resolved())) {
            e.setResolvedAt(LocalDateTime.now());
        }

        OutsourcingMonitoringEvent saved = repo.save(e);
        audit.log("CREATE", "OutsourcingMonitoringEvent", saved.getId(),
            "Monitoring event recorded for " + arr.getArrangementCode()
                + " — " + saved.getEventType() + " / " + saved.getSeverity());

        Map<String, Object> payload = new HashMap<>();
        payload.put("arrangementId", String.valueOf(arr.getId()));
        payload.put("arrangementCode", arr.getArrangementCode());
        payload.put("eventId", String.valueOf(saved.getId()));
        payload.put("eventType", saved.getEventType());
        payload.put("severity", saved.getSeverity());
        payload.put("resolved", String.valueOf(saved.getResolved()));
        publisher.publishEvent(new DomainEvent(
            orgId, "OUTSOURCING_MONITORING_EVENT_RECORDED", "OutsourcingMonitoringEvent", saved.getId(),
            "outsourcing-event:" + saved.getId(), payload));

        // SLA_BREACH events get an additional dedicated trigger so the
        // Audit Committee dashboard can pick them up directly.
        if (SLA_BREACH.equals(saved.getEventType())) {
            publisher.publishEvent(new DomainEvent(
                orgId, "OUTSOURCING_SLA_BREACH", "OutsourcingMonitoringEvent", saved.getId(),
                "outsourcing-sla-breach:" + saved.getId(), payload));
        }

        return toResponse(saved);
    }

    @Transactional
    public MonitoringEventResponse resolve(Long id, MonitoringEventResolveRequest req) {
        OutsourcingMonitoringEvent e = loadOrThrow(id);
        e.setResolved(true);
        e.setResolvedAt(LocalDateTime.now());
        if (req != null) {
            if (req.actionTakenEn() != null && !req.actionTakenEn().isBlank()) e.setActionTakenEn(req.actionTakenEn());
            if (req.actionTakenAr() != null && !req.actionTakenAr().isBlank()) e.setActionTakenAr(req.actionTakenAr());
        }
        e.setUpdatedAt(LocalDateTime.now());
        OutsourcingMonitoringEvent saved = repo.save(e);
        audit.log("RESOLVE", "OutsourcingMonitoringEvent", saved.getId(),
            "Monitoring event resolved: " + saved.getEventType() + " (" + saved.getSeverity() + ")");
        return toResponse(saved);
    }

    private OutsourcingMonitoringEvent loadOrThrow(Long id) {
        return repo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Monitoring event not found: " + id));
    }

    static MonitoringEventResponse toResponse(OutsourcingMonitoringEvent e) {
        return new MonitoringEventResponse(
            e.getId(), e.getExternalId(), e.getOrganisationId(),
            e.getArrangementId(),
            e.getEventType(), e.getEventDate(), e.getSeverity(),
            e.getDescriptionEn(), e.getDescriptionAr(),
            e.getActionTakenEn(), e.getActionTakenAr(),
            e.getResolved(), e.getResolvedAt(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
