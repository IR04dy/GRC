package com.tss.grc.module.privacy.subprocessor.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventResponse;
import com.tss.grc.module.privacy.subprocessor.entity.Subprocessor;
import com.tss.grc.module.privacy.subprocessor.entity.SubprocessorEvent;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorEventRepository;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Append-only event ledger for {@link Subprocessor}.
 *
 * <p>Most lifecycle-changing events are written by
 * {@link SubprocessorService} (so they stay transactionally consistent
 * with the register row). This service handles the standalone cases:
 * <ul>
 *   <li>{@code INCIDENT_REPORTED} — raised by the privacy-breach
 *       module via cross-module dispatch</li>
 *   <li>data-subject notification flag updates after the fact</li>
 *   <li>standalone reads for the detail-page Events tab</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SubprocessorEventService {

    private final SubprocessorEventRepository eventRepo;
    private final SubprocessorRepository subRepo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public List<SubprocessorEventResponse> listForSubprocessor(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        Subprocessor s = subRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("Subprocessor not found: " + externalId));
        return eventRepo.findByOrganisationIdAndSubprocessorIdAndIsDeletedFalseOrderByEventDateDesc(
                orgId, s.getId()).stream().map(SubprocessorEventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SubprocessorEventResponse> listAll() {
        Long orgId = TenantContext.getCurrentTenant();
        return eventRepo.findByOrganisationIdAndIsDeletedFalseOrderByEventDateDesc(orgId)
                .stream().map(SubprocessorEventResponse::from).toList();
    }

    @Transactional
    public SubprocessorEventResponse recordEvent(UUID subExternalId, SubprocessorEventRequest req, Long recordedByUserId) {
        Long orgId = TenantContext.getCurrentTenant();
        Subprocessor s = subRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, subExternalId)
                .orElseThrow(() -> new EntityNotFoundException("Subprocessor not found: " + subExternalId));

        SubprocessorEvent ev = new SubprocessorEvent();
        ev.setOrganisationId(orgId);
        ev.setSubprocessorId(s.getId());
        ev.setEventType(req.eventType());
        ev.setEventDate(LocalDateTime.now());
        ev.setRecordedByUserId(recordedByUserId);
        ev.setDescriptionEn(req.descriptionEn());
        ev.setDescriptionAr(req.descriptionAr());
        if (req.dataSubjectsNotified() != null) ev.setDataSubjectsNotified(req.dataSubjectsNotified());
        if (req.dataSubjectsNotificationDate() != null)
            ev.setDataSubjectsNotificationDate(req.dataSubjectsNotificationDate());

        ev = eventRepo.save(ev);
        audit.log("EVENT_RECORDED", "SubprocessorEvent", ev.getId(),
                "Event " + ev.getEventType() + " recorded for sub-processor " + s.getSubprocessorCode());
        return SubprocessorEventResponse.from(ev);
    }
}
