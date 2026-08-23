package com.tss.grc.module.ndi.gap.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.entity.NdiGap;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.gap.repository.NdiGapRepository;
import com.tss.grc.module.ndi.integration.NdiCrossLinkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Locks Rule 7 (gap → CAPA rollup) and Rule 9 (progress 0..100):
 *
 * <ul>
 *   <li>Adding/updating a CAPA recomputes the parent gap's
 *       capa_count, capa_completed_count and rollup_progress.</li>
 *   <li>When every CAPA reaches COMPLETED, the gap auto-flips to
 *       RESOLVED with actualCloseDate stamped today.</li>
 *   <li>Updating a CAPA's status to COMPLETED forces progress=100 and
 *       stamps completion_date.</li>
 *   <li>progressPct outside [0,100] is rejected.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NdiGapServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long GAP_ID = 100L;

    @Mock private NdiGapRepository gaps;
    @Mock private NdiCorrectiveActionRepository capas;
    @Mock private NdiAuditPublisher audit;
    @Mock private NdiCrossLinkService crossLink;
    @InjectMocks private NdiGapService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static NdiGap gap() {
        NdiGap g = new NdiGap();
        g.setId(GAP_ID);
        g.setOrganisationId(ORG_ID);
        g.setCycleId(1L);
        g.setGapCode("GAP-001");
        g.setSeverity("HIGH");
        g.setStatus("OPEN");
        g.setOpenDate(LocalDate.now());
        g.setCapaCount(0);
        g.setCapaCompletedCount(0);
        g.setRollupProgress(0);
        g.setTitleEn("Demo gap");
        g.setSourceType("MANUAL");
        return g;
    }

    private static NdiCorrectiveAction capa(Long id, String status, int progressPct) {
        NdiCorrectiveAction c = new NdiCorrectiveAction();
        c.setId(id);
        c.setOrganisationId(ORG_ID);
        c.setGapId(GAP_ID);
        c.setActionEn("Do thing");
        c.setActionType("CONTROL");
        c.setStatus(status);
        c.setProgressPct(progressPct);
        return c;
    }

    @Test void addCapa_recomputesGapRollupToMeanProgress() {
        NdiGap g = gap();
        List<NdiCorrectiveAction> children = new ArrayList<>(List.of(
            capa(1L, "IN_PROGRESS", 40),
            capa(2L, "IN_PROGRESS", 60)
        ));

        when(gaps.findByIdAndOrganisationId(GAP_ID, ORG_ID)).thenReturn(Optional.of(g));
        when(capas.findByOrganisationIdAndGapIdOrderByIdAsc(ORG_ID, GAP_ID)).thenReturn(children);
        when(capas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gaps.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Adding a third CAPA at 0% — mean becomes (40+60+0)/3 = 33.
        children.add(capa(3L, "NOT_STARTED", 0));
        NdiCorrectiveAction newCapa = capa(3L, "NOT_STARTED", 0);
        newCapa.setId(null);
        service.createCapa(GAP_ID, newCapa);

        assertThat(g.getCapaCount()).isEqualTo(3);
        assertThat(g.getCapaCompletedCount()).isEqualTo(0);
        assertThat(g.getRollupProgress()).isEqualTo(33);
        assertThat(g.getStatus()).isEqualTo("IN_PROGRESS"); // bumped from OPEN since rollup > 0
    }

    @Test void completingAllCapas_autoResolvesGap() {
        NdiGap g = gap();
        // Two CAPAs already completed at 100%.
        List<NdiCorrectiveAction> children = new ArrayList<>(List.of(
            capa(1L, "COMPLETED", 100),
            capa(2L, "IN_PROGRESS", 80)
        ));

        when(gaps.findByIdAndOrganisationId(GAP_ID, ORG_ID)).thenReturn(Optional.of(g));
        when(capas.findByIdAndOrganisationId(eq(2L), eq(ORG_ID)))
            .thenReturn(Optional.of(children.get(1)));
        when(capas.findByOrganisationIdAndGapIdOrderByIdAsc(ORG_ID, GAP_ID)).thenReturn(children);
        when(capas.save(any())).thenAnswer(inv -> {
            // Mutate the children list so the post-save lookup reflects the new state.
            NdiCorrectiveAction saved = inv.getArgument(0);
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i).getId().equals(saved.getId())) {
                    children.set(i, saved);
                    break;
                }
            }
            return saved;
        });
        when(gaps.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Promote CAPA 2 to COMPLETED — all CAPAs now done.
        NdiCorrectiveAction patch = new NdiCorrectiveAction();
        patch.setStatus("COMPLETED");
        service.updateCapa(2L, patch);

        // Both progress should be 100 (rule auto-derives), gap resolves.
        assertThat(children.get(1).getProgressPct()).isEqualTo(100);
        assertThat(children.get(1).getStatus()).isEqualTo("COMPLETED");
        assertThat(children.get(1).getCompletionDate()).isEqualTo(LocalDate.now());

        assertThat(g.getRollupProgress()).isEqualTo(100);
        assertThat(g.getCapaCompletedCount()).isEqualTo(2);
        assertThat(g.getStatus()).isEqualTo("RESOLVED");
        assertThat(g.getActualCloseDate()).isEqualTo(LocalDate.now());
    }

    @Test void progressOutsideRange_rejected() {
        NdiGap g = gap();
        when(gaps.findByIdAndOrganisationId(GAP_ID, ORG_ID)).thenReturn(Optional.of(g));

        NdiCorrectiveAction bad = capa(null, "IN_PROGRESS", 150);
        bad.setId(null);

        assertThatThrownBy(() -> service.createCapa(GAP_ID, bad))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("progressPct must be 0..100");
    }

    // ── F2-302: the (status, actual_close_date) pair, and a create that cannot name a derived state

    /**
     * The create is a create. {@code NdiGap.onCreate()} has always defaulted the field, and a
     * default is not a guard — probed live, a create naming RESOLVED returned 200 and stored it, so
     * the state the CAPA rollup derives was assertable on a row with no CAPAs at all.
     */
    @Test
    void createGap_cannotNameAStateTheRollupDerives() {
        NdiGap body = gap();
        body.setId(null);
        body.setStatus("RESOLVED");

        assertThatThrownBy(() -> service.createGap(body))
            .isInstanceOf(IllegalArgumentException.class)
            // names the FIELD (F2-296) ...
            .hasMessageContaining("status:")
            // ... and states what THIS route guarantees, never the primitive's default sentence,
            // because this register has no state machine and its PUT records no actor (F2-302).
            .hasMessageContaining("consistent with the CAPA rollup")
            .hasMessageNotContainingAny("state machine", "who made the change");
    }

    @Test
    void createGap_withNoStatusLandsOpen() {
        NdiGap body = gap();
        body.setId(null);
        body.setStatus(null);
        when(gaps.save(any(NdiGap.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.createGap(body).getStatus()).isEqualTo(NdiGap.INITIAL_STATUS);
    }

    /** An echo of the initial state is not an attempt to skip anything. */
    @Test
    void createGap_mayStateTheInitialStateExplicitly() {
        NdiGap body = gap();
        body.setId(null);
        body.setStatus("OPEN");
        when(gaps.save(any(NdiGap.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.createGap(body).getStatus()).isEqualTo("OPEN");
    }

    /**
     * The pair moves together. The rollup sets RESOLVED and stamps the close date in the same
     * breath; the register's inline status {@code <select>} sends {@code {status}} and nothing else,
     * so before this the screen could produce RESOLVED with no close date.
     */
    @Test
    void updateGap_resolvingStampsTheCloseDate() {
        NdiGap stored = gap();
        when(gaps.findByIdAndOrganisationId(GAP_ID, ORG_ID)).thenReturn(Optional.of(stored));
        when(gaps.save(any(NdiGap.class))).thenAnswer(i -> i.getArgument(0));

        NdiGap patch = new NdiGap();
        patch.setStatus("RESOLVED");
        NdiGap saved = service.updateGap(GAP_ID, patch);

        assertThat(saved.getStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getActualCloseDate()).isEqualTo(LocalDate.now());
    }

    /** A date the caller states survives: it is applied before the status rule, which only fills. */
    @Test
    void updateGap_honoursACallerStatedCloseDate() {
        NdiGap stored = gap();
        when(gaps.findByIdAndOrganisationId(GAP_ID, ORG_ID)).thenReturn(Optional.of(stored));
        when(gaps.save(any(NdiGap.class))).thenAnswer(i -> i.getArgument(0));

        NdiGap patch = new NdiGap();
        patch.setStatus("RESOLVED");
        patch.setActualCloseDate(LocalDate.of(2026, 3, 1));

        assertThat(service.updateGap(GAP_ID, patch).getActualCloseDate())
            .isEqualTo(LocalDate.of(2026, 3, 1));
    }

    /** A gap that is open was not closed, so a stale close date cannot stand. */
    @Test
    void updateGap_reopeningClearsTheCloseDate() {
        NdiGap stored = gap();
        stored.setStatus("RESOLVED");
        stored.setActualCloseDate(LocalDate.of(2026, 5, 17));
        when(gaps.findByIdAndOrganisationId(GAP_ID, ORG_ID)).thenReturn(Optional.of(stored));
        when(gaps.save(any(NdiGap.class))).thenAnswer(i -> i.getArgument(0));

        NdiGap patch = new NdiGap();
        patch.setStatus("IN_PROGRESS");
        NdiGap saved = service.updateGap(GAP_ID, patch);

        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getActualCloseDate()).isNull();
    }

    /**
     * ⚠ The BOUNDARY, asserted in both directions. CLOSED and ACCEPTED_RISK are left alone: the
     * rollup refuses to move either, and nothing in this platform declares whether they carry a
     * close date — so stamping one would invent a rule rather than enforce a declared one
     * (F2-183). If a later slice decides closure must be dated, this test is the thing to change
     * deliberately rather than a behaviour to discover.
     */
    @Test
    void updateGap_closingNeitherStampsNorClearsTheDate() {
        NdiGap stored = gap();
        when(gaps.findByIdAndOrganisationId(GAP_ID, ORG_ID)).thenReturn(Optional.of(stored));
        when(gaps.save(any(NdiGap.class))).thenAnswer(i -> i.getArgument(0));

        NdiGap toClosed = new NdiGap();
        toClosed.setStatus("CLOSED");
        assertThat(service.updateGap(GAP_ID, toClosed).getActualCloseDate())
            .as("CLOSED does not stamp a date -- no writer in this platform declares that it should")
            .isNull();

        stored.setStatus("RESOLVED");
        stored.setActualCloseDate(LocalDate.of(2026, 5, 17));
        NdiGap toAccepted = new NdiGap();
        toAccepted.setStatus("ACCEPTED_RISK");
        assertThat(service.updateGap(GAP_ID, toAccepted).getActualCloseDate())
            .as("ACCEPTED_RISK does not clear an existing date either")
            .isEqualTo(LocalDate.of(2026, 5, 17));
    }

    /**
     * Structural: the pair has ONE writer. A second {@code setStatus("RESOLVED")} beside its own
     * date write is how the update path and the rollup came apart in the first place, and no
     * behaviour test can see a third writer being added later.
     */
    @Test
    void thePairHasASingleWriter() throws java.io.IOException {
        String svc = java.nio.file.Files.readString(java.nio.file.Paths.get(
            "src/main/java/com/tss/grc/module/ndi/gap/service/NdiGapService.java"));
        String code = com.tss.grc.support.GuardExpressions.stripComments(svc);

        assertThat(code).as(
            "The rollup must route through applyStatusAndCloseDate, not set RESOLVED itself.")
            .doesNotContain("setStatus(\"RESOLVED\")");
        assertThat(code.split("setActualCloseDate\\(LocalDate\\.now\\(\\)\\)", -1).length - 1)
            .as("Exactly one place may stamp the close date from the clock.")
            .isEqualTo(1);
    }
}
