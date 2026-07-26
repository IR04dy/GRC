package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.reporting.entity.ReportRun;
import com.tss.grc.module.reporting.entity.ReportSchedule;
import com.tss.grc.module.reporting.repository.ReportRunRepository;
import com.tss.grc.module.reporting.repository.ReportScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD + run-now front for {@link ReportSchedule}. The actual cron tick lives
 * in {@link ReportSchedulerService}.
 */
@Service
@RequiredArgsConstructor
public class ReportScheduleService {

    private final ReportScheduleRepository scheduleRepo;
    private final ReportRunRepository runRepo;
    private final ReportSchedulerService schedulerService;

    @Transactional(readOnly = true)
    public Page<ReportSchedule> list(Pageable pageable) {
        return scheduleRepo.findByOrganisationIdOrderByCreatedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public ReportSchedule get(Long id) { return require(id); }

    @Transactional
    public ReportSchedule create(ReportSchedule req) {
        ReportSchedule s = new ReportSchedule();
        s.setOrganisationId(TenantContext.getCurrentTenant());
        apply(s, req);
        if (s.getNextRunAt() == null) s.setNextRunAt(LocalDateTime.now().plusMinutes(1));
        return scheduleRepo.save(s);
    }

    @Transactional
    public ReportSchedule update(Long id, ReportSchedule req) {
        ReportSchedule s = require(id);
        apply(s, req);
        return scheduleRepo.save(s);
    }

    @Transactional
    public void delete(Long id) {
        ReportSchedule s = require(id);
        s.setIsDeleted(true);
        scheduleRepo.save(s);
    }

    @Transactional
    public ReportRun runNow(Long id) {
        ReportSchedule s = require(id);
        return schedulerService.runNow(s);
    }

    @Transactional(readOnly = true)
    public List<ReportRun> recentRunsForSchedule(Long scheduleId) {
        require(scheduleId);
        return runRepo.findTop50ByScheduleIdOrderByTriggeredAtDesc(scheduleId);
    }

    @Transactional(readOnly = true)
    public List<ReportRun> recentRuns() {
        return runRepo.findTop50ByOrganisationIdOrderByTriggeredAtDesc(TenantContext.getCurrentTenant());
    }

    /**
     * Copies the caller-settable fields. Deliberately field-by-field rather than a bean copy:
     * {@code id}, {@code organisationId}, {@code externalId}, the audit stamps and the
     * {@code lastRun*} outcome fields are server-owned, and the controller binds the entity
     * straight from the request body.
     *
     * <p>F2-13a: {@code nameAr} and {@code descriptionAr} were missing from this list, so the two
     * Arabic columns V20 added were accepted by the endpoint, mapped by the entity, and dropped
     * here without an error — every schedule was English-only and nothing said so. Invisible until
     * V321 made these endpoints reachable at all: no role held {@code REPORTING:WRITE}, so no
     * request had ever completed.
     */
    private void apply(ReportSchedule t, ReportSchedule s) {
        if (s.getName()           != null) t.setName(s.getName());
        if (s.getNameAr()         != null) t.setNameAr(s.getNameAr());
        if (s.getDescription()    != null) t.setDescription(s.getDescription());
        if (s.getDescriptionAr()  != null) t.setDescriptionAr(s.getDescriptionAr());
        if (s.getReportType()     != null) t.setReportType(s.getReportType());
        if (s.getFormat()         != null) t.setFormat(s.getFormat());
        if (s.getLanguage()       != null) t.setLanguage(s.getLanguage());
        if (s.getCadence()        != null) t.setCadence(s.getCadence());
        if (s.getCadenceAnchor()  != null) t.setCadenceAnchor(s.getCadenceAnchor());
        if (s.getTimeOfDay()      != null) t.setTimeOfDay(s.getTimeOfDay());
        if (s.getRecipients()     != null) t.setRecipients(s.getRecipients());
        if (s.getCcRecipients()   != null) t.setCcRecipients(s.getCcRecipients());
        if (s.getIsActive()       != null) t.setIsActive(s.getIsActive());
        if (s.getNextRunAt()      != null) t.setNextRunAt(s.getNextRunAt());
    }

    private ReportSchedule require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return scheduleRepo.findById(id)
            .filter(s -> s.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + id));
    }
}
