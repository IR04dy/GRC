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

    /** F2-293 — the defaults the entity used to carry as field initialisers. See {@link #create}. */
    static final String DEFAULT_FORMAT = "PDF";
    static final String DEFAULT_LANGUAGE = "en";
    static final String DEFAULT_CADENCE = "WEEKLY";
    static final String DEFAULT_TIME_OF_DAY = "06:00";
    static final boolean DEFAULT_IS_ACTIVE = true;

    /**
     * F2-293 — the create owns every default the entity used to declare as a field initialiser.
     *
     * <p>The initialisers had to go because the controller binds the entity on PUT: Jackson
     * deserialises an omitted key into the initialiser's value, so {@link #apply}'s
     * {@code if (s.getX() != null)} could never be false and a partial update rewrote all five
     * fields. Every one of these columns is {@code NOT NULL}, so a create that stops setting one
     * turns a silent reset into a 500 — which is why the (entity, field, setter, writer, method)
     * quintuples are asserted by {@code NeverFiringNullGuardContractTest}.
     */
    @Transactional
    public ReportSchedule create(ReportSchedule req) {
        ReportSchedule s = new ReportSchedule();
        s.setOrganisationId(TenantContext.getCurrentTenant());
        apply(s, req);
        if (s.getFormat()    == null) s.setFormat(DEFAULT_FORMAT);
        if (s.getLanguage()  == null) s.setLanguage(DEFAULT_LANGUAGE);
        if (s.getCadence()   == null) s.setCadence(DEFAULT_CADENCE);
        if (s.getTimeOfDay() == null) s.setTimeOfDay(DEFAULT_TIME_OF_DAY);
        if (s.getIsActive()  == null) s.setIsActive(DEFAULT_IS_ACTIVE);
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
        if (s.getCadence()        != null) t.setCadence(requireSupportedCadence(s.getCadence()));
        if (s.getCadenceAnchor()  != null) t.setCadenceAnchor(s.getCadenceAnchor());
        if (s.getTimeOfDay()      != null) t.setTimeOfDay(s.getTimeOfDay());
        if (s.getRecipients()     != null) t.setRecipients(s.getRecipients());
        if (s.getCcRecipients()   != null) t.setCcRecipients(s.getCcRecipients());
        if (s.getIsActive()       != null) t.setIsActive(s.getIsActive());
        if (s.getNextRunAt()      != null) t.setNextRunAt(s.getNextRunAt());
    }

    /**
     * F2-293 — refuse a cadence {@link ReportSchedulerService#advanceNextRun} cannot compute.
     *
     * <p>{@code reporting.report_schedules} has <b>zero CHECK constraints</b>, so before this the
     * column's vocabulary lived only in the heads of its readers — and the seed wrote
     * {@code SEMIANNUAL}, which the scheduler's {@code switch} did not name, so its
     * {@code default} rescheduled the semi-annual ISO 22301 / NCEMA BCM readiness report
     * <b>every day</b>: 13 consecutive FAILED runs in dev and prod. F2-196's rule — an unrecognised
     * member should be chased, not excused.
     *
     * <p>⚠ This is a claim about <em>what the scheduler can compute</em>, not about the schema, so
     * it is deliberately NOT a {@code ClosedVocabulary}: that primitive requires the name of a
     * {@code CHECK} it mirrors, and there is none here. The claim is checkable in Java instead —
     * {@code ReportCadenceContractTest} asserts the switch is exhaustive over
     * {@link ReportSchedulerService#SUPPORTED_CADENCES}, which a schema-mirroring set could not do.
     *
     * <p>⚠ No CHECK migration rides with this, and the reason is that {@code report_type} is the
     * larger open vocabulary on the same table: <b>7 of 11 live rows hold a report type the
     * renderer answers with {@code default -> throw}</b>, so those seven reports have never been
     * delivered. Constraining {@code cadence} alone would leave the table's real vocabulary problem
     * open while looking addressed; both want one migration once the report-type question has an
     * owner decision (F2-183: the constraint and its writers land together).
     *
     * <p>{@code require} rather than {@code coerce}: every value stored in either database is
     * already a member, and the frontend offers exactly this set, so no request that succeeds today
     * starts failing (F2-182). What it converts is a silently-daily schedule into a 400 naming the
     * field.
     */
    private String requireSupportedCadence(String cadence) {
        String canonical = cadence.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ReportSchedulerService.SUPPORTED_CADENCES.contains(canonical)) {
            throw new IllegalArgumentException(
                "cadence '" + cadence + "' is not one the scheduler can compute a next run for. "
                + "Allowed: " + ReportSchedulerService.SUPPORTED_CADENCES
                + ". A cadence outside that set would be silently rescheduled daily.");
        }
        return canonical;
    }

    private ReportSchedule require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return scheduleRepo.findById(id)
            .filter(s -> s.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + id));
    }
}
