package com.tss.grc.module.governance.pivot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import com.tss.grc.module.ccm.entity.ControlTestRun;
import com.tss.grc.module.ccm.repository.ControlTestDefinitionRepository;
import com.tss.grc.module.ccm.repository.ControlTestRunRepository;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.CapaPivot;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.CcmCoverageStats;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.ControlPivot;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.ControlSummary;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.FindingPivot;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.FindingSummary;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.FocalEntity;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.PivotResponse;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.PivotSummary;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.RiskPivot;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.RiskSummary;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.entity.ControlAssessment;
import com.tss.grc.module.platform.controls.entity.RiskControlLink;
import com.tss.grc.module.platform.controls.repository.ControlAssessmentRepository;
import com.tss.grc.module.platform.controls.repository.ControlRepository;
import com.tss.grc.module.platform.controls.repository.RiskControlLinkRepository;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Risk-Control-Finding Pivot service — Sprint 11.
 *
 * <p>Pure aggregation across existing modules. Owns no tables. Three
 * lenses share a single response shape; the {@code focalEntity}
 * discriminator on the response tells the caller which entity is at
 * the centre.
 *
 * <p>F7 audit log + F8 {@code PIVOT_VIEW_GENERATED} on every pivot
 * read — the analytics signal that surfaces which entities are
 * audited most often (drives "frequently reviewed" suggestions on the
 * dashboard later).
 *
 * <p>Tenant isolation: each pivot resolves its focal entity through
 * the source-module repository finder that takes
 * {@code organisationId}. If the focal entity is invisible under the
 * current tenant, the service throws
 * {@link EntityNotFoundException}, which the controller maps to 404.
 *
 * <p>Graceful missing-link handling: a risk with zero linked
 * controls is a legitimate state ("uncontrolled risk"). Sections
 * collapse to empty arrays rather than throw. The
 * {@code dataCompletenessPct} on {@code PivotSummary} surfaces the
 * gap.
 */
@Slf4j
@Service
public class RiskControlFindingPivotService {

    /** CAPA statuses considered open. */
    static final List<String> OPEN_CAPA_STATUSES = List.of(
            "OPEN", "IN_PROGRESS", "AWAITING_VERIFICATION", "OVERDUE");
    /** Finding statuses considered open. */
    static final List<String> OPEN_FINDING_STATUSES = List.of(
            "OPEN", "MGMT_RESPONSE", "IN_REMEDIATION", "AWAITING_VERIFICATION");

    private final RiskRepository riskRepo;
    private final ControlRepository controlRepo;
    private final RiskControlLinkRepository riskControlLinkRepo;
    private final ControlAssessmentRepository controlAssessmentRepo;
    private final FindingRepository findingRepo;
    /** Bean qualifier — the unified capa.tasks repository (Sprint 11 source of truth). */
    private final CapaTaskRepository capaRepo;
    /** Bean qualifier — the legacy audit_lc.capa_tasks repository (carries finding_id linkage). */
    private final com.tss.grc.module.audit_lc.repository.CapaTaskRepository auditLcCapaRepo;
    private final ControlTestDefinitionRepository ccmDefRepo;
    private final ControlTestRunRepository ccmRunRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    public RiskControlFindingPivotService(
            RiskRepository riskRepo,
            ControlRepository controlRepo,
            RiskControlLinkRepository riskControlLinkRepo,
            ControlAssessmentRepository controlAssessmentRepo,
            FindingRepository findingRepo,
            CapaTaskRepository capaRepo,
            @Qualifier("auditLcCapaTaskRepository")
            com.tss.grc.module.audit_lc.repository.CapaTaskRepository auditLcCapaRepo,
            ControlTestDefinitionRepository ccmDefRepo,
            ControlTestRunRepository ccmRunRepo,
            AuditLogService audit,
            ApplicationEventPublisher eventPublisher) {
        this.riskRepo = riskRepo;
        this.controlRepo = controlRepo;
        this.riskControlLinkRepo = riskControlLinkRepo;
        this.controlAssessmentRepo = controlAssessmentRepo;
        this.findingRepo = findingRepo;
        this.capaRepo = capaRepo;
        this.auditLcCapaRepo = auditLcCapaRepo;
        this.ccmDefRepo = ccmDefRepo;
        this.ccmRunRepo = ccmRunRepo;
        this.audit = audit;
        this.eventPublisher = eventPublisher;
    }

    // ─── Public API ────────────────────────────────────────────────

    /**
     * Pivot around a single risk — surrounding cards are linked
     * controls, findings raised against those controls, open CAPA
     * remediating those findings, CCM coverage rollup.
     */
    @Transactional(readOnly = true)
    public PivotResponse buildRiskPivot(Long riskId) {
        Long orgId = TenantContext.requireOrganisationId();
        Risk r = mustGetRisk(orgId, riskId);

        List<RiskControlLink> links = safeList(() ->
                riskControlLinkRepo.findByOrganisationIdAndRiskIdOrderByIdAsc(orgId, riskId));
        // PERF-NQ-004: load the org's CCM test definitions ONCE per pivot (grouped
        // by controlId) instead of re-scanning the whole table inside toControlPivot
        // for every control and again in ccmCoverageForControls.
        Map<Long, List<ControlTestDefinition>> defsByControl = loadDefsByControl(orgId);
        List<ControlPivot> controlRows = new ArrayList<>();
        List<Long> controlIds = new ArrayList<>();
        for (RiskControlLink link : links) {
            Control c = safeOpt(() -> controlRepo.findByIdAndOrganisationId(link.getControlId(), orgId))
                    .orElse(null);
            if (c == null) continue;
            controlIds.add(c.getId());
            controlRows.add(toControlPivot(c, link.getEffectiveness(), orgId, defsByControl));
        }

        List<FindingPivot> findingRows = findingsForControls(orgId, controlRows);
        List<CapaPivot> capaRows = capasForFindings(orgId, findingRows);
        CcmCoverageStats ccm = ccmCoverageForControls(orgId, controlIds, defsByControl);

        PivotResponse out = new PivotResponse(
                FocalEntity.RISK,
                LocalDateTime.now(),
                toRiskSummary(r),
                null,
                null,
                List.of(),
                controlRows,
                findingRows,
                capaRows,
                ccm,
                summarise(controlRows, findingRows, capaRows, List.of()));

        emitPivotEvent(orgId, "Risk", r.getId(), r.getTitleEn());
        return out;
    }

    /**
     * Pivot around a single control — surrounding cards are the
     * risks the control mitigates, findings targeting the control,
     * open CAPA, CCM coverage for the control alone.
     */
    @Transactional(readOnly = true)
    public PivotResponse buildControlPivot(Long controlId) {
        Long orgId = TenantContext.requireOrganisationId();
        Control c = mustGetControl(orgId, controlId);

        List<RiskControlLink> links = safeList(() ->
                riskControlLinkRepo.findByOrganisationIdAndControlIdOrderByIdAsc(orgId, controlId));
        List<RiskPivot> riskRows = new ArrayList<>();
        for (RiskControlLink link : links) {
            safeOpt(() -> riskRepo.findById(link.getRiskId()))
                    .filter(r -> orgId.equals(r.getOrganisationId()))
                    .ifPresent(r -> riskRows.add(toRiskPivot(r)));
        }

        String selfEffectiveness = links.stream()
                .map(RiskControlLink::getEffectiveness)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> latestAssessmentEffectiveness(orgId, controlId));
        // PERF-NQ-004: see buildRiskPivot — CCM defs loaded once.
        Map<Long, List<ControlTestDefinition>> defsByControl = loadDefsByControl(orgId);
        ControlPivot selfRow = toControlPivot(c, selfEffectiveness, orgId, defsByControl);
        List<ControlPivot> controlRows = List.of(selfRow);

        List<FindingPivot> findingRows = findingsForControls(orgId, controlRows);
        List<CapaPivot> capaRows = capasForFindings(orgId, findingRows);
        CcmCoverageStats ccm = ccmCoverageForControls(orgId, List.of(controlId), defsByControl);

        PivotResponse out = new PivotResponse(
                FocalEntity.CONTROL,
                LocalDateTime.now(),
                null,
                toControlSummary(c),
                null,
                riskRows,
                controlRows,
                findingRows,
                capaRows,
                ccm,
                summarise(controlRows, findingRows, capaRows, riskRows));

        emitPivotEvent(orgId, "Control", c.getId(), c.getTitleEn());
        return out;
    }

    /**
     * Pivot around a single finding — surrounding cards are the
     * linked control (resolved via control_crosswalk), the risks
     * that control mitigates, and the CAPA items hanging off the
     * finding.
     */
    @Transactional(readOnly = true)
    public PivotResponse buildFindingPivot(Long findingId) {
        Long orgId = TenantContext.requireOrganisationId();
        Finding f = mustGetFinding(orgId, findingId);

        List<String> controlCodes = parseControlCodes(f.getControlCrosswalk());
        List<ControlPivot> controlRows = new ArrayList<>();
        List<Long> controlIds = new ArrayList<>();
        List<RiskPivot> riskRows = new ArrayList<>();
        Map<Long, Boolean> seenRiskIds = new LinkedHashMap<>();
        // PERF-NQ-004: see buildRiskPivot — CCM defs loaded once.
        Map<Long, List<ControlTestDefinition>> defsByControl = loadDefsByControl(orgId);
        if (!controlCodes.isEmpty()) {
            List<Control> orgControls = safeList(() ->
                    controlRepo.findByOrganisationIdOrderByDomainAscControlCodeAsc(orgId));
            Set<String> codeSet = new java.util.HashSet<>(controlCodes);
            for (Control c : orgControls) {
                if (c.getControlCode() == null || !codeSet.contains(c.getControlCode())) continue;
                controlIds.add(c.getId());
                controlRows.add(toControlPivot(c, latestAssessmentEffectiveness(orgId, c.getId()), orgId, defsByControl));
                for (RiskControlLink link : safeList(() ->
                        riskControlLinkRepo.findByOrganisationIdAndControlIdOrderByIdAsc(orgId, c.getId()))) {
                    if (seenRiskIds.containsKey(link.getRiskId())) continue;
                    seenRiskIds.put(link.getRiskId(), Boolean.TRUE);
                    safeOpt(() -> riskRepo.findById(link.getRiskId()))
                            .filter(r -> orgId.equals(r.getOrganisationId()))
                            .ifPresent(r -> riskRows.add(toRiskPivot(r)));
                }
            }
        }

        List<CapaPivot> capaRows = new ArrayList<>();
        if (f.getExternalId() != null) {
            for (com.tss.grc.module.audit_lc.entity.CapaTask t : safeList(() ->
                    auditLcCapaRepo.findByOrganisationIdAndFindingIdOrderByTargetDateAsc(orgId, f.getExternalId()))) {
                capaRows.add(toCapaPivot(t));
            }
            for (CapaTask t : safeList(() ->
                    capaRepo.findByOrganisationIdAndOriginTypeAndOriginId(orgId, "FINDING", f.getExternalId()))) {
                capaRows.add(toCapaPivotUnified(t));
            }
        }

        CcmCoverageStats ccm = ccmCoverageForControls(orgId, controlIds, defsByControl);

        FindingPivot self = toFindingPivot(f);
        PivotResponse out = new PivotResponse(
                FocalEntity.FINDING,
                LocalDateTime.now(),
                null,
                null,
                toFindingSummary(f),
                riskRows,
                controlRows,
                List.of(self),
                capaRows,
                ccm,
                summarise(controlRows, List.of(self), capaRows, riskRows));

        emitPivotEvent(orgId, "Finding", f.getId(), f.getTitleEn());
        return out;
    }

    // ─── Lookups ───────────────────────────────────────────────────

    private Risk mustGetRisk(Long orgId, Long riskId) {
        return riskRepo.findById(riskId)
                .filter(rr -> orgId.equals(rr.getOrganisationId()))
                .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + riskId));
    }

    private Control mustGetControl(Long orgId, Long controlId) {
        return controlRepo.findByIdAndOrganisationId(controlId, orgId)
                .orElseThrow(() -> new EntityNotFoundException("Control not found: " + controlId));
    }

    private Finding mustGetFinding(Long orgId, Long findingId) {
        return findingRepo.findById(findingId)
                .filter(ff -> orgId.equals(ff.getOrganisationId()))
                .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + findingId));
    }

    /**
     * Walk the org's findings and pick those whose
     * {@code control_crosswalk} JSON cites any of the supplied
     * control codes. The JSON shape is
     * {@code [{"framework":..,"controlCode":..}]}; we accept either
     * the structured form or a simple comma-separated fallback.
     */
    private List<FindingPivot> findingsForControls(Long orgId, List<ControlPivot> controls) {
        if (controls == null || controls.isEmpty()) return List.of();
        Set<String> codes = new java.util.HashSet<>();
        for (ControlPivot cp : controls) {
            if (cp.code() != null) codes.add(cp.code());
        }
        if (codes.isEmpty()) return List.of();
        List<Finding> all = safeList(() -> findingRepo.findByOrganisationIdOrderByRaisedAtDesc(orgId));
        List<FindingPivot> out = new ArrayList<>();
        for (Finding f : all) {
            List<String> findingCodes = parseControlCodes(f.getControlCrosswalk());
            if (findingCodes.stream().anyMatch(codes::contains)) out.add(toFindingPivot(f));
        }
        return out;
    }

    private List<CapaPivot> capasForFindings(Long orgId, List<FindingPivot> findings) {
        if (findings == null || findings.isEmpty()) return List.of();
        List<CapaPivot> out = new ArrayList<>();
        for (FindingPivot fp : findings) {
            if (fp.externalId() == null) continue;
            for (com.tss.grc.module.audit_lc.entity.CapaTask t : safeList(() ->
                    auditLcCapaRepo.findByOrganisationIdAndFindingIdOrderByTargetDateAsc(orgId, fp.externalId()))) {
                out.add(toCapaPivot(t));
            }
            for (CapaTask t : safeList(() ->
                    capaRepo.findByOrganisationIdAndOriginTypeAndOriginId(orgId, "FINDING", fp.externalId()))) {
                out.add(toCapaPivotUnified(t));
            }
        }
        return out;
    }

    /**
     * PERF-NQ-004: load the org's CCM test definitions ONCE and group them by
     * controlId. Both {@link #toControlPivot} (per control) and
     * {@link #ccmCoverageForControls} consume this map, so a pivot scans the
     * control_test_definitions table a single time instead of once per control
     * (toControlPivot) plus once more in the coverage rollup.
     */
    private Map<Long, List<ControlTestDefinition>> loadDefsByControl(Long orgId) {
        Map<Long, List<ControlTestDefinition>> byControl = new HashMap<>();
        for (ControlTestDefinition d : safeList(() ->
                ccmDefRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId))) {
            if (d.getControlId() == null) continue;
            byControl.computeIfAbsent(d.getControlId(), k -> new ArrayList<>()).add(d);
        }
        return byControl;
    }

    private CcmCoverageStats ccmCoverageForControls(Long orgId, List<Long> controlIds,
            Map<Long, List<ControlTestDefinition>> defsByControl) {
        if (controlIds == null || controlIds.isEmpty()) {
            return new CcmCoverageStats(0L, 0L, 0L, 0L, null);
        }
        List<ControlTestDefinition> defs = controlIds.stream().distinct()
                .flatMap(id -> defsByControl.getOrDefault(id, List.of()).stream())
                .toList();
        if (defs.isEmpty()) {
            return new CcmCoverageStats(0L, 0L, 0L, 0L, null);
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long passing = 0, failing = 0, errors = 0;
        LocalDateTime mostRecent = null;
        for (ControlTestDefinition d : defs) {
            List<ControlTestRun> recent = safeList(() ->
                    ccmRunRepo.findRecentForTest(orgId, d.getId(), PageRequest.of(0, 100)));
            for (ControlTestRun r : recent) {
                if (r.getRunAt() == null) continue;
                if (mostRecent == null || r.getRunAt().isAfter(mostRecent)) mostRecent = r.getRunAt();
                if (r.getRunAt().isBefore(since)) continue;
                String s = r.getStatus();
                if ("PASS".equals(s)) passing++;
                else if ("FAIL".equals(s)) failing++;
                else if ("ERROR".equals(s) || "TIMEOUT".equals(s)) errors++;
            }
        }
        return new CcmCoverageStats(defs.size(), passing, failing, errors, mostRecent);
    }

    private String latestAssessmentEffectiveness(Long orgId, Long controlId) {
        List<ControlAssessment> all = safeList(() ->
                controlAssessmentRepo.findByOrganisationIdAndControlIdOrderByAssessmentDateDesc(orgId, controlId));
        return all.stream()
                .filter(a -> "APPROVED".equals(a.getWorkflowStatus()))
                .findFirst()
                .map(ControlAssessment::getEffectiveness)
                .orElse("NOT_TESTED");
    }

    /**
     * Parse a control_crosswalk JSON column into the set of control
     * codes it cites. Supports the structured shape (a JSON array of
     * objects with {@code controlCode}) and a comma-separated
     * fallback for legacy data.
     */
    static List<String> parseControlCodes(String json) {
        if (json == null || json.isBlank()) return List.of();
        String trimmed = json.trim();
        List<String> out = new ArrayList<>();
        try {
            ObjectMapper m = new ObjectMapper();
            if (trimmed.startsWith("[")) {
                JsonNode arr = m.readTree(trimmed);
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        JsonNode code = n.get("controlCode");
                        if (code != null && code.isTextual() && !code.asText().isBlank()) {
                            out.add(code.asText());
                        } else if (n.isTextual() && !n.asText().isBlank()) {
                            out.add(n.asText());
                        }
                    }
                }
            } else if (trimmed.startsWith("{")) {
                JsonNode root = m.readTree(trimmed);
                JsonNode code = root.get("controlCode");
                if (code != null && code.isTextual()) out.add(code.asText());
            } else {
                for (String s : trimmed.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
        } catch (Exception ex) {
            for (String s : trimmed.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    private PivotSummary summarise(
            List<ControlPivot> controls,
            List<FindingPivot> findings,
            List<CapaPivot> capa,
            List<RiskPivot> risks) {
        int controlsCount = controls == null ? 0 : controls.size();
        int effective = controls == null ? 0 : (int) controls.stream()
                .filter(c -> "EFFECTIVE".equals(c.effectiveness()))
                .count();
        double effPct = controlsCount == 0 ? 0.0 : (effective * 100.0) / controlsCount;
        int openF = findings == null ? 0 : (int) findings.stream()
                .filter(f -> OPEN_FINDING_STATUSES.contains(safe(f.status())))
                .count();
        int overdue = capa == null ? 0 : (int) capa.stream()
                .filter(c -> c.daysOverdue() != null && c.daysOverdue() > 0L
                        && OPEN_CAPA_STATUSES.contains(safe(c.status())))
                .count();
        int risksCount = risks == null ? 0 : risks.size();
        double completeness = computeCompleteness(controls, findings, capa, risks);
        return new PivotSummary(controlsCount, effective, effPct, openF, overdue, risksCount, completeness);
    }

    /**
     * 0..100 — proportion of "useful" optional fields populated
     * across each section. A risk with linked controls all carrying
     * effectiveness + a last-tested timestamp scores higher than one
     * with mostly NOT_TESTED entries.
     *
     * <p>Six equally-weighted gates (each worth ~16.67):
     * <ol>
     *   <li>≥1 linked control</li>
     *   <li>All controls have a non-null effectiveness != NOT_TESTED</li>
     *   <li>All controls have a recent CCM lastTestAt</li>
     *   <li>Findings present have a non-OBSERVATION severity bucket</li>
     *   <li>All open CAPA have a target date</li>
     *   <li>All linked risks have a residualScore</li>
     * </ol>
     */
    static double computeCompleteness(
            List<ControlPivot> controls,
            List<FindingPivot> findings,
            List<CapaPivot> capa,
            List<RiskPivot> risks) {
        double score = 0.0;
        double gate = 100.0 / 6.0;
        if (controls != null && !controls.isEmpty()) score += gate;
        if (controls != null && !controls.isEmpty()
                && controls.stream().allMatch(c -> c.effectiveness() != null
                        && !"NOT_TESTED".equals(c.effectiveness()))) {
            score += gate;
        }
        if (controls != null && !controls.isEmpty()
                && controls.stream().allMatch(c -> c.lastTestAt() != null)) {
            score += gate;
        }
        if (findings == null || findings.isEmpty()
                || findings.stream().allMatch(f -> f.severity() != null
                        && !"OBSERVATION".equalsIgnoreCase(f.severity()))) {
            score += gate;
        }
        if (capa == null || capa.isEmpty()
                || capa.stream().allMatch(c -> c.targetDate() != null)) {
            score += gate;
        }
        if (risks == null || risks.isEmpty()
                || risks.stream().allMatch(r -> r.residualScore() != null)) {
            score += gate;
        }
        return Math.min(100.0, Math.max(0.0, score));
    }

    private void emitPivotEvent(Long orgId, String entityType, Long entityId, String title) {
        try {
            audit.log("PIVOT_VIEW_GENERATED", entityType, entityId,
                    "Pivot generated: " + entityType + " · id=" + entityId
                            + " · title=" + safe(title));
            Map<String, Object> payload = new HashMap<>();
            payload.put("focalEntity", entityType);
            payload.put("entityId", entityId == null ? 0L : entityId);
            payload.put("entityTitle", safe(title));
            eventPublisher.publishEvent(DomainEvent.of(
                    orgId, "PIVOT_VIEW_GENERATED", entityType, entityId, payload));
        } catch (RuntimeException ex) {
            log.warn("Pivot F7/F8 emission failed (continuing): {}", ex.getMessage());
        }
    }

    // ─── Mappers ───────────────────────────────────────────────────

    private RiskSummary toRiskSummary(Risk r) {
        return new RiskSummary(
                r.getId(),
                r.getExternalId() == null ? null : r.getExternalId().toString(),
                "RISK-" + r.getId(),
                r.getTitleEn(),
                r.getTitleAr(),
                r.getRiskLevel(),
                r.getResidualScore(),
                r.getOwnerId(),
                r.getStatus());
    }

    private ControlSummary toControlSummary(Control c) {
        return new ControlSummary(
                c.getId(),
                c.getExternalId() == null ? null : c.getExternalId().toString(),
                c.getControlCode(),
                c.getTitleEn(),
                c.getTitleAr(),
                c.getControlType(),
                c.getAutomationLevel(),
                c.getStatus(),
                c.getDomain(),
                c.getOwnerUserId());
    }

    private FindingSummary toFindingSummary(Finding f) {
        return new FindingSummary(
                f.getId(),
                f.getExternalId(),
                f.getRefNumber(),
                f.getTitleEn(),
                f.getTitleAr(),
                f.getSeverity(),
                f.getStatus(),
                f.getEngagementCode(),
                f.getRaisedAt());
    }

    private RiskPivot toRiskPivot(Risk r) {
        return new RiskPivot(
                r.getId(),
                r.getExternalId() == null ? null : r.getExternalId().toString(),
                r.getTitleEn(),
                r.getTitleAr(),
                r.getRiskLevel(),
                r.getResidualScore(),
                r.getStatus());
    }

    private ControlPivot toControlPivot(Control c, String effectivenessHint, Long orgId,
            Map<Long, List<ControlTestDefinition>> defsByControl) {
        String eff = effectivenessHint;
        if (eff == null || eff.isBlank() || "NOT_TESTED".equals(eff)) {
            String fromAssessment = latestAssessmentEffectiveness(orgId, c.getId());
            if (fromAssessment != null && !"NOT_TESTED".equals(fromAssessment)) {
                eff = fromAssessment;
            } else if (eff == null) {
                eff = "NOT_TESTED";
            }
        }
        String lastStatus = null;
        LocalDateTime lastAt = null;
        // PERF-NQ-004: defs for this control come from the once-loaded map (was a
        // full control_test_definitions scan + filter on every call).
        List<ControlTestDefinition> defs = defsByControl.getOrDefault(c.getId(), List.of());
        for (ControlTestDefinition d : defs) {
            if (d.getLastRunAt() == null) continue;
            if (lastAt == null || d.getLastRunAt().isAfter(lastAt)) {
                lastAt = d.getLastRunAt();
                lastStatus = d.getLastRunStatus();
            }
        }
        return new ControlPivot(
                c.getId(),
                c.getControlCode(),
                c.getTitleEn(),
                c.getTitleAr(),
                eff,
                lastStatus,
                lastAt,
                c.getDomain(),
                c.getOwnerUserId());
    }

    private FindingPivot toFindingPivot(Finding f) {
        return new FindingPivot(
                f.getId(),
                f.getExternalId(),
                f.getRefNumber(),
                f.getTitleEn(),
                f.getTitleAr(),
                f.getSeverity(),
                f.getStatus(),
                f.getRaisedAt(),
                f.getEngagementCode());
    }

    private CapaPivot toCapaPivot(com.tss.grc.module.audit_lc.entity.CapaTask t) {
        LocalDate target = t.getTargetDate();
        Long overdue = target == null ? null
                : ChronoUnit.DAYS.between(target, LocalDate.now());
        return new CapaPivot(
                t.getId(),
                t.getExternalId(),
                t.getTitleEn(),
                t.getTitleAr(),
                target,
                t.getStatus(),
                t.getOwnerRole(),
                overdue,
                "AUDIT_LC");
    }

    private CapaPivot toCapaPivotUnified(CapaTask t) {
        LocalDate target = t.getTargetDate();
        Long overdue = target == null ? null
                : ChronoUnit.DAYS.between(target, LocalDate.now());
        return new CapaPivot(
                t.getId(),
                t.getExternalId(),
                t.getTitleEn(),
                t.getTitleAr(),
                target,
                t.getStatus(),
                t.getOwner(),
                overdue,
                "CAPA");
    }

    // ─── Tiny helpers ──────────────────────────────────────────────

    private static <T> List<T> safeList(java.util.function.Supplier<List<T>> sup) {
        try {
            List<T> r = sup.get();
            return r == null ? List.of() : r;
        } catch (RuntimeException ex) {
            log.warn("Pivot safeList supplier failed; returning empty list", ex);
            return Collections.emptyList();
        }
    }

    private static <T> Optional<T> safeOpt(java.util.function.Supplier<Optional<T>> sup) {
        try { return sup.get(); }
        catch (RuntimeException ex) {
            log.warn("Pivot safeOpt supplier failed; returning empty Optional", ex);
            return Optional.empty();
        }
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
