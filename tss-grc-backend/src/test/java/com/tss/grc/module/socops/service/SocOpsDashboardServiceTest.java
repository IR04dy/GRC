package com.tss.grc.module.socops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.socops.dto.SocOpsDashboardResponse;
import com.tss.grc.module.socops.entity.RegulatorExamination;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * F2-367 — the SOC leadership dashboard's "open security events" set must be the COMPLEMENT of the
 * terminal set, never a hand-typed inclusion list.
 *
 * <p>The prior code listed {@code NEW / TRIAGED / IN_ANALYSIS / CONTAINED}; it dropped
 * {@code ESCALATED} — a real, actively-escalated state (the event has been promoted to a cyber
 * incident) — and named two tokens ({@code IN_ANALYSIS}, {@code CONTAINED}) no writer ever sets. So
 * an escalated CRITICAL event vanished from the open counts and the leadership top-5, in the
 * flattering direction (F2-196 / F2-364 / F2-343). The authoritative readers already use the
 * complement: {@code SecurityEventRepository.findApproachingSlaBreach}'s
 * {@code NOT IN ('RESOLVED', 'FALSE_POSITIVE')} and {@code SocOpsExportService}.
 *
 * <p>Hermetic: a focused Mockito test of the extracted {@code openEvents(org)} plus source scans of
 * the vocabulary — no Spring, no DB (the query's row selection is SQL). The dashboard had no prior
 * test (F2-248).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocOpsDashboardServiceTest {

    private static final Long ORG = 1L;

    @Mock private SecurityEventRepository       events;
    @Mock private ThreatIntelRepository         threatIntel;
    @Mock private IrPlaybookRepository          playbooks;
    @Mock private ScannerRunRepository          scannerRuns;
    @Mock private PentestEngagementRepository   pentests;
    @Mock private CryptoAssetRepository         crypto;
    @Mock private SbomComponentRepository       sbom;
    @Mock private AwarenessCampaignRepository   awareness;
    @Mock private RegulatorExaminationRepository regulatorExams;
    @Mock private SeasonalOverlayRepository     seasonal;

    @InjectMocks private SocOpsDashboardService svc;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── Leg A (load-bearing): open is read via the complement finder, so ESCALATED counts ──

    @Test
    @DisplayName("F2-367: openEvents() reads the complement finder with the terminal set — ESCALATED counts as open")
    void openEventsReadsTheComplementFinder_soEscalatedCounts() {
        SecurityEvent escalated = ev(42L, "ESCALATED", "CRITICAL");
        when(events.findByOrganisationIdAndStatusNotInOrderByEventTimeDesc(
                ORG, SecurityEventService.TERMINAL_STATUSES))
            .thenReturn(List.of(escalated));

        List<SecurityEvent> open = svc.openEvents(ORG);

        // Pre-fix this listed NEW/TRIAGED/IN_ANALYSIS/CONTAINED and dropped ESCALATED, so `open`
        // would be empty and this assertion RED.
        assertThat(open).extracting(SecurityEvent::getStatus).containsExactly("ESCALATED");
        verify(events).findByOrganisationIdAndStatusNotInOrderByEventTimeDesc(
            ORG, SecurityEventService.TERMINAL_STATUSES);
    }

    // ── Leg D (vocabulary pin): the terminal set is exactly the two states resolve() writes ──

    @Test
    @DisplayName("F2-367: TERMINAL_STATUSES = {RESOLVED, FALSE_POSITIVE}; ESCALATED/NEW/TRIAGED are open")
    void terminalStatusesAreExactlyTheClosedStates() {
        assertThat(SecurityEventService.TERMINAL_STATUSES)
            .containsExactlyInAnyOrder("RESOLVED", "FALSE_POSITIVE");
        assertThat(SecurityEventService.TERMINAL_STATUSES)
            .as("ESCALATED / NEW / TRIAGED are open work, not terminal")
            .doesNotContain("ESCALATED", "NEW", "TRIAGED");
    }

    // ── Leg C (drift guard): the SLA sweep @Query excludes exactly the same terminal set ──

    @Test
    @DisplayName("F2-367: the SLA-sweep @Query's NOT IN set equals TERMINAL_STATUSES (F2-299/F2-343)")
    void theSlaSweepQueryExcludesExactlyTheTerminalSet() throws Exception {
        String repo = stripComments(Files.readString(Path.of(
            "src/main/java/com/tss/grc/module/socops/repository/SecurityEventRepository.java")));
        Matcher m = Pattern.compile("status\\s+NOT\\s+IN\\s*\\(([^)]*)\\)").matcher(repo);
        assertThat(m.find()).as("the SLA sweep must declare a NOT IN (...) terminal set").isTrue();
        Set<String> excluded = Arrays.stream(m.group(1).split(","))
            .map(t -> t.trim().replace("'", "").replace("\"", ""))
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toSet());
        assertThat(excluded)
            .as("the dashboard constant and the SLA @Query must not drift apart")
            .isEqualTo(SecurityEventService.TERMINAL_STATUSES);
    }

    // ── Leg B (wiring + mechanism removal): get() reads openEvents(); the inclusion list is gone ──

    @Test
    @DisplayName("F2-367: get() derives open from the complement; the NEW/TRIAGED/IN_ANALYSIS/CONTAINED list is gone")
    void getDerivesOpenFromTheComplement_andTheInclusionListIsGone() throws Exception {
        String src = stripComments(Files.readString(Path.of(
            "src/main/java/com/tss/grc/module/socops/service/SocOpsDashboardService.java")));
        assertThat(src)
            .as("the dashboard must derive the open set from the complement finder")
            .contains("openEvents(org)")
            .contains("findByOrganisationIdAndStatusNotInOrderByEventTimeDesc")
            .contains("SecurityEventService.TERMINAL_STATUSES");
        assertThat(src)
            .as("the hand-typed inclusion list that dropped ESCALATED must be gone")
            .doesNotContain("\"IN_ANALYSIS\"")
            .doesNotContain("\"CONTAINED\"")
            .doesNotContain("findByOrganisationIdAndStatusOrderByEventTimeDesc(org, \"NEW\")");
    }

    // ── F2-420: the regulator-examination figures must not come off a notified_at window ──

    /** The service source, resolved once — the two structural legs both read it. */
    private static final String SERVICE_SRC =
        "src/main/java/com/tss/grc/module/socops/service/SocOpsDashboardService.java";

    /**
     * F2-420 — every {@code PageRequest.of(0, N)} left in {@code get()}, with the reason it is
     * allowed to stay. Asserted EXACTLY rather than as a ceiling: a new capped fetch must fail
     * here and be read, because whether a cap is a defect depends on whether its order is
     * aligned with the question the figure asks — which no regex can decide.
     */
    private static final Map<String, String> REMAINING_CAPS = Map.of(
        "100",  "scanRunsLast24h — capped under started_at DESC, which is ALIGNED with the "
              + "last-24h question it asks, so the window can only bite above 100 scanner runs "
              + "in a single day. The benign variant of this shape.",
        "1000", "scanCritsOpen — F2-367 FILED this as an owner decision: summing "
              + "findings_critical over historical runs double-counts a finding present in "
              + "consecutive scans, so the metric question has to be settled before the cap is "
              + "the interesting part.");

    /**
     * The discriminating leg. Notified-at order puts the LATER-due examination first, which is
     * exactly the row a notified_at-ordered window keeps while dropping the one due soonest — so
     * a service still reading the capped overload reports the wrong examination as next up.
     */
    @Test
    @DisplayName("F2-420: upcomingExams orders EVERY live examination by response_due_at, not the notified_at window")
    void theUpcomingListIsOrderedOverEveryExamNotTheNotifiedAtWindow() {
        stubPagedFetchesEmpty();
        LocalDateTime now = LocalDateTime.now();
        RegulatorExamination notifiedRecentlyDueLater = exam(1L, "EX-LATER", "SAMA", now.plusDays(40));
        RegulatorExamination notifiedLongAgoDueSoon   = exam(2L, "EX-SOON",  "NCA",  now.plusDays(3));

        when(regulatorExams.findByOrganisationIdOrderByNotifiedAtDesc(ORG))
            .thenReturn(List.of(notifiedRecentlyDueLater, notifiedLongAgoDueSoon));
        // Had the service kept the capped overload it would see only the recently-notified row.
        when(regulatorExams.findByOrganisationIdOrderByNotifiedAtDesc(anyLong(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(notifiedRecentlyDueLater)));

        SocOpsDashboardResponse snap = svc.get();

        assertThat(snap.upcomingExams())
            .as("the soonest-due examination must lead the list even though it was notified longest ago")
            .extracting(SocOpsDashboardResponse.RegulatorExamBucket::examinationCode)
            .containsExactly("EX-SOON", "EX-LATER");
        assertThat(snap.regulatorExamsByRegulator())
            .as("the per-regulator breakdown must count every live examination, not a window")
            .containsEntry("SAMA", 1L)
            .containsEntry("NCA", 1L);
        verify(regulatorExams, never())
            .findByOrganisationIdOrderByNotifiedAtDesc(anyLong(), any(Pageable.class));
    }

    /**
     * F2-420 — the fortnight KPI and {@code SocOpsScheduledSweep.sweepRegulatorResponseDue} were
     * two implementations of one predicate over the same band, and only the KPI was capped. They
     * read one query now, so the scheduler can no longer page someone about an examination the
     * dashboard does not count.
     */
    @Test
    @DisplayName("F2-420: the due-soon KPI reads the same query the scheduler escalates from, over the same 14-day band")
    void dueSoonReadsTheSameQueryTheSchedulerEscalatesFrom() {
        stubPagedFetchesEmpty();
        when(regulatorExams.findByOrganisationIdOrderByNotifiedAtDesc(ORG)).thenReturn(List.of());
        when(regulatorExams.findResponseDueBetween(anyLong(), any(), any()))
            .thenReturn(List.of(exam(1L, "A", "SAMA", null),
                                exam(2L, "B", "NCA", null),
                                exam(3L, "C", "CMA", null)));

        SocOpsDashboardResponse snap = svc.get();

        assertThat(snap.regulatorExamsResponseDueSoon())
            .as("the KPI is the size of the authoritative query's result, not a Java re-filter")
            .isEqualTo(3L);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to   = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(regulatorExams).findResponseDueBetween(eq(ORG), from.capture(), to.capture());
        assertThat(Duration.between(from.getValue(), to.getValue()).toDays())
            .as("the band must be the 14 days SocOpsScheduledSweep escalates on")
            .isEqualTo(14L);
    }

    /** Structural: the window mechanism is gone and the uncapped reads are wired in. */
    @Test
    @DisplayName("F2-420: the notified_at window is gone from the source")
    void theRegulatorExamWindowIsGoneFromTheSource() throws Exception {
        String src = stripComments(Files.readString(Path.of(SERVICE_SRC)));
        assertThat(src)
            .as("the 200-row notified_at window must be gone")
            .doesNotContain("PageRequest.of(0, 200)")
            .doesNotContain("examPage");
        assertThat(src)
            .as("all three exam figures must read the uncapped finder / the authoritative query")
            .contains("findByOrganisationIdOrderByNotifiedAtDesc(org)")
            .contains("findResponseDueBetween(org, now, now.plusDays(14))");
    }

    /**
     * Census. A blanket ban on capped fetches would fail on correct code (F2-214): {@code
     * PageRequest.of(0, 1)} is a point lookup — {@code tiTotal} reads {@code getTotalElements()},
     * whose value does not depend on the page size at all, and {@code awarenessRate} takes {@code
     * findFirst()} of a started_at DESC page, where the cap order IS the question. So the residual
     * is counted and named instead, which keeps it visible (F2-205) without forcing a filed
     * owner decision.
     */
    @Test
    @DisplayName("F2-420: every capped fetch still feeding a dashboard figure is named")
    void everyRemainingCappedAggregateIsNamed() throws Exception {
        String src = stripComments(Files.readString(Path.of(SERVICE_SRC)));
        Matcher m = Pattern.compile("PageRequest\\.of\\(0,\\s*(\\d+)\\)").matcher(src);
        List<String> caps = new ArrayList<>();
        while (m.find()) {
            caps.add(m.group(1));
        }
        assertThat(caps)
            .as("a new capped fetch must be read and named here, not absorbed into a ceiling")
            .containsExactlyInAnyOrder("1", "1", "100", "1000");
        assertThat(REMAINING_CAPS.keySet())
            .as("every cap above 1 owes a reason; a point lookup (N=1) does not")
            .containsExactlyInAnyOrderElementsOf(
                caps.stream().filter(c -> !"1".equals(c)).collect(Collectors.toSet()));
        assertThat(REMAINING_CAPS.values())
            .allSatisfy(reason -> assertThat(reason.length())
                .as("a bare label is not a reason (F2-188)")
                .isGreaterThan(60));
    }

    /**
     * {@code get()} reads four other paged finders; Mockito returns null for a {@code Page}, so
     * they are stubbed empty to keep these legs about the examination figures alone.
     */
    private void stubPagedFetchesEmpty() {
        when(threatIntel.findByOrganisationIdOrderByPublishedAtDesc(anyLong(), any(Pageable.class)))
            .thenReturn(Page.empty());
        when(scannerRuns.findByOrganisationIdOrderByStartedAtDesc(anyLong(), any(Pageable.class)))
            .thenReturn(Page.empty());
        when(awareness.findByOrganisationIdOrderByStartedAtDesc(anyLong(), any(Pageable.class)))
            .thenReturn(Page.empty());
    }

    private static RegulatorExamination exam(Long id, String code, String regulator, LocalDateTime dueAt) {
        RegulatorExamination e = new RegulatorExamination();
        e.setId(id);
        e.setOrganisationId(ORG);
        e.setExaminationCode(code);
        e.setRegulator(regulator);
        e.setTitleEn("Thematic review");
        e.setResponseDueAt(dueAt);
        return e;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static SecurityEvent ev(Long id, String status, String severity) {
        SecurityEvent e = new SecurityEvent();
        e.setId(id);
        e.setOrganisationId(ORG);
        e.setStatus(status);
        e.setSeverity(severity);
        return e;
    }

    /** F2-193 — strip comments so a scan reads code, not the prose that explains the fix. */
    private static String stripComments(String s) {
        return s.replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", " ");
    }
}
