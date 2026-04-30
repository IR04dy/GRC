package com.tss.grc.module.incident.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.incident.dto.*;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.module.incident.repository.IncidentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepo;

    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(String severity, String status, String category, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        String sev  = (severity != null && !severity.isBlank()) ? severity : null;
        String sta  = (status   != null && !status.isBlank())   ? status   : null;
        String cat  = (category != null && !category.isBlank()) ? category : null;
        return incidentRepo.findWithFilters(orgId, sev, sta, cat, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public IncidentResponse create(IncidentRequest req) {
        Incident i = new Incident();
        i.setOrganisationId(TenantContext.getCurrentTenant());
        apply(i, req);
        return toResponse(incidentRepo.save(i));
    }

    @Transactional
    public IncidentResponse update(Long id, IncidentRequest req) {
        Incident i = require(id);
        apply(i, req);
        return toResponse(incidentRepo.save(i));
    }

    @Transactional
    public void delete(Long id) {
        Incident i = require(id);
        i.setIsDeleted(true);
        incidentRepo.save(i);
    }

    @Transactional(readOnly = true)
    public IncidentStats getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        long open          = incidentRepo.countByOrganisationIdAndStatus(orgId, "OPEN");
        long investigating = incidentRepo.countByOrganisationIdAndStatus(orgId, "INVESTIGATING");
        long resolved      = incidentRepo.countByOrganisationIdAndStatus(orgId, "RESOLVED");
        long closed        = incidentRepo.countByOrganisationIdAndStatus(orgId, "CLOSED");
        long critical      = incidentRepo.countByOrganisationIdAndSeverity(orgId, "CRITICAL");
        long high          = incidentRepo.countByOrganisationIdAndSeverity(orgId, "HIGH");
        long total         = open + investigating + resolved + closed;

        // Average resolution time in days (for incidents with both discoveryDate and resolutionDate)
        List<Incident> resolvedList = incidentRepo.findRecent(orgId, PageRequest.of(0, 200));
        double avgDays = resolvedList.stream()
            .filter(i -> i.getDiscoveryDate() != null && i.getResolutionDate() != null)
            .mapToLong(i -> ChronoUnit.DAYS.between(i.getDiscoveryDate(), i.getResolutionDate()))
            .filter(d -> d >= 0)
            .average()
            .orElse(0.0);

        return new IncidentStats(total, open, investigating, resolved, critical, high, avgDays);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getRecent() {
        Long orgId = TenantContext.getCurrentTenant();
        return incidentRepo.findRecent(orgId, PageRequest.of(0, 5)).stream().map(this::toResponse).toList();
    }

    private void apply(Incident i, IncidentRequest req) {
        i.setTitle(req.title());
        i.setTitleAr(req.titleAr());
        i.setSeverity(req.severity());
        i.setCategory(req.category());
        i.setStatus(req.status() != null ? req.status() : "OPEN");
        i.setAssignee(req.assignee());
        i.setDiscoveryDate(req.discoveryDate());
        i.setResolutionDate(req.resolutionDate());
        i.setDescription(req.description());
        i.setTimelineJson(req.timelineJson());
        i.setLessonsLearned(req.lessonsLearned());
        i.setLessonsLearnedAr(req.lessonsLearnedAr());
    }

    private Incident require(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return incidentRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Incident not found: " + id));
    }

    private IncidentResponse toResponse(Incident i) {
        return new IncidentResponse(
            i.getId(), i.getTitle(), i.getTitleAr(),
            i.getSeverity(), i.getCategory(), i.getStatus(),
            i.getAssignee(), i.getDiscoveryDate(), i.getResolutionDate(),
            i.getDescription(), i.getTimelineJson(),
            i.getLessonsLearned(), i.getLessonsLearnedAr(),
            i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
