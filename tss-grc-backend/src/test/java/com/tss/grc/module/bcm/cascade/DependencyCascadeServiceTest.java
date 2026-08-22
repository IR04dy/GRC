package com.tss.grc.module.bcm.cascade;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.entity.ProcessDependency;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.module.bcm.repository.ProcessDependencyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * F2-283 — the first test of {@link DependencyCascadeService}.
 *
 * <p>It had none. [[F2-248]]'s rule is that a path with no test is not a path
 * somebody decided was safe, and [[F2-282]] had just changed how this service
 * chooses which BIA states a process's recovery objectives — with a source-scanning
 * detector carrying the whole weight for this caller, because there was nothing
 * else here.
 *
 * <p>The service is a pure read aggregator: BFS from a failed process over
 * {@code bcm.process_dependencies}, a depth-weighted reach hour, and a breach
 * verdict against that process's MTPD (or RTO when MTPD is absent). Everything
 * asserted here was previously unasserted.
 *
 * <h2>Two latent defects this pins</h2>
 *
 * <p><b>A soft-deleted process must not CONDUCT the cascade.</b> The BFS enqueued
 * a consumer before checking the tenant's process register returned it, so a
 * deleted process was dropped from the output and still propagated reach to the
 * live consumers behind it. Measured 2026-08-22: 0 dependency edges name a deleted
 * process in either database, so it was latent — and reachability is a fact about
 * today, not a property of a control ([[F2-182]]).
 *
 * <p><b>{@code SOVEREIGN} was ranked below {@code LOW}.</b> It is the platform's
 * own top tier and {@code bcm.critical_processes.criticality} has no CHECK, so the
 * most critical process in a tie group sorted last.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DependencyCascadeServiceTest {

    private static final Long ORG = 1L;

    @Mock private CriticalProcessRepository processes;
    @Mock private ProcessDependencyRepository deps;
    @Mock private BiaRecordRepository bias;
    @InjectMocks private DependencyCascadeService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── fixtures ──────────────────────────────────────────────────────────

    private static CriticalProcess process(long id, String name, String criticality) {
        CriticalProcess p = new CriticalProcess();
        p.setId(id);
        p.setOrganisationId(ORG);
        p.setProcessNameEn(name);
        p.setProcessNameAr("عملية " + id);
        p.setCriticality(criticality);
        return p;
    }

    /** {@code consumer} depends on {@code upstream}: upstream failing hurts consumer. */
    private static ProcessDependency dep(long id, long consumer, long upstream) {
        ProcessDependency d = new ProcessDependency();
        d.setId(id);
        d.setOrganisationId(ORG);
        d.setProcessId(consumer);
        d.setDependsOnProcessId(upstream);
        return d;
    }

    private static BiaRecord bia(long processId, Integer mtpd, Integer rto, String status) {
        BiaRecord b = new BiaRecord();
        b.setProcessId(processId);
        b.setMtpdHours(mtpd);
        b.setRtoHours(rto);
        b.setStatus(status);
        b.setAssessmentDate(LocalDate.of(2026, 1, 1));
        return b;
    }

    private void given(List<CriticalProcess> procs, List<ProcessDependency> edges,
                       List<BiaRecord> biaRows) {
        when(processes.findByOrganisationId(ORG)).thenReturn(procs);
        when(deps.findByOrganisationId(ORG)).thenReturn(edges);
        when(bias.findByProcessIdIn(anyList())).thenReturn(biaRows);
    }

    private static DependencyCascadeService.ImpactRow row(
            DependencyCascadeService.CascadeResult r, long processId) {
        return r.impacts().stream().filter(x -> x.processId() == processId)
                .findFirst().orElseThrow(() ->
                    new AssertionError("process " + processId + " is not in the impact list"));
    }

    // ─── the walk ──────────────────────────────────────────────────────────

    @Test
    void anUnknownSourceIsRefusedRatherThanReturningAnEmptyCascade() {
        given(List.of(process(1, "Core Banking", "CRITICAL")), List.of(), List.of());

        assertThatThrownBy(() -> service.simulate(99L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Critical process not found in tenant: 99");
    }

    @Test
    void bfsWalksTheChainAndRecordsTheShallowestDepthReached() {
        // 1 -> 2 -> 3 -> 4, and 1 -> 4 directly. 4 must record depth 1, not 3.
        given(List.of(process(1, "Core", "CRITICAL"), process(2, "Cards", "HIGH"),
                      process(3, "Reporting", "MEDIUM"), process(4, "Channels", "HIGH")),
              List.of(dep(10, 2, 1), dep(11, 3, 2), dep(12, 4, 3), dep(13, 4, 1)),
              List.of());

        var r = service.simulate(1L);

        assertThat(r.totalImpactedCount()).isEqualTo(3);
        assertThat(row(r, 2L).depth()).isEqualTo(1);
        assertThat(row(r, 3L).depth()).isEqualTo(2);
        assertThat(row(r, 4L).depth())
            .as("F2-283: BFS records the SHALLOWEST depth, so a shortcut edge wins")
            .isEqualTo(1);
        assertThat(r.impacts()).noneMatch(x -> x.processId() == 1L);
    }

    @Test
    void aCycleBackToTheSourceTerminatesRatherThanLooping() {
        given(List.of(process(1, "A", "HIGH"), process(2, "B", "HIGH")),
              List.of(dep(10, 2, 1), dep(11, 1, 2)),   // 1 -> 2 -> 1
              List.of());

        var r = service.simulate(1L);

        assertThat(r.totalImpactedCount()).isEqualTo(1);
        assertThat(row(r, 2L).depth()).isEqualTo(1);
    }

    @Test
    void theWalkStopsAtMaxDepth() {
        // A chain longer than MAX_DEPTH: 1 -> 2 -> ... -> 9
        List<CriticalProcess> procs = new ArrayList<>();
        List<ProcessDependency> edges = new ArrayList<>();
        for (int i = 1; i <= 9; i++) procs.add(process(i, "P" + i, "MEDIUM"));
        for (int i = 2; i <= 9; i++) edges.add(dep(100 + i, i, i - 1));
        given(procs, edges, List.of());

        var r = service.simulate(1L);

        assertThat(DependencyCascadeService.MAX_DEPTH).isEqualTo(6);
        assertThat(r.totalImpactedCount())
            .as("F2-283: the cascade signal is cut at MAX_DEPTH, so 6 hops and no more")
            .isEqualTo(6);
        assertThat(r.impacts()).allSatisfy(x -> assertThat(x.depth()).isLessThanOrEqualTo(6));
    }

    /**
     * F2-283's first latent fix. The deleted process is absent from the register, so
     * it must neither appear in the output NOR pass the cascade to what sits behind
     * it — before the fix, 3 was reported at depth 2 through a process this tenant
     * cannot see.
     */
    @Test
    void aSoftDeletedProcessNeitherAppearsNorConductsTheCascade() {
        // 1 -> 2(deleted, so absent from the register) -> 3
        given(List.of(process(1, "Core", "CRITICAL"), process(3, "Behind", "CRITICAL")),
              List.of(dep(10, 2, 1), dep(11, 3, 2)),
              List.of());

        var r = service.simulate(1L);

        assertThat(r.impacts()).noneMatch(x -> x.processId() == 2L);
        assertThat(r.impacts())
            .as("F2-283: a process the register does not return may not conduct reach")
            .noneMatch(x -> x.processId() == 3L);
        assertThat(r.totalImpactedCount()).isZero();
    }

    // ─── the reach and breach verdict ───────────────────────────────────────

    @Test
    void theDepthLadderAndItsConfidenceAreTheDeclaredHeuristic() {
        assertThat(DependencyCascadeService.depthToHour(1)).isEqualTo(1);
        assertThat(DependencyCascadeService.depthToHour(2)).isEqualTo(4);
        assertThat(DependencyCascadeService.depthToHour(3)).isEqualTo(8);
        assertThat(DependencyCascadeService.depthToHour(4)).isEqualTo(24);
        assertThat(DependencyCascadeService.depthToHour(6)).isEqualTo(24);

        assertThat(DependencyCascadeService.depthToConfidence(1)).isEqualTo(1.0);
        assertThat(DependencyCascadeService.depthToConfidence(2)).isEqualTo(0.75);
        assertThat(DependencyCascadeService.depthToConfidence(3)).isEqualTo(0.5);
        assertThat(DependencyCascadeService.depthToConfidence(4)).isEqualTo(0.25);
    }

    @Test
    void aProcessBreachesWhenTheReachHourMeetsItsMtpd() {
        // depth 1 -> reach 1h. MTPD 1 breaches (>=), MTPD 2 does not.
        given(List.of(process(1, "Core", "CRITICAL"),
                      process(2, "Tight", "CRITICAL"), process(3, "Slack", "HIGH")),
              List.of(dep(10, 2, 1), dep(11, 3, 1)),
              List.of(bia(2L, 1, 1, "APPROVED"), bia(3L, 2, 1, "APPROVED")));

        var r = service.simulate(1L);

        assertThat(row(r, 2L).breached()).isTrue();
        assertThat(row(r, 3L).breached())
            .as("F2-283: the test is reachHour >= MTPD, so a 2h tolerance survives 1h")
            .isFalse();
        assertThat(r.breachedCount()).isEqualTo(1);
    }

    @Test
    void rtoIsTheThresholdWhenNoMtpdIsStated() {
        // RTO <= MTPD by definition, so falling back to RTO is the CONSERVATIVE
        // direction: it can only report a breach earlier, never later.
        given(List.of(process(1, "Core", "CRITICAL"), process(2, "NoMtpd", "HIGH")),
              List.of(dep(10, 2, 1)),
              List.of(bia(2L, null, 1, "APPROVED")));

        var r = service.simulate(1L);

        assertThat(row(r, 2L).mtpdHours()).isNull();
        assertThat(row(r, 2L).rtoHours()).isEqualTo(1);
        assertThat(row(r, 2L).breached()).isTrue();
    }

    @Test
    void aProcessWithNoBiaAtAllIsReportedAndNeverBreached() {
        given(List.of(process(1, "Core", "CRITICAL"), process(2, "Unassessed", "HIGH")),
              List.of(dep(10, 2, 1)),
              List.of());

        var r = service.simulate(1L);

        assertThat(row(r, 2L).mtpdHours()).isNull();
        assertThat(row(r, 2L).mtpdSourceStatus()).isNull();
        assertThat(row(r, 2L).breached())
            .as("F2-283: no objective is not a breach — it is an unmeasurable one, and "
                + "claiming a breach would invent a tolerance nobody recorded")
            .isFalse();
        assertThat(r.totalImpactedCount()).isEqualTo(1);
    }

    /**
     * F2-282's disclosure, asserted end to end: the objective and the approval
     * status of the BIA it came from must describe the SAME row, or the disclosure
     * is worse than none.
     */
    @Test
    void theObjectiveAndItsApprovalStatusComeFromTheSameBiaRow() {
        // The live shape: an older APPROVED record stating 72h and a newer
        // IN_PROGRESS one stating 1h. The shared predicate takes the lowest.
        given(List.of(process(1, "Core", "CRITICAL"), process(2, "Downstream", "CRITICAL")),
              List.of(dep(10, 2, 1)),
              List.of(bia(2L, 72, 4, "APPROVED"), bia(2L, 1, 1, "IN_PROGRESS")));

        var r = service.simulate(1L);

        assertThat(row(r, 2L).mtpdHours()).isEqualTo(1);
        assertThat(row(r, 2L).mtpdSourceStatus())
            .as("F2-282: the status must name the row the 1h came from, not the other")
            .isEqualTo("IN_PROGRESS");
    }

    // ─── the summary and the ordering ───────────────────────────────────────

    @Test
    void theHourBandsAreCumulativeAndTheWidestEqualsTheTotal() {
        given(List.of(process(1, "Core", "CRITICAL"), process(2, "D1", "HIGH"),
                      process(3, "D2", "HIGH"), process(4, "D3", "HIGH")),
              List.of(dep(10, 2, 1), dep(11, 3, 2), dep(12, 4, 3)),
              List.of(bia(2L, 1, 1, "APPROVED")));   // only the depth-1 row breaches

        var r = service.simulate(1L);
        Map<Integer, DependencyCascadeService.BandSummary> bands = r.byHourBand();

        assertThat(bands.keySet()).containsExactly(1, 4, 8, 24);
        assertThat(bands.get(1).impacted()).isEqualTo(1);
        assertThat(bands.get(4).impacted()).isEqualTo(2);
        assertThat(bands.get(8).impacted()).isEqualTo(3);
        assertThat(bands.get(24).impacted())
            .as("F2-283: depthToHour caps at 24, so the widest band is the whole list")
            .isEqualTo(r.totalImpactedCount());
        assertThat(bands.get(1).breached()).isEqualTo(1);
        assertThat(bands.get(24).breached()).isEqualTo(r.breachedCount());
    }

    @Test
    void breachedRowsSortFirstThenShallowestThenMostCritical() {
        given(List.of(process(1, "Core", "CRITICAL"),
                      process(2, "d1-low", "LOW"),
                      process(3, "d1-critical", "CRITICAL"),
                      process(4, "d2-breached", "MEDIUM")),
              List.of(dep(10, 2, 1), dep(11, 3, 1), dep(12, 4, 2)),
              List.of(bia(4L, 1, 1, "APPROVED")));   // depth 2 -> reach 4h >= 1 -> breached

        var r = service.simulate(1L);

        assertThat(r.impacts().get(0).processId())
            .as("F2-283: a breach outranks a shallower non-breach")
            .isEqualTo(4L);
        assertThat(r.impacts().get(1).processId())
            .as("F2-283: within one depth, the more critical process comes first")
            .isEqualTo(3L);
        assertThat(r.impacts().get(2).processId()).isEqualTo(2L);
    }

    /**
     * F2-283's second latent fix. {@code SOVEREIGN} is the platform's own top tier
     * ({@code BcmBiaAssessmentService} computes it and emits
     * {@code BIA_CRITICALITY_SOVEREIGN}) and {@code critical_processes.criticality}
     * has no CHECK — so before the fix the most critical process sorted BELOW LOW.
     */
    @Test
    void sovereignOutranksCriticalAndAnUnknownTokenSortsLast() {
        given(List.of(process(1, "Core", "CRITICAL"),
                      process(2, "critical", "CRITICAL"),
                      process(3, "sovereign", "SOVEREIGN"),
                      process(4, "tier-2", "TIER_2")),   // a token no CHECK forbids
              List.of(dep(10, 2, 1), dep(11, 3, 1), dep(12, 4, 1)),
              List.of());

        var r = service.simulate(1L);

        assertThat(r.impacts().stream().map(x -> x.processId()).toList())
            .as("F2-283: SOVEREIGN first, then CRITICAL, and the unclassified token last")
            .containsExactly(3L, 2L, 4L);
    }

    @Test
    void theSourceProcessIsDescribedButNeverListedAsImpacted() {
        given(List.of(process(1, "Core Banking Operations", "CRITICAL"),
                      process(2, "Downstream", "HIGH")),
              List.of(dep(10, 2, 1)),
              List.of());

        var r = service.simulate(1L);

        assertThat(r.sourceProcessId()).isEqualTo(1L);
        assertThat(r.sourceProcessNameEn()).isEqualTo("Core Banking Operations");
        assertThat(r.sourceProcessNameAr()).isEqualTo("عملية 1");
        assertThat(r.sourceCriticality()).isEqualTo("CRITICAL");
        assertThat(r.impacts()).noneMatch(x -> x.processId() == 1L);
    }

    @Test
    void aProcessWithNoDependentsCascadesToNothing() {
        given(List.of(process(1, "Isolated", "CRITICAL")), List.of(), List.of());

        var r = service.simulate(1L);

        assertThat(r.totalImpactedCount()).isZero();
        assertThat(r.breachedCount()).isZero();
        assertThat(r.byHourBand().values())
            .allSatisfy(b -> assertThat(b.impacted()).isZero());
    }
}
