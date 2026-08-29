package com.tss.grc.module.incident.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.incident.dto.*;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Incident service — NIST SP 800-61r2 lifecycle + Saudi regulator
 * notification flow.
 *
 * <p>Wired into the F7 (audit log) and F8 (automation rules) loops
 * via {@link AuditLogService#log} + {@link ApplicationEventPublisher
 * #publishEvent}. Every state mutation now writes an audit row AND
 * publishes a canonical {@link DomainEvent} the rule engine
 * evaluates. Event vocabulary:
 *
 *   - INCIDENT_CREATED        — new incident logged
 *   - INCIDENT_PHASE_CHANGED  — NIST phase advance (carries previousPhase)
 *   - INCIDENT_CLOSED         — terminal close (resolutionDate stamped)
 *   - INCIDENT_REGULATOR_NOTIFIED — one of NCA_HEY / DATA_OFFICE / CITC /
 *                              SAMA / HRSD recorded
 *
 * <p>The audit + event emission happens INSIDE the existing
 * @Transactional methods so the writes commit together with the
 * mutation — no risk of an event firing for a transaction that later
 * rolls back.
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    /** CY-C: auto-raise a corrective CAPA from an incident's lessons-learned on close. */
    private final CapaTaskService capaService;

    /** Days to close the auto-raised post-incident CAPA. */
    private static final int CAPA_DUE_DAYS = 30;
    /** Fallback owner when the incident has no assignee (CISO role, seeded by SOC-A). */
    private static final String CAPA_DEFAULT_OWNER = "CISO";

    /** Optional — null in unit tests, auto-wired by Spring at runtime. */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    private void broadcast(Long orgId, Long id, Object payload) {
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "incident", String.valueOf(id), payload);
    }

    /** Build the F8 payload that downstream rules condition on. */
    private Map<String, Object> payloadFor(Incident i, String previousPhase) {
        Map<String, Object> m = new HashMap<>();
        m.put("incidentId",     nullSafeStr(i.getId()));
        m.put("title",          nullSafeStr(i.getTitle()));
        m.put("titleAr",        nullSafeStr(i.getTitleAr()));
        m.put("severity",       nullSafeStr(i.getSeverity()));
        m.put("category",       nullSafeStr(i.getCategory()));
        m.put("status",         nullSafeStr(i.getStatus()));
        m.put("nistPhase",      nullSafeStr(i.getNistPhase()));
        m.put("previousPhase",  nullSafeStr(previousPhase));
        m.put("assignee",       nullSafeStr(i.getAssignee()));
        m.put("mitreTactics",   nullSafeStr(i.getMitreTactics()));
        m.put("mitreTechniques",nullSafeStr(i.getMitreTechniques()));
        return m;
    }

    /** Always returns a non-null String so the rule engine's equality
     *  matcher doesn't NPE on null payload values. */
    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }

    private void emit(Long orgId, String trigger, Incident i, String previousPhase) {
        publisher.publishEvent(new DomainEvent(
            orgId, trigger, "Incident", i.getId(),
            "incident:" + i.getId(), payloadFor(i, previousPhase)));
    }

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
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = new Incident();
        i.setOrganisationId(orgId);
        apply(i, req);
        i = incidentRepo.save(i);
        audit.log("CREATE", "Incident", i.getId(),
            "Incident raised: " + nullSafe(i.getTitle()) + " — " + nullSafe(i.getSeverity()));
        emit(orgId, "INCIDENT_CREATED", i, null);
        IncidentResponse out = toResponse(i);
        broadcast(orgId, out.id(), out);
        return out;
    }

    @Transactional
    public IncidentResponse update(Long id, IncidentRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = require(id);
        apply(i, req);
        IncidentResponse out = toResponse(incidentRepo.save(i));
        broadcast(orgId, id, out);
        return out;
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = require(id);
        i.setIsDeleted(true);
        incidentRepo.save(i);
        broadcast(orgId, id, java.util.Map.of("id", id, "deleted", true));
    }

    // ── CY-B: /cyber SOC workspace ↔ canonical register bridge ────────────────
    // The /cyber workspace (cyber.cyber_incidents) is the SOC operating view; the
    // register (cyber.incidents — this entity) is the system of record read by the
    // dashboard KPI, the KPI trend and the PDPL breach bridge. These methods let a
    // workspace incident register a linked canonical row and keep it in step, so a
    // cyber-workspace incident finally reaches the dashboard + can be PDPL-linked.

    private static final java.util.Set<String> LEGAL_SEVERITY =
        java.util.Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final java.util.Set<String> LEGAL_CATEGORY =
        java.util.Set.of("OTHER", "ACCESS_CONTROL", "INFRASTRUCTURE", "DATA_BREACH", "CYBER");

    /**
     * Register (or return the existing) canonical {@code cyber.incidents} row that
     * mirrors a /cyber SOC-workspace incident. Idempotent per workspace incident
     * (guarded by {@code source_workspace_incident_id} + a unique index). Fires the
     * same audit + F8 {@code INCIDENT_CREATED} event as a normal incident create, so
     * the mirror is a first-class incident, not a silent side-table row.
     *
     * @return the canonical register row id.
     */
    @Transactional
    public Long registerFromWorkspace(Long orgId, Long workspaceIncidentId,
                                      String titleEn, String titleAr, String severity,
                                      String category, String status,
                                      java.time.LocalDate discoveryDate, String description) {
        var existing = incidentRepo.findByOrganisationIdAndSourceWorkspaceIncidentId(orgId, workspaceIncidentId);
        if (existing.isPresent()) return existing.get().getId();

        String sev = severity == null ? "MEDIUM" : severity.trim().toUpperCase();
        if (!LEGAL_SEVERITY.contains(sev)) sev = "MEDIUM";
        String cat = category == null ? "CYBER" : category.trim().toUpperCase();
        if (!LEGAL_CATEGORY.contains(cat)) cat = "CYBER";   // CK_incidents_category is strict

        Incident i = new Incident();
        i.setOrganisationId(orgId);
        i.setSourceWorkspaceIncidentId(workspaceIncidentId);
        i.setTitle(titleEn != null && !titleEn.isBlank() ? titleEn : "Cyber incident #" + workspaceIncidentId);
        i.setTitleAr(titleAr);
        i.setSeverity(sev);
        i.setCategory(cat);
        i.setStatus(legalStatus(status));
        i.setNistPhase("IDENTIFICATION");
        i.setDiscoveryDate(discoveryDate);
        i.setDescription(description);
        i = incidentRepo.save(i);
        audit.log("CREATE", "Incident", i.getId(),
            "Registered from /cyber workspace incident #" + workspaceIncidentId + " — " + nullSafe(i.getTitle()));
        emit(orgId, "INCIDENT_CREATED", i, null);
        broadcast(orgId, i.getId(), toResponse(i));
        return i.getId();
    }

    /**
     * Keep the canonical register row's status in step with the workspace incident's
     * workflow as it advances/closes, so the dashboard open-count stays live. No-op
     * if the workspace incident has no registered mirror (e.g. a legacy row).
     */
    @Transactional
    public void syncStatusFromWorkspace(Long orgId, Long workspaceIncidentId, String status) {
        if (workspaceIncidentId == null) return;
        incidentRepo.findByOrganisationIdAndSourceWorkspaceIncidentId(orgId, workspaceIncidentId)
            .ifPresent(i -> {
                String next = legalStatus(status);
                if (!next.equals(i.getStatus())) {
                    String prev = i.getStatus();
                    i.setStatus(next);
                    if (("RESOLVED".equals(next) || "CLOSED".equals(next)) && i.getResolutionDate() == null) {
                        i.setResolutionDate(java.time.LocalDate.now());
                    }
                    i = incidentRepo.save(i);
                    audit.log("STATUS_SYNC", "Incident", i.getId(),
                        "Status " + nullSafe(prev) + " → " + next + " (from workspace incident #" + workspaceIncidentId + ")");
                }
            });
    }

    /** Soft-delete the canonical mirror when its workspace incident is deleted. */
    @Transactional
    public void softDeleteFromWorkspace(Long orgId, Long workspaceIncidentId) {
        if (workspaceIncidentId == null) return;
        incidentRepo.findByOrganisationIdAndSourceWorkspaceIncidentId(orgId, workspaceIncidentId)
            .ifPresent(i -> {
                i.setIsDeleted(true);
                incidentRepo.save(i);
                audit.log("DELETE", "Incident", i.getId(),
                    "Register mirror soft-deleted with workspace incident #" + workspaceIncidentId);
            });
    }

    private static String legalStatus(String s) {
        String v = s == null ? "OPEN" : s.trim().toUpperCase();
        return switch (v) {
            case "OPEN", "INVESTIGATING", "RESOLVED", "CLOSED" -> v;
            default -> "OPEN";   // CK_incidents_status is strict
        };
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

        // Average resolution time in days over ALL resolved incidents (those with both a
        // discovery and a resolution date) — matching the all-time counts above. F2-345: this
        // used to average over findRecent(200), the 200 newest-by-createdAt, which windowed the
        // figure and biased it downward at scale.
        List<Incident> resolvedList = incidentRepo.findResolvedWithDates(orgId);
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

    // ── NIST SP 800-61r2 lifecycle ────────────────────────────────────────────
    // State machine:
    //   IDENTIFICATION → ANALYSIS → CONTAINMENT → ERADICATION → RECOVERY → POST_INCIDENT
    // The high-level `status` column is auto-mapped from `nist_phase` so existing
    // dashboards and CHECK constraint (OPEN|INVESTIGATING|RESOLVED|CLOSED) keep working.

    private static final java.util.List<String> NIST_PHASES = java.util.List.of(
        "IDENTIFICATION", "ANALYSIS", "CONTAINMENT", "ERADICATION", "RECOVERY", "POST_INCIDENT");

    @Transactional
    public java.util.Map<String, Object> advancePhase(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = require(id);
        int idx = NIST_PHASES.indexOf(i.getNistPhase());
        if (idx < 0) idx = 0;
        if (idx >= NIST_PHASES.size() - 1) {
            throw new IllegalArgumentException("Already at terminal phase POST_INCIDENT — call /close to finalise");
        }
        String previousPhase = i.getNistPhase();
        String next = NIST_PHASES.get(idx + 1);
        i.setNistPhase(next);
        i.setStatus(mapPhaseToStatus(next));
        i = incidentRepo.save(i);
        audit.log("PHASE_ADVANCE", "Incident", i.getId(),
            "Phase " + nullSafe(previousPhase) + " → " + next);
        emit(orgId, "INCIDENT_PHASE_CHANGED", i, previousPhase);
        java.util.Map<String, Object> snap = phaseSnapshot(i);
        broadcast(orgId, id, snap);
        return snap;
    }

    @Transactional
    public java.util.Map<String, Object> setPhase(Long id, String phase) {
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = require(id);
        if (!NIST_PHASES.contains(phase)) {
            throw new IllegalArgumentException("phase must be one of " + NIST_PHASES);
        }
        String previousPhase = i.getNistPhase();
        i.setNistPhase(phase);
        i.setStatus(mapPhaseToStatus(phase));
        i = incidentRepo.save(i);
        audit.log("PHASE_SET", "Incident", i.getId(),
            "Phase set to " + phase + " (was " + nullSafe(previousPhase) + ")");
        emit(orgId, "INCIDENT_PHASE_CHANGED", i, previousPhase);
        java.util.Map<String, Object> snap = phaseSnapshot(i);
        broadcast(orgId, id, snap);
        return snap;
    }

    @Transactional
    public java.util.Map<String, Object> closeIncident(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = require(id);
        String previousPhase = i.getNistPhase();
        i.setStatus("CLOSED");
        if (i.getResolutionDate() == null) i.setResolutionDate(java.time.LocalDate.now());
        i = incidentRepo.save(i);
        audit.log("CLOSE", "Incident", i.getId(),
            "Incident closed — resolution date " + i.getResolutionDate());
        emit(orgId, "INCIDENT_CLOSED", i, previousPhase);
        // CY-C: turn the lessons-learned into a tracked, routed corrective CAPA.
        String capaId = raiseCapaFromLessons(i);
        java.util.Map<String, Object> snap = phaseSnapshot(i);
        if (capaId != null) snap.put("capaRaised", capaId);
        broadcast(orgId, id, snap);
        return snap;
    }

    /**
     * CY-C: when an incident is closed carrying lessons-learned, auto-open a
     * CORRECTIVE CAPA routed to an owner with a due date, so post-incident actions
     * are tracked to closure instead of dying as free text (confirmed gap: reaching
     * closure previously created 0 action plans). No lessons → no CAPA. Runs in the
     * close transaction so the incident and its follow-up CAPA commit together.
     */
    private String raiseCapaFromLessons(Incident i) {
        String lessons = i.getLessonsLearned();
        if (lessons == null || lessons.isBlank()) return null;

        String base = (i.getTitle() != null && !i.getTitle().isBlank()) ? i.getTitle() : ("Incident #" + i.getId());
        String owner = (i.getAssignee() != null && !i.getAssignee().isBlank()) ? i.getAssignee() : CAPA_DEFAULT_OWNER;
        String sev   = (i.getSeverity() != null && !i.getSeverity().isBlank()) ? i.getSeverity() : "MEDIUM";
        String titleEn = clip("Post-incident corrective actions — " + base, 500);
        String titleAr = clip("إجراءات تصحيحية بعد الحادثة — "
                + (i.getTitleAr() != null && !i.getTitleAr().isBlank() ? i.getTitleAr() : base), 500);

        CapaTaskDto dto = CapaTaskDto.builder()
                .originType("incident")          // matches the existing origin_type convention
                .originId(String.valueOf(i.getId()))
                .titleEn(titleEn)
                .titleAr(titleAr)
                .descriptionEn("Lessons learned from incident \"" + base + "\":\n" + lessons)
                .descriptionAr(i.getLessonsLearnedAr())
                .capaType("CORRECTIVE")
                .severity(sev)
                .owner(owner)
                .targetDate(java.time.LocalDate.now().plusDays(CAPA_DUE_DAYS))
                .build();
        CapaTaskDto created = capaService.create(dto);
        audit.log("CAPA_AUTO_RAISED", "Incident", i.getId(),
            "Auto-raised CAPA " + created.getId() + " from lessons learned (owner=" + owner
                + ", due=" + dto.getTargetDate() + ")");
        return created.getId();
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String mapPhaseToStatus(String phase) {
        return switch (phase) {
            case "IDENTIFICATION"            -> "OPEN";
            case "ANALYSIS", "CONTAINMENT", "ERADICATION" -> "INVESTIGATING";
            case "RECOVERY", "POST_INCIDENT" -> "RESOLVED";
            default -> "OPEN";
        };
    }

    // ── Saudi regulator notification ─────────────────────────────────────────

    @Transactional
    public java.util.Map<String, Object> notifyRegulator(Long id, String regulator) {
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = require(id);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String reg = regulator == null ? "" : regulator.toUpperCase();
        switch (reg) {
            case "NCA_HEY"     -> i.setNcaHeyNotifiedAt(now);
            case "DATA_OFFICE" -> i.setDataOfficeNotifiedAt(now);
            case "CITC"        -> i.setCitcNotifiedAt(now);
            case "SAMA"        -> i.setSamaNotifiedAt(now);
            case "HRSD"        -> i.setHrsdNotifiedAt(now);
            default -> throw new IllegalArgumentException("regulator must be one of NCA_HEY|DATA_OFFICE|CITC|SAMA|HRSD");
        }
        i = incidentRepo.save(i);
        audit.log("REGULATOR_NOTIFIED", "Incident", i.getId(),
            "Regulator " + reg + " notified at " + now);
        // Single canonical event with regulator in payload — rules
        // match equality on `regulator` to route the right follow-up
        // (e.g. PDPL DSR clock, NCA CMU coordination, SAMA reporting
        // ladder).
        Map<String, Object> p = payloadFor(i, null);
        p.put("regulator", reg);
        p.put("notifiedAt", now.toString());
        publisher.publishEvent(new DomainEvent(
            orgId, "INCIDENT_REGULATOR_NOTIFIED", "Incident", i.getId(),
            "incident:" + i.getId() + ":" + reg, p));
        java.util.Map<String, Object> snap = phaseSnapshot(i);
        broadcast(orgId, id, snap);
        return snap;
    }

    /**
     * Returns the Saudi regulatory notification templates applicable to this
     * incident, with placeholders auto-filled from the incident record. The
     * frontend can present these as one-click "generate notification package"
     * buttons. Each template carries its own deadline and the regulator-side
     * submission channel.
     */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> regulatoryTemplates(Long id) {
        Incident i = require(id);
        java.time.LocalDateTime discovered = i.getDiscoveryDate() != null
            ? i.getDiscoveryDate().atStartOfDay()
            : (i.getCreatedAt() != null ? i.getCreatedAt() : java.time.LocalDateTime.now());
        long hoursSinceDiscovery = java.time.temporal.ChronoUnit.HOURS.between(discovered, java.time.LocalDateTime.now());
        // F2-281: one incident-level fact, resolved once and carried by every
        // template, so no template can hold its own idea of the classification.
        String piiClassification = i.isPiiConfirmed() ? "CONFIRMED" : "NOT_CLASSIFIED";

        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();

        out.add(template(
            "NCA_HEY",
            "NCA HEY — 24-hour Cyber Incident Notification",
            "إخطار حادثة سيبرانية — هيئة الأمن السيبراني (24 ساعة)",
            24, hoursSinceDiscovery, i.getNcaHeyNotifiedAt(),
            "https://hey.nca.gov.sa  (replace with operational submission URL)",
            "Cybersecurity incident with material impact — NCA mandate for all government and CNI",
            renderNcaHeyBody(i),
            piiClassification
        ));
        out.add(template(
            "DATA_OFFICE",
            "Saudi Data Office — 72-hour PDPL Article 20 Breach Notification",
            "إخطار خرق البيانات — مكتب البيانات السعودي (72 ساعة)",
            72, hoursSinceDiscovery, i.getDataOfficeNotifiedAt(),
            "Submit via Saudi Data Office portal once available",
            "Personal data breach — PDPL Article 20 mandates 72-hour notification",
            renderDataOfficeBody(i),
            piiClassification
        ));
        out.add(template(
            "CITC",
            "CITC — 24-hour Telecom / Cloud Incident Notification",
            "إخطار حادثة — هيئة الاتصالات والفضاء والتقنية (24 ساعة)",
            24, hoursSinceDiscovery, i.getCitcNotifiedAt(),
            "Submit via CITC operator portal",
            "For telecom and cloud-provider customers — CITC Cybersecurity Requirements",
            renderCitcBody(i),
            piiClassification
        ));
        out.add(template(
            "SAMA",
            "SAMA — Cyber Threat Intelligence Sharing",
            "مشاركة معلومات التهديدات السيبرانية — البنك المركزي السعودي",
            48, hoursSinceDiscovery, i.getSamaNotifiedAt(),
            "Submit via SAMA Cyber Threat Intelligence Sharing channel",
            "For SAMA-licensed financial institutions",
            renderSamaBody(i),
            piiClassification
        ));
        out.add(template(
            "HRSD",
            "HRSD — Employee Data Breach Notification",
            "إخطار خرق بيانات الموظفين — وزارة الموارد البشرية",
            72, hoursSinceDiscovery, i.getHrsdNotifiedAt(),
            "Submit via HRSD employer portal",
            "If the breach affects employee personal or payroll data",
            renderHrsdBody(i),
            piiClassification
        ));
        return out;
    }

    /**
     * F2-281 adds {@code piiClassification}.
     *
     * <p>Applicability was disclosed as prose for a human to judge — right for
     * SAMA ("for SAMA-licensed financial institutions") and CITC, which the
     * platform cannot know, and wrong for PDPL Article 20, which is exactly the
     * question {@code affects_personal_data} answers and which the platform had
     * stored on the row all along.
     *
     * <p>It is carried and NOT acted on: the template is still offered and
     * {@code overdue} is unchanged. Suppressing the notification for an
     * unclassified incident would turn "nobody has assessed this yet" into a
     * clean bill on a statutory clock, and under-scoping a statutory obligation
     * is the finding an examiner writes up (F2-196, F2-197).
     */
    // F2-390: package-private (was private) so IncidentServiceTest can drive the overdue
    // boundary deterministically — regulatoryTemplates() computes hoursSinceDiscovery from
    // LocalDateTime.now(), which a unit test cannot pin to an exact statutory deadline.
    java.util.Map<String, Object> template(String code, String titleEn, String titleAr,
                                                    int deadlineHours, long hoursSinceDiscovery,
                                                    java.time.LocalDateTime notifiedAt,
                                                    String submissionChannel, String applicabilityHint,
                                                    String prefilledBody,
                                                    String piiClassification) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("code", code);
        m.put("titleEn", titleEn);
        m.put("titleAr", titleAr);
        m.put("deadlineHours", deadlineHours);
        m.put("hoursSinceDiscovery", hoursSinceDiscovery);
        m.put("hoursRemaining", deadlineHours - hoursSinceDiscovery);
        // F2-390: a statutory deadline is breached AT the deadline, not an hour after it.
        // hoursSinceDiscovery is ChronoUnit.HOURS.between(...) floored to whole hours, so a
        // strict `< 0` (overdue only when floor(hours) > deadlineHours) reads "not overdue"
        // at the exact deadline AND for up to a full hour past it. Inclusive `<= 0` means
        // floor(hours) >= deadlineHours, i.e. realHours >= deadlineHours (deadlineHours is a
        // whole number), so overdue flips at the exact hour boundary — matching the sibling
        // RegulatorCountdownService (minutesLeft <= 0 -> BREACHED) for the same regulator clock.
        m.put("overdue", (deadlineHours - hoursSinceDiscovery) <= 0 && notifiedAt == null);
        m.put("notifiedAt", notifiedAt);
        m.put("submissionChannel", submissionChannel);
        m.put("applicabilityHint", applicabilityHint);
        m.put("prefilledBody", prefilledBody);
        m.put("piiClassification", piiClassification);
        return m;
    }

    private String renderNcaHeyBody(Incident i) {
        return "INCIDENT TITLE: " + nullSafe(i.getTitle())
            + "\nDISCOVERED: " + nullSafe(i.getDiscoveryDate())
            + "\nSEVERITY: " + nullSafe(i.getSeverity())
            + "\nCATEGORY: " + nullSafe(i.getCategory())
            + "\nMITRE TACTICS: " + nullSafe(i.getMitreTactics())
            + "\nMITRE TECHNIQUES: " + nullSafe(i.getMitreTechniques())
            + "\nCURRENT NIST PHASE: " + nullSafe(i.getNistPhase())
            + "\nDESCRIPTION:\n" + nullSafe(i.getDescription());
    }
    /**
      * PDPL Article 20 breach notification to the Saudi Data Office.
      *
      * <p>F2-281: this body omitted the number of affected data subjects and the
      * categories of personal data affected -- the two facts Article 20 turns on
      * -- while {@code cyber.incidents} stored both on the same row. Measured
      * live: incident 1 holds 1,450 subjects and EMAIL,NATIONAL_ID,PHONE_NUMBER
      * and neither appeared in the notification the platform generated for it.
      *
      * <p>An unassessed count renders as NOT YET ASSESSED and never as 0: on a
      * breach notification "0 data subjects affected" is a factual claim, and a
      * reset is worse than a null (F2-213). The three placeholders that remain
      * are the honest kind -- likely consequences, mitigation actions and the DPO
      * contact have no column anywhere, so there is nothing to fill them from
      * (F2-209's third state).
      */
     private String renderDataOfficeBody(Incident i) {
        return "BREACH NOTIFICATION — PDPL Article 20"
            + "\nINCIDENT TITLE: " + nullSafe(i.getTitle())
            + "\nDISCOVERED AT: " + nullSafe(i.getDiscoveryDate())
            + "\nNATURE OF BREACH: " + nullSafe(i.getCategory())
            + "\nSEVERITY: " + nullSafe(i.getSeverity())
            // The classification date renders as a plain DATE. Found in the browser
            // after every gate was green: `LocalDateTime.toString()` emits
            // `2026-05-07T14:29:52.134` -- a raw ISO stamp with milliseconds, two
            // lines under `DISCOVERED AT: 2026-04-05`, on a document going to a
            // regulator. F2-209 recorded exactly this blemish on the SDAIA console.
            + "\nPERSONAL DATA AFFECTED: " + (i.isPiiConfirmed()
                ? "CONFIRMED" + (i.getPiiClassifiedAt() == null ? ""
                    : " (classified " + i.getPiiClassifiedAt().toLocalDate() + ")")
                : "NOT YET CLASSIFIED — assess before submitting")
            + "\nDATA SUBJECTS AFFECTED (EST.): " + notAssessed(i.getDataSubjectsAffectedEst())
            + "\nCATEGORIES OF PERSONAL DATA AFFECTED: " + notAssessed(i.getDataCategoriesAffected())
            + "\nPII ASSESSMENT: " + notAssessed(i.getPersonalDataPiiAssessment())
            + "\nLINKED PDPL BREACH RECORD: " + (i.getLinkedDataBreachCode() == null
                ? "<none — no breach record has been raised for this incident>"
                : i.getLinkedDataBreachCode())
            + "\nLIKELY CONSEQUENCES: <to be completed by DPO>"
            + "\nMITIGATION ACTIONS: <to be completed>"
            + "\nDPO CONTACT: <to be completed>"
            + "\nDESCRIPTION:\n" + nullSafe(i.getDescription());
    }
    private String renderCitcBody(Incident i) {
        return "CITC TELECOM/CLOUD INCIDENT NOTIFICATION"
            + "\nINCIDENT TITLE: " + nullSafe(i.getTitle())
            + "\nDISCOVERED: " + nullSafe(i.getDiscoveryDate())
            + "\nSEVERITY: " + nullSafe(i.getSeverity())
            + "\nSERVICE IMPACT: <to be completed>"
            + "\nUSERS AFFECTED: <count>"
            + "\nCURRENT MITIGATION: <to be completed>"
            + "\nDESCRIPTION:\n" + nullSafe(i.getDescription());
    }
    private String renderSamaBody(Incident i) {
        return "SAMA CYBER THREAT INTELLIGENCE SHARING"
            + "\nINCIDENT TITLE: " + nullSafe(i.getTitle())
            + "\nDISCOVERED: " + nullSafe(i.getDiscoveryDate())
            + "\nSEVERITY: " + nullSafe(i.getSeverity())
            + "\nMITRE TACTICS: " + nullSafe(i.getMitreTactics())
            + "\nIOCs: <list of indicators of compromise>"
            + "\nDESCRIPTION:\n" + nullSafe(i.getDescription());
    }
    private String renderHrsdBody(Incident i) {
        return "HRSD EMPLOYEE-DATA BREACH NOTIFICATION"
            + "\nINCIDENT TITLE: " + nullSafe(i.getTitle())
            + "\nDISCOVERED: " + nullSafe(i.getDiscoveryDate())
            + "\nDATA CATEGORIES AFFECTED: " + notAssessed(i.getDataCategoriesAffected())
            + "\nEMPLOYEES AFFECTED: " + notAssessed(i.getDataSubjectsAffectedEst())
            + "\nMITIGATION: <actions taken>"
            + "\nDESCRIPTION:\n" + nullSafe(i.getDescription());
    }
    private static String nullSafe(Object o) { return o == null ? "<not provided>" : o.toString(); }

    /**
     * F2-281. Renders a classification field that has not been assessed yet.
     *
     * <p>Deliberately distinct from {@link #nullSafe}: "&lt;not provided&gt;" is
     * right for a field somebody simply did not fill in, and wrong for a
     * statutory count, where the reader has to be able to tell "nobody has
     * assessed this" from "assessed, and the answer is none". Blank counts as
     * absent, because a blank category list is not a statement either.
     */
    private static String notAssessed(Object o) {
        if (o == null || o.toString().isBlank()) return "<not yet assessed>";
        return o.toString();
    }

    /** Snapshot of NIST + Saudi-notification fields for the workflow endpoints to return. */
    private java.util.Map<String, Object> phaseSnapshot(Incident i) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("id",                     i.getId());
        m.put("status",                 i.getStatus());
        m.put("nistPhase",              i.getNistPhase());
        m.put("ncaHeyNotifiedAt",       i.getNcaHeyNotifiedAt());
        m.put("dataOfficeNotifiedAt",   i.getDataOfficeNotifiedAt());
        m.put("citcNotifiedAt",         i.getCitcNotifiedAt());
        m.put("samaNotifiedAt",         i.getSamaNotifiedAt());
        m.put("hrsdNotifiedAt",         i.getHrsdNotifiedAt());
        m.put("mitreTactics",           i.getMitreTactics());
        m.put("mitreTechniques",        i.getMitreTechniques());
        // F2-281: this snapshot carried dataOfficeNotifiedAt -- the PDPL Article 20
        // notification stamp -- and not the classification that makes that
        // notification owed. Two of N, in one map (F2-215).
        m.put("affectsPersonalData",    i.isPiiConfirmed());
        m.put("piiClassifiedAt",        i.getPiiClassifiedAt());
        m.put("linkedDataBreachCode",   i.getLinkedDataBreachCode());
        return m;
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
            i.getCreatedAt(), i.getUpdatedAt(),
            i.isPiiConfirmed(), i.getDataSubjectsAffectedEst(),
            i.getDataCategoriesAffected(), i.getPersonalDataPiiAssessment(),
            i.getLinkedDataBreachId(), i.getLinkedDataBreachCode(),
            i.getPiiClassifiedAt(), i.getPiiClassifiedBy()
        );
    }
}
