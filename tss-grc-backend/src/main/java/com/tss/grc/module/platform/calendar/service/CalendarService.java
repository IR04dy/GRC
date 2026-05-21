package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.calendar.dto.CalendarEventDto;
import com.tss.grc.module.platform.calendar.entity.CalendarEvent;
import com.tss.grc.module.platform.calendar.repository.CalendarEventRepository;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Compliance Calendar service. Two flows:
 *
 *   1. {@link #listInWindow(LocalDate, LocalDate, List)} — aggregator
 *      that stitches persisted {@link CalendarEvent}s with synthetic
 *      "AGGREGATED" rows pulled from dated GRC entities (CAPA target
 *      dates, Finding remediation deadlines, Risk attestation /
 *      review dates). The aggregator gives the buyer a single
 *      Outlook-like view of everything that lands inside a date
 *      window, without us having to keep duplicate copies in a
 *      calendar table that immediately go stale.
 *
 *   2. CRUD on user-created events ({@link #create}, {@link #update},
 *      {@link #delete}, {@link #get}) — meetings, training, custom
 *      reminders that don't have a parent record. All
 *      tenant-scoped via {@link TenantContext}.
 *
 * <p>Every event in the returned list carries an {@code onWorkday}
 * flag the frontend uses to render a 'Falls on weekend' chip — the
 * server is the source of truth on Saudi workday math because the
 * F8 reminder scheduler depends on the same answer.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEventRepository       repo;
    private final CapaTaskRepository            capas;
    private final FindingRepository             findings;
    private final RiskRepository                risks;
    private final SaudiWorkdayCalendar          workdays;
    private final AuditLogService               audit;

    // ── Aggregated read ──────────────────────────────────────────────

    /**
     * Everything on the calendar between {@code from} (inclusive)
     * and {@code to} (exclusive). If {@code types} is non-null, only
     * events whose {@code eventType} is in the filter are returned.
     */
    @Transactional(readOnly = true)
    public List<CalendarEventDto> listInWindow(LocalDate from, LocalDate to, List<String> types) {
        Long orgId = TenantContext.requireOrganisationId();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atStartOfDay();

        List<CalendarEventDto> out = new ArrayList<>();

        // 1. Persisted user events + public holidays.
        for (CalendarEvent e : repo.findInWindow(orgId, fromDt, toDt)) {
            out.add(stamp(CalendarEventDto.fromEntity(e), orgId));
        }

        // 2. CAPA target dates — auto-aggregated as CAPA_DUE rows.
        for (CapaTask c : capas.findByOrganisationIdOrderByCreatedAtDesc(orgId)) {
            if (c.getTargetDate() == null) continue;
            if (isOutOfWindow(c.getTargetDate(), from, to)) continue;
            if ("CLOSED".equals(c.getStatus()) || "CANCELLED".equals(c.getStatus())) continue;
            out.add(stamp(synthFromCapa(c), orgId));
        }

        // 3. Finding remediation deadlines — auto-aggregated as
        //    FINDING_DEADLINE rows. Closed findings don't surface
        //    (their deadline is moot once verified).
        for (Finding f : findings.findByOrganisationIdOrderByRaisedAtDesc(orgId)) {
            if (f.getTargetDate() == null) continue;
            if (isOutOfWindow(f.getTargetDate(), from, to)) continue;
            if ("CLOSED".equals(f.getStatus())) continue;
            out.add(stamp(synthFromFinding(f), orgId));
        }

        // 4. Risk attestation cadence (V115) — RISK_ATTESTATION rows.
        //    Pulls reviewDate as a secondary anchor for risks without
        //    an attestation cadence set yet.
        for (Risk r : risks.findByOrganisationId(orgId, Pageable.unpaged())) {
            if ("CLOSED".equals(r.getStatus()) || "ACCEPTED".equals(r.getStatus())) continue;
            if (r.getNextAttestationDate() != null
                && !isOutOfWindow(r.getNextAttestationDate(), from, to)) {
                out.add(stamp(synthFromRiskAttestation(r), orgId));
            }
            if (r.getReviewDate() != null
                && !isOutOfWindow(r.getReviewDate(), from, to)) {
                out.add(stamp(synthFromRiskReview(r), orgId));
            }
        }

        // Filter by eventType if requested.
        if (types != null && !types.isEmpty()) {
            out.removeIf(e -> !types.contains(e.getEventType()));
        }

        // Sort chronologically; secondary by entityId for stability.
        out.sort(Comparator.comparing(CalendarEventDto::getStartsAt)
            .thenComparing(d -> d.getEntityId() == null ? 0L : d.getEntityId()));
        return out;
    }

    /** Quick "What's on the calendar" panel for an entity detail
     *  page — lists every persisted event linked to that entity. */
    @Transactional(readOnly = true)
    public List<CalendarEventDto> listForEntity(String entityType, Long entityId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByStartsAtAsc(
                orgId, entityType, entityId)
            .stream().map(e -> stamp(CalendarEventDto.fromEntity(e), orgId))
            .toList();
    }

    // ── CRUD on user-created events ─────────────────────────────────

    @Transactional
    public CalendarEventDto create(CalendarEventDto.CreateRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        CalendarEvent e = new CalendarEvent();
        e.setOrganisationId(orgId);
        applyCreate(e, req);
        e.setStatus("SCHEDULED");
        e = repo.save(e);
        audit.log("CREATE", "CalendarEvent", e.getId(),
            "Calendar event '" + safe(e.getTitleEn()) + "' (" + e.getEventType() + ") on "
            + e.getStartsAt().toLocalDate());
        return stamp(CalendarEventDto.fromEntity(e), orgId);
    }

    @Transactional
    public CalendarEventDto get(UUID externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        CalendarEvent e = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + externalId));
        return stamp(CalendarEventDto.fromEntity(e), orgId);
    }

    @Transactional
    public CalendarEventDto update(UUID externalId, CalendarEventDto.CreateRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        CalendarEvent e = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + externalId));
        // Public holidays are owned by the platform — refuse edits.
        if (Boolean.TRUE.equals(e.getIsHoliday())) {
            throw new IllegalStateException("Public holidays are read-only.");
        }
        applyCreate(e, req);
        e.setUpdatedAt(LocalDateTime.now());
        e = repo.save(e);
        audit.log("UPDATE", "CalendarEvent", e.getId(),
            "Calendar event '" + safe(e.getTitleEn()) + "' updated");
        return stamp(CalendarEventDto.fromEntity(e), orgId);
    }

    @Transactional
    public void delete(UUID externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        CalendarEvent e = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + externalId));
        if (Boolean.TRUE.equals(e.getIsHoliday())) {
            throw new IllegalStateException("Public holidays are read-only.");
        }
        e.setIsDeleted(Boolean.TRUE);
        repo.save(e);
        audit.log("DELETE", "CalendarEvent", e.getId(),
            "Calendar event '" + safe(e.getTitleEn()) + "' soft-deleted");
    }

    // ── Synthetic-row builders ──────────────────────────────────────

    private CalendarEventDto synthFromCapa(CapaTask c) {
        return CalendarEventDto.builder()
            .source("AGGREGATED")
            .externalId(synthId("CapaTask", c.getId()))
            .titleEn("CAPA due: " + safe(c.getTitleEn()))
            .titleAr("استحقاق إجراء: " + safe(c.getTitleAr()))
            .eventType("CAPA_DUE")
            .startsAt(c.getTargetDate().atStartOfDay())
            .allDay(true)
            .severity(c.getSeverity())
            .entityType("CapaTask")
            .entityId(c.getId())
            .ownerLabel(c.getOwner())
            .status(c.getStatus())
            .build();
    }

    private CalendarEventDto synthFromFinding(Finding f) {
        return CalendarEventDto.builder()
            .source("AGGREGATED")
            .externalId(synthId("Finding", f.getId()))
            .titleEn("Finding remediation: " + safe(f.getTitleEn()))
            .titleAr("معالجة نتيجة: " + safe(f.getTitleAr()))
            .eventType("FINDING_DEADLINE")
            .startsAt(f.getTargetDate().atStartOfDay())
            .allDay(true)
            .severity(f.getSeverity())
            .entityType("Finding")
            .entityId(f.getId())
            .ownerLabel(f.getOwnerRole())
            .status(f.getStatus())
            .build();
    }

    private CalendarEventDto synthFromRiskAttestation(Risk r) {
        return CalendarEventDto.builder()
            .source("AGGREGATED")
            .externalId(synthId("Risk:attest", r.getId()))
            .titleEn("Risk attestation due: " + safe(r.getTitleEn()))
            .titleAr("توكيد خطر مستحق: " + safe(r.getTitleAr()))
            .eventType("RISK_ATTESTATION")
            .startsAt(r.getNextAttestationDate().atStartOfDay())
            .allDay(true)
            .severity(r.getRiskLevel())
            .entityType("Risk")
            .entityId(r.getId())
            .status(r.getStatus())
            .build();
    }

    private CalendarEventDto synthFromRiskReview(Risk r) {
        return CalendarEventDto.builder()
            .source("AGGREGATED")
            .externalId(synthId("Risk:review", r.getId()))
            .titleEn("Risk review: " + safe(r.getTitleEn()))
            .titleAr("مراجعة خطر: " + safe(r.getTitleAr()))
            .eventType("AAR_REVIEW")  // reused as a generic "review" pill
            .startsAt(r.getReviewDate().atStartOfDay())
            .allDay(true)
            .severity(r.getRiskLevel())
            .entityType("Risk")
            .entityId(r.getId())
            .status(r.getStatus())
            .build();
    }

    /** Deterministic UUID per synthetic row so the client can use it
     *  as a React key without collisions across page refreshes. */
    private UUID synthId(String kind, Long entityId) {
        return UUID.nameUUIDFromBytes((kind + ":" + entityId).getBytes());
    }

    private CalendarEventDto stamp(CalendarEventDto dto, Long orgId) {
        if (dto.getStartsAt() != null) {
            dto.setOnWorkday(workdays.isWorkday(orgId, dto.getStartsAt().toLocalDate()));
        }
        return dto;
    }

    private void applyCreate(CalendarEvent e, CalendarEventDto.CreateRequest req) {
        e.setTitleEn(req.getTitleEn());
        e.setTitleAr(req.getTitleAr());
        e.setDescriptionEn(req.getDescriptionEn());
        e.setDescriptionAr(req.getDescriptionAr());
        e.setEventType(req.getEventType());
        e.setStartsAt(req.getStartsAt());
        e.setEndsAt(req.getEndsAt());
        e.setAllDay(req.getAllDay() == null ? Boolean.TRUE : req.getAllDay());
        e.setLocation(req.getLocation());
        e.setOwnerId(req.getOwnerId());
        e.setSeverity(req.getSeverity());
        e.setEntityType(req.getEntityType());
        e.setEntityId(req.getEntityId());
        e.setReminderDays(req.getReminderDays() == null ? "7,1" : req.getReminderDays());
        e.setRecurrence(req.getRecurrence() == null ? "NONE" : req.getRecurrence());
        e.setRecurrenceUntil(req.getRecurrenceUntil());
    }

    private static boolean isOutOfWindow(LocalDate d, LocalDate from, LocalDate to) {
        return d.isBefore(from) || !d.isBefore(to);  // [from, to)
    }

    private static String safe(String v) { return v == null ? "" : v; }

    /** Cosmetic null-safe wrap for UI labels — never returns null. */
    @SuppressWarnings("unused")
    private static String label(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Suppresses an unused-method warning on {@link Objects}. */
    @SuppressWarnings("unused")
    private static final boolean OBJECTS_KEEP_IMPORT = Objects.equals("a", "a");
}
