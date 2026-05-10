package com.tss.grc.module.incident.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.incident.dto.*;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** Optional — null in unit tests, auto-wired by Spring at runtime. */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    private void broadcast(Long orgId, Long id, Object payload) {
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "incident", String.valueOf(id), payload);
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
        IncidentResponse out = toResponse(incidentRepo.save(i));
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
        String next = NIST_PHASES.get(idx + 1);
        i.setNistPhase(next);
        i.setStatus(mapPhaseToStatus(next));
        java.util.Map<String, Object> snap = phaseSnapshot(incidentRepo.save(i));
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
        i.setNistPhase(phase);
        i.setStatus(mapPhaseToStatus(phase));
        java.util.Map<String, Object> snap = phaseSnapshot(incidentRepo.save(i));
        broadcast(orgId, id, snap);
        return snap;
    }

    @Transactional
    public java.util.Map<String, Object> closeIncident(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Incident i = require(id);
        i.setStatus("CLOSED");
        if (i.getResolutionDate() == null) i.setResolutionDate(java.time.LocalDate.now());
        java.util.Map<String, Object> snap = phaseSnapshot(incidentRepo.save(i));
        broadcast(orgId, id, snap);
        return snap;
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
        switch (regulator.toUpperCase()) {
            case "NCA_HEY"     -> i.setNcaHeyNotifiedAt(now);
            case "DATA_OFFICE" -> i.setDataOfficeNotifiedAt(now);
            case "CITC"        -> i.setCitcNotifiedAt(now);
            case "SAMA"        -> i.setSamaNotifiedAt(now);
            case "HRSD"        -> i.setHrsdNotifiedAt(now);
            default -> throw new IllegalArgumentException("regulator must be one of NCA_HEY|DATA_OFFICE|CITC|SAMA|HRSD");
        }
        java.util.Map<String, Object> snap = phaseSnapshot(incidentRepo.save(i));
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

        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();

        out.add(template(
            "NCA_HEY",
            "NCA HEY — 24-hour Cyber Incident Notification",
            "إخطار حادثة سيبرانية — هيئة الأمن السيبراني (24 ساعة)",
            24, hoursSinceDiscovery, i.getNcaHeyNotifiedAt(),
            "https://hey.nca.gov.sa  (replace with operational submission URL)",
            "Cybersecurity incident with material impact — NCA mandate for all government and CNI",
            renderNcaHeyBody(i)
        ));
        out.add(template(
            "DATA_OFFICE",
            "Saudi Data Office — 72-hour PDPL Article 20 Breach Notification",
            "إخطار خرق البيانات — مكتب البيانات السعودي (72 ساعة)",
            72, hoursSinceDiscovery, i.getDataOfficeNotifiedAt(),
            "Submit via Saudi Data Office portal once available",
            "Personal data breach — PDPL Article 20 mandates 72-hour notification",
            renderDataOfficeBody(i)
        ));
        out.add(template(
            "CITC",
            "CITC — 24-hour Telecom / Cloud Incident Notification",
            "إخطار حادثة — هيئة الاتصالات والفضاء والتقنية (24 ساعة)",
            24, hoursSinceDiscovery, i.getCitcNotifiedAt(),
            "Submit via CITC operator portal",
            "For telecom and cloud-provider customers — CITC Cybersecurity Requirements",
            renderCitcBody(i)
        ));
        out.add(template(
            "SAMA",
            "SAMA — Cyber Threat Intelligence Sharing",
            "مشاركة معلومات التهديدات السيبرانية — البنك المركزي السعودي",
            48, hoursSinceDiscovery, i.getSamaNotifiedAt(),
            "Submit via SAMA Cyber Threat Intelligence Sharing channel",
            "For SAMA-licensed financial institutions",
            renderSamaBody(i)
        ));
        out.add(template(
            "HRSD",
            "HRSD — Employee Data Breach Notification",
            "إخطار خرق بيانات الموظفين — وزارة الموارد البشرية",
            72, hoursSinceDiscovery, i.getHrsdNotifiedAt(),
            "Submit via HRSD employer portal",
            "If the breach affects employee personal or payroll data",
            renderHrsdBody(i)
        ));
        return out;
    }

    private java.util.Map<String, Object> template(String code, String titleEn, String titleAr,
                                                    int deadlineHours, long hoursSinceDiscovery,
                                                    java.time.LocalDateTime notifiedAt,
                                                    String submissionChannel, String applicabilityHint,
                                                    String prefilledBody) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("code", code);
        m.put("titleEn", titleEn);
        m.put("titleAr", titleAr);
        m.put("deadlineHours", deadlineHours);
        m.put("hoursSinceDiscovery", hoursSinceDiscovery);
        m.put("hoursRemaining", deadlineHours - hoursSinceDiscovery);
        m.put("overdue", (deadlineHours - hoursSinceDiscovery) < 0 && notifiedAt == null);
        m.put("notifiedAt", notifiedAt);
        m.put("submissionChannel", submissionChannel);
        m.put("applicabilityHint", applicabilityHint);
        m.put("prefilledBody", prefilledBody);
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
    private String renderDataOfficeBody(Incident i) {
        return "BREACH NOTIFICATION — PDPL Article 20"
            + "\nINCIDENT TITLE: " + nullSafe(i.getTitle())
            + "\nDISCOVERED AT: " + nullSafe(i.getDiscoveryDate())
            + "\nNATURE OF BREACH: " + nullSafe(i.getCategory())
            + "\nSEVERITY: " + nullSafe(i.getSeverity())
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
            + "\nDATA CATEGORIES AFFECTED: <e.g. salary, ID, contract>"
            + "\nEMPLOYEES AFFECTED: <count>"
            + "\nMITIGATION: <actions taken>"
            + "\nDESCRIPTION:\n" + nullSafe(i.getDescription());
    }
    private static String nullSafe(Object o) { return o == null ? "<not provided>" : o.toString(); }

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
            i.getCreatedAt(), i.getUpdatedAt()
        );
    }
}
