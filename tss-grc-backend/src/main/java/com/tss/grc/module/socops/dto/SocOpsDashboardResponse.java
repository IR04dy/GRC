package com.tss.grc.module.socops.dto;

import java.util.List;
import java.util.Map;

/**
 * SOC Operations Dashboard payload — what a Saudi CISO sees on the
 * leadership ops screen. Designed around the four questions a CISO is
 * asked at any executive meeting:
 *
 * <ol>
 *   <li>How fast are we detecting? ({@link #mttdMinutes})</li>
 *   <li>How fast are we responding? ({@link #mttrMinutes})</li>
 *   <li>What's open right now, and at what severity?</li>
 *   <li>What deadlines (SLA, regulator, cert expiry) are approaching?</li>
 * </ol>
 *
 * <p>All counts are tenant-scoped and computed live; nothing's cached on the
 * server side because the dashboard refreshes infrequently and a 200ms
 * delta query on indexed columns is cheap.
 */
public record SocOpsDashboardResponse(
    /* Headline KPIs (cover-tile values for the dashboard) */
    long openEventsCritical,
    long openEventsHigh,
    long openEventsMedium,
    long openEventsTotal,
    long eventsApproachingSlaBreach,
    Double mttdMinutes,          // mean-time-to-detect (trailing 30 days)
    Double mttrMinutes,          // mean-time-to-respond  (trailing 30 days)
    /* IR activity */
    long activePlaybookInvocations,
    long playbookTotalCount,
    /* Threat intelligence */
    long actionableTiUnactioned,
    long actionableTiTotal,
    /* Vulnerability ingest */
    long scannerRunsLast24h,
    long scannerCriticalsOpen,
    /* Pentest pipeline */
    long pentestsInFieldwork,
    long pentestsRetestDue,
    /* Crypto risk — cert expiry pipeline */
    long certsExpiring30d,
    long certsExpiring90d,
    long certsQuantumUnsafe,
    /* SBOM */
    long sbomComponentsTotal,
    long sbomComponentsWithCves,
    /* Awareness */
    long awarenessCampaignsRunning,
    Double awarenessLastPassRatePercent,
    /* Regulator engagement */
    long regulatorExamsOpen,
    long regulatorExamsResponseDueSoon,
    Map<String, Long> regulatorExamsByRegulator,
    /* Active seasonal overlays */
    List<SeasonalOverlayBadge> activeOverlays,
    /* Top-N lists */
    List<EventBucket> topRecentEvents,           // 5 most recent CRITICAL/HIGH
    List<RegulatorExamBucket> upcomingExams      // next 5 by responseDueAt
) {
    public record SeasonalOverlayBadge(String overlayCode, String titleEn, String titleAr,
                                       String hijriLabel,
                                       boolean tighteningKris, boolean tighteningSlas) {}

    public record EventBucket(Long id, String severity, String ruleName, String status,
                              String sourceSystem, String eventTime, String slaDueAt) {}

    public record RegulatorExamBucket(Long id, String examinationCode, String regulator,
                                      String titleEn, String status,
                                      String responseDueAt) {}
}
