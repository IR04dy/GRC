package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.socops.dto.SocOpsDashboardResponse;
import com.tss.grc.module.socops.entity.RegulatorExamination;
import com.tss.grc.module.socops.entity.SeasonalOverlay;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.AwarenessCampaignRepository;
import com.tss.grc.module.socops.repository.CryptoAssetRepository;
import com.tss.grc.module.socops.repository.IrPlaybookRepository;
import com.tss.grc.module.socops.repository.PentestEngagementRepository;
import com.tss.grc.module.socops.repository.RegulatorExaminationRepository;
import com.tss.grc.module.socops.repository.SbomComponentRepository;
import com.tss.grc.module.socops.repository.ScannerRunRepository;
import com.tss.grc.module.socops.repository.SeasonalOverlayRepository;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import com.tss.grc.module.socops.repository.ThreatIntelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOC Operations dashboard — aggregates 12 repositories into a single
 * leadership-ops payload. Trailing-30-day window for MTTD/MTTR; 24h
 * window for "scanner runs last 24h"; 30/90-day windows for cert expiry.
 *
 * <p>All counts are tenant-scoped; live-computed at request time (no
 * server-side cache). Indexed columns keep the query cost cheap even at
 * tens-of-thousands-of-events tenant scale.
 */
@Service
@RequiredArgsConstructor
public class SocOpsDashboardService {

    private final SecurityEventRepository       events;
    private final ThreatIntelRepository         threatIntel;
    private final IrPlaybookRepository          playbooks;
    private final ScannerRunRepository          scannerRuns;
    private final PentestEngagementRepository   pentests;
    private final CryptoAssetRepository         crypto;
    private final SbomComponentRepository       sbom;
    private final AwarenessCampaignRepository   awareness;
    private final RegulatorExaminationRepository regulatorExams;
    private final SeasonalOverlayRepository     seasonal;

    @Transactional(readOnly = true)
    public SocOpsDashboardResponse get() {
        Long org = TenantContext.getCurrentTenant();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime past30d = now.minusDays(30);
        LocalDateTime past24h = now.minusHours(24);

        // ── Headline event counters ──────────────────────────────────
        long openCritical = events.countByOrganisationIdAndSeverity(org, "CRITICAL")
            - countResolvedAtSeverity(org, "CRITICAL");
        // The above isn't exact — use status-based count instead via a
        // narrower repository call. Simplify: count NEW + TRIAGED at each
        // severity by listing then filtering — small queue, cheap in
        // practice. For tenants with massive queues we'd add a dedicated
        // count-by-severity-and-status query; not critical for v1.
        List<SecurityEvent> openEvents = events.findByOrganisationIdAndStatusOrderByEventTimeDesc(org, "NEW");
        openEvents.addAll(events.findByOrganisationIdAndStatusOrderByEventTimeDesc(org, "TRIAGED"));
        openEvents.addAll(events.findByOrganisationIdAndStatusOrderByEventTimeDesc(org, "IN_ANALYSIS"));
        openEvents.addAll(events.findByOrganisationIdAndStatusOrderByEventTimeDesc(org, "CONTAINED"));

        long oCritical = openEvents.stream().filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())).count();
        long oHigh     = openEvents.stream().filter(e -> "HIGH".equalsIgnoreCase(e.getSeverity())).count();
        long oMedium   = openEvents.stream().filter(e -> "MEDIUM".equalsIgnoreCase(e.getSeverity())).count();
        long oTotal    = openEvents.size();

        long approaching = events.findApproachingSlaBreach(org, now.plusHours(1)).size();
        Double mttd = events.meanTimeToDetectSeconds(org, past30d);
        Double mttr = events.meanTimeToResolveSeconds(org, past30d);

        // ── IR playbooks ──────────────────────────────────────────────
        long playbookTotal = playbooks.findByOrganisationIdAndStatus(org, "ACTIVE").size();
        long playbookInvocations = playbooks.findByOrganisationIdOrderByCategoryAscPlaybookCodeAsc(org)
            .stream().mapToLong(p -> p.getInvocationCount() == null ? 0 : p.getInvocationCount()).sum();

        // ── Threat intel ─────────────────────────────────────────────
        long tiUnactioned = threatIntel.countByOrganisationIdAndActioned(org, false);
        long tiTotal      = threatIntel.findByOrganisationIdOrderByPublishedAtDesc(org, PageRequest.of(0, 1))
            .getTotalElements();

        // ── Scanner activity ─────────────────────────────────────────
        long scanRunsLast24h = scannerRuns.findByOrganisationIdOrderByStartedAtDesc(org, PageRequest.of(0, 100))
            .getContent().stream()
            .filter(r -> r.getStartedAt() != null && r.getStartedAt().isAfter(past24h))
            .count();
        long scanCritsOpen = scannerRuns.findByOrganisationIdOrderByStartedAtDesc(org, PageRequest.of(0, 1000))
            .getContent().stream()
            .filter(r -> "COMPLETED".equalsIgnoreCase(r.getStatus()))
            .mapToInt(r -> r.getFindingsCritical() == null ? 0 : r.getFindingsCritical())
            .sum();

        // ── Pentests ─────────────────────────────────────────────────
        long pentestsFieldwork = pentests.countByOrganisationIdAndStatus(org, "IN_PROGRESS")
                                + pentests.countByOrganisationIdAndStatus(org, "REPORT_PENDING");
        long pentestsRetest = pentests.countByOrganisationIdAndStatus(org, "RETEST");

        // ── Crypto / cert expiry ─────────────────────────────────────
        long certs30d = crypto.findExpiringBetween(org, now, now.plusDays(30)).size();
        long certs90d = crypto.findExpiringBetween(org, now, now.plusDays(90)).size();
        long certsQuantum = crypto.countByOrganisationIdAndQuantumSafe(org, false);

        // ── SBOM ─────────────────────────────────────────────────────
        long sbomTotal = sbom.countByOrganisationId(org);
        long sbomWithCves = sbom.countByOrganisationIdAndHasKnownCves(org, true);

        // ── Awareness ────────────────────────────────────────────────
        long awarenessRunning = awareness.countByOrganisationIdAndStatus(org, "RUNNING");
        Double awarenessRate = awareness.findByOrganisationIdOrderByStartedAtDesc(org, PageRequest.of(0, 1))
            .stream().findFirst()
            .map(c -> c.getPassRatePercent() == null ? null : c.getPassRatePercent().doubleValue())
            .orElse(null);

        // ── Regulator exams ──────────────────────────────────────────
        long examsOpen = regulatorExams.countByOrganisationIdAndStatus(org, "FIELDWORK")
                       + regulatorExams.countByOrganisationIdAndStatus(org, "DRAFT_REPORT")
                       + regulatorExams.countByOrganisationIdAndStatus(org, "AWAITING_RESPONSE")
                       + regulatorExams.countByOrganisationIdAndStatus(org, "REMEDIATION");
        List<RegulatorExamination> examPage =
            regulatorExams.findByOrganisationIdOrderByNotifiedAtDesc(org, PageRequest.of(0, 200)).getContent();
        long examsDueSoon = examPage.stream()
            .filter(e -> e.getResponseDueAt() != null
                && e.getResponseSubmittedAt() == null
                && e.getResponseDueAt().isAfter(now)
                && e.getResponseDueAt().isBefore(now.plusDays(14)))
            .count();
        Map<String, Long> examsByRegulator = new LinkedHashMap<>();
        examPage.stream()
            .filter(e -> !"CLOSED".equalsIgnoreCase(e.getStatus()))
            .forEach(e -> examsByRegulator.merge(e.getRegulator(), 1L, Long::sum));

        // ── Seasonal overlays (active right now) ─────────────────────
        List<SocOpsDashboardResponse.SeasonalOverlayBadge> overlays = new ArrayList<>();
        for (SeasonalOverlay s : seasonal.findActiveAt(org, now)) {
            overlays.add(new SocOpsDashboardResponse.SeasonalOverlayBadge(
                s.getOverlayCode(), s.getTitleEn(), s.getTitleAr(), s.getHijriLabel(),
                Boolean.TRUE.equals(s.getAutoTightenKris()),
                Boolean.TRUE.equals(s.getAutoTightenSlas())
            ));
        }

        // ── Top-N lists ──────────────────────────────────────────────
        List<SocOpsDashboardResponse.EventBucket> topEvents = openEvents.stream()
            .filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())
                      || "HIGH".equalsIgnoreCase(e.getSeverity()))
            .limit(5)
            .map(e -> new SocOpsDashboardResponse.EventBucket(
                e.getId(), e.getSeverity(), e.getRuleName(), e.getStatus(),
                e.getSourceSystem(),
                e.getEventTime() == null ? null : e.getEventTime().toString(),
                e.getSlaDueAt() == null ? null : e.getSlaDueAt().toString()))
            .toList();

        List<SocOpsDashboardResponse.RegulatorExamBucket> upcomingExams = examPage.stream()
            .filter(e -> e.getResponseDueAt() != null && e.getResponseSubmittedAt() == null)
            .sorted((a, b) -> a.getResponseDueAt().compareTo(b.getResponseDueAt()))
            .limit(5)
            .map(e -> new SocOpsDashboardResponse.RegulatorExamBucket(
                e.getId(), e.getExaminationCode(), e.getRegulator(), e.getTitleEn(),
                e.getStatus(),
                e.getResponseDueAt() == null ? null : e.getResponseDueAt().toString()))
            .toList();

        // Convert seconds → minutes for display, with null-safety
        Double mttdMin = mttd == null ? null : mttd / 60.0;
        Double mttrMin = mttr == null ? null : mttr / 60.0;

        return new SocOpsDashboardResponse(
            oCritical, oHigh, oMedium, oTotal,
            approaching, mttdMin, mttrMin,
            playbookInvocations, playbookTotal,
            tiUnactioned, tiTotal,
            scanRunsLast24h, scanCritsOpen,
            pentestsFieldwork, pentestsRetest,
            certs30d, certs90d, certsQuantum,
            sbomTotal, sbomWithCves,
            awarenessRunning, awarenessRate,
            examsOpen, examsDueSoon, examsByRegulator,
            overlays, topEvents, upcomingExams
        );
    }

    /** Placeholder — kept so the headline-counter signature compiles
     *  before we wire the dedicated count-by-status-and-severity repo
     *  query. The dashboard already uses the {@code openEvents} list
     *  directly so this is currently unused. */
    private long countResolvedAtSeverity(Long org, String severity) {
        return 0L;
    }
}
