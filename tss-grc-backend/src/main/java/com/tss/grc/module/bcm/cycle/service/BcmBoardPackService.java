package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisAar;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.entity.CrisisRegulatoryNotification;
import com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisAarRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisRegulatoryNotificationRepository;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Board Pack BCM section auto-generator — Slice 7-D (Sprint-7).
 *
 * <p>Aggregates the Board-ready BCM snapshot every Saudi CISO / BCM Manager
 * is asked to ship into the quarterly Board pack:
 *
 * <ul>
 *   <li>Top 5 most-critical processes by MTPD (lowest MTPD first)</li>
 *   <li>Last 5 executed exercises with score + status</li>
 *   <li>Open CAPA count (BCM register only — federated CAPA stays in {@code capa.capa_tasks})</li>
 *   <li>Most-recent APPROVED / PUBLISHED AAR — title + executive summary excerpt</li>
 *   <li>Active crisis events (status not in CLOSED/CANCELLED) — count + severity breakdown</li>
 *   <li>Regulator notification log — last 20 across all crisis events</li>
 *   <li>Continuity Readiness Score headline (single 0..100) via {@link BcmReadinessScoreService}</li>
 * </ul>
 *
 * <p>Pure additive / read-only — no schema changes. Designed to feed both the
 * board-pack PDF export (planned Sprint-8 wizard) and an at-a-glance executive
 * dashboard tile.
 *
 * <p>Tenant-scoped via {@link TenantContext}; every repository call already
 * filters by {@code organisation_id}.
 */
@Service
@RequiredArgsConstructor
public class BcmBoardPackService {

    private final CriticalProcessRepository processes;
    private final BiaRecordRepository bias;
    private final BcmExerciseRepository exercises;
    private final BcmCorrectiveActionRepository capas;
    private final CrisisAarRepository aars;
    private final CrisisEventRepository crises;
    private final CrisisRegulatoryNotificationRepository regNotifs;
    private final BcmReadinessScoreService readiness;

    // ─── Public surface ──────────────────────────────────────────────

    /** The full Board Pack BCM section for the current tenant. */
    @Transactional(readOnly = true)
    public BoardPackSection generate() {
        Long org = TenantContext.getCurrentTenant();

        return new BoardPackSection(
            org,
            LocalDateTime.now(),
            topCriticalProcesses(org),
            recentExercises(org),
            capaSnapshot(org),
            lastAar(org),
            activeCrises(org),
            recentRegulatorNotifications(org),
            readinessHeadline()
        );
    }

    // ─── Section builders ────────────────────────────────────────────

    private List<CriticalProcessRow> topCriticalProcesses(Long org) {
        // Pull all critical processes for tenant, then their BIAs by process id.
        // BiaRecord doesn't carry organisationId directly — we join through processes.
        List<CriticalProcess> all = processes.findByOrganisationId(org);
        if (all.isEmpty()) return List.of();

        Map<Long, CriticalProcess> byId = new HashMap<>();
        for (CriticalProcess p : all) byId.put(p.getId(), p);

        List<BiaRecord> rows = bias.findByProcessIdIn(new ArrayList<>(byId.keySet()));
        // For each process keep the smallest MTPD BIA (most-critical).
        Map<Long, BiaRecord> bestPerProcess = new HashMap<>();
        for (BiaRecord b : rows) {
            if (b.getMtpdHours() == null) continue;
            BiaRecord cur = bestPerProcess.get(b.getProcessId());
            if (cur == null || (cur.getMtpdHours() != null && b.getMtpdHours() < cur.getMtpdHours())) {
                bestPerProcess.put(b.getProcessId(), b);
            }
        }

        return bestPerProcess.values().stream()
            .sorted(Comparator.comparingInt(b -> b.getMtpdHours() == null ? Integer.MAX_VALUE : b.getMtpdHours()))
            .limit(5)
            .map(b -> {
                CriticalProcess p = byId.get(b.getProcessId());
                return new CriticalProcessRow(
                    b.getProcessId(),
                    p == null ? "(unknown)" : p.getProcessNameEn(),
                    p == null ? null : p.getProcessNameAr(),
                    p == null ? null : p.getCriticality(),
                    b.getMtpdHours(),
                    b.getRtoHours(),
                    b.getRpoHours(),
                    b.getOverallImpact()
                );
            })
            .collect(Collectors.toList());
    }

    private List<ExerciseRow> recentExercises(Long org) {
        // Order by id desc as a proxy for newest-first; we filter to executed rows
        // (executedDate not null) and cap at 5. Pull 25 to give some slack for
        // unscheduled rows, then trim.
        return exercises.findByOrganisationId(org, PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "id")))
            .stream()
            .filter(e -> e.getExecutedDate() != null
                      || "COMPLETED".equalsIgnoreCase(e.getStatus())
                      || "DEBRIEFED".equalsIgnoreCase(e.getStatus()))
            .limit(5)
            .map(this::toExerciseRow)
            .collect(Collectors.toList());
    }

    private ExerciseRow toExerciseRow(BcmExercise e) {
        return new ExerciseRow(
            e.getId(),
            e.getExerciseNameEn(),
            e.getExerciseNameAr(),
            e.getExerciseType(),
            e.getExecutedDate(),
            e.getStatus(),
            e.getScore(),
            e.getCapaCount(),
            e.getFacilitator()
        );
    }

    private CapaSnapshot capaSnapshot(Long org) {
        long open       = capas.countByOrganisationIdAndStatus(org, "OPEN");
        long assigned   = capas.countByOrganisationIdAndStatus(org, "ASSIGNED");
        long inProgress = capas.countByOrganisationIdAndStatus(org, "IN_PROGRESS");
        long evidence   = capas.countByOrganisationIdAndStatus(org, "EVIDENCE_SUBMITTED");
        long review     = capas.countByOrganisationIdAndStatus(org, "UNDER_REVIEW");
        long overdue    = capas.countByOrganisationIdAndStatus(org, "OVERDUE");
        long reopened   = capas.countByOrganisationIdAndStatus(org, "REOPENED");
        long closed     = capas.countByOrganisationIdAndStatus(org, "CLOSED");
        long activeOpen = open + assigned + inProgress + evidence + review + overdue + reopened;

        // Overdue = due date in past with status not in CLOSED.
        List<String> done = List.of("CLOSED");
        int overdueByDate = capas
            .findByOrganisationIdAndDueDateBeforeAndStatusNotIn(org, LocalDate.now(), done)
            .size();

        return new CapaSnapshot(
            activeOpen, overdueByDate, closed,
            new LinkedHashMap<>(Map.of(
                "OPEN",               open,
                "ASSIGNED",           assigned,
                "IN_PROGRESS",        inProgress,
                "EVIDENCE_SUBMITTED", evidence,
                "UNDER_REVIEW",       review,
                "OVERDUE",            overdue,
                "REOPENED",           reopened,
                "CLOSED",             closed
            ))
        );
    }

    private AarSummary lastAar(Long org) {
        List<CrisisAar> all = aars.findByOrganisationIdOrderByCreatedAtDesc(org);
        // Prefer APPROVED / PUBLISHED for board presentation.
        Optional<CrisisAar> pick = all.stream()
            .filter(a -> "APPROVED".equals(a.getStatus()) || "PUBLISHED".equals(a.getStatus()))
            .findFirst();
        CrisisAar a = pick.orElse(all.isEmpty() ? null : all.get(0));
        if (a == null) return null;
        return new AarSummary(
            a.getId(),
            a.getReportCode(),
            a.getTitleEn(),
            a.getTitleAr(),
            a.getStatus(),
            a.getApprovedAt(),
            a.getPublishedAt(),
            excerpt(a.getExecutiveSummaryEn(), 320),
            excerpt(a.getExecutiveSummaryAr(), 320),
            a.getCrisisEventId()
        );
    }

    private ActiveCrisisSnapshot activeCrises(Long org) {
        List<String> excluded = List.of("CLOSED", "CANCELLED");
        List<CrisisEvent> active = crises.findByOrganisationIdAndStatusNotInOrderByIdDesc(org, excluded);

        Map<Integer, Long> bySeverity = new TreeMap<>(Comparator.reverseOrder());
        for (CrisisEvent e : active) {
            bySeverity.merge(e.getSeverity() == null ? 0 : e.getSeverity(), 1L, Long::sum);
        }

        List<CrisisHeadline> headlines = active.stream()
            .limit(5)
            .map(e -> new CrisisHeadline(
                e.getId(), e.getEventCode(), e.getTitleEn(), e.getTitleAr(),
                e.getSeverity(), e.getStatus(), e.getDetectionTime(),
                Boolean.TRUE.equals(e.getPersonalDataAffected()),
                Boolean.TRUE.equals(e.getCyberRelated()),
                Boolean.TRUE.equals(e.getSupplierInvolved())
            ))
            .collect(Collectors.toList());

        return new ActiveCrisisSnapshot(active.size(), bySeverity, headlines);
    }

    private List<RegulatorNotificationRow> recentRegulatorNotifications(Long org) {
        return regNotifs.findTop20ByOrganisationIdOrderByNotifiedAtDesc(org)
            .stream()
            .map(n -> new RegulatorNotificationRow(
                n.getId(),
                n.getCrisisEventId(),
                n.getRegulator(),
                n.getNotifiedAt(),
                n.getNotificationMethod(),
                n.getReferenceNumber(),
                Boolean.TRUE.equals(n.getAcknowledgementReceived()),
                n.getAcknowledgementAt(),
                n.getAcknowledgementReference()
            ))
            .collect(Collectors.toList());
    }

    private ReadinessHeadline readinessHeadline() {
        BcmReadinessScoreService.TenantScore s = readiness.tenantOverall();
        return new ReadinessHeadline(s.averageScore(), s.biaCount(), s.biasByCriticality());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static String excerpt(String s, int max) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max - 1) + "…";
    }

    // ─── DTO records — Saudi-native field names where audience-relevant ──

    public record BoardPackSection(
        Long organisationId,
        LocalDateTime generatedAt,
        List<CriticalProcessRow> topCriticalProcesses,
        List<ExerciseRow> recentExercises,
        CapaSnapshot capa,
        AarSummary lastAar,                              // nullable
        ActiveCrisisSnapshot activeCrises,
        List<RegulatorNotificationRow> regulatorNotifications,
        ReadinessHeadline readiness
    ) {}

    public record CriticalProcessRow(
        Long processId,
        String processNameEn,
        String processNameAr,
        String criticality,
        Integer mtpdHours,
        Integer rtoHours,
        Integer rpoHours,
        String overallImpact
    ) {}

    public record ExerciseRow(
        Long id,
        String exerciseNameEn,
        String exerciseNameAr,
        String exerciseType,
        LocalDate executedDate,
        String status,
        Integer score,
        Integer capaCount,
        String facilitator
    ) {}

    public record CapaSnapshot(
        long activeOpenCount,         // not CLOSED
        long overdueCount,            // dueDate < today AND status != CLOSED
        long closedCount,
        Map<String, Long> byStatus    // full breakdown
    ) {}

    public record AarSummary(
        Long id,
        String reportCode,
        String titleEn,
        String titleAr,
        String status,
        LocalDateTime approvedAt,
        LocalDateTime publishedAt,
        String executiveSummaryExcerptEn,
        String executiveSummaryExcerptAr,
        Long crisisEventId
    ) {}

    public record ActiveCrisisSnapshot(
        int activeCount,
        Map<Integer, Long> bySeverity,
        List<CrisisHeadline> top
    ) {}

    public record CrisisHeadline(
        Long id,
        String eventCode,
        String titleEn,
        String titleAr,
        Integer severity,
        String status,
        LocalDateTime detectionTime,
        boolean personalDataAffected,
        boolean cyberRelated,
        boolean supplierInvolved
    ) {}

    public record RegulatorNotificationRow(
        Long id,
        Long crisisEventId,
        String regulator,
        LocalDateTime notifiedAt,
        String method,
        String referenceNumber,
        boolean acknowledged,
        LocalDateTime acknowledgementAt,
        String acknowledgementReference
    ) {}

    public record ReadinessHeadline(
        double averageScore,
        int biaCount,
        Map<String, Long> biasByCriticality
    ) {}
}
