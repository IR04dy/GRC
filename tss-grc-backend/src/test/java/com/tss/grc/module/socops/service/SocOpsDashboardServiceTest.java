package com.tss.grc.module.socops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.common.tenant.TenantContext;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
