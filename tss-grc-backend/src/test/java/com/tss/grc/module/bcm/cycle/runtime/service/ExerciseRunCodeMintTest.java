package com.tss.grc.module.bcm.cycle.runtime.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.repository.ExerciseScenarioInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.RunSummary;
import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos.StartRunRequest;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObservationRepository;
import com.tss.grc.module.bcm.cycle.runtime.observer.repository.ExerciseObserverAssignmentRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunInjectRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunResponseRepository;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-351 — {@code ExerciseRuntimeService#generateRunCode} must mint from the MAX ordinal + a
 * skip-loop, never from a COUNT.
 *
 * <p>The old body seeded from {@code countByOrganisationIdAndExerciseId + 1}. That count respects
 * the entity's {@code @Where(is_deleted = 0)}, so a soft-deleted run lowered it and the next mint
 * re-used a live ordinal → unique-index violation on the FILTERED index
 * {@code UX_exercise_run_run_code (organisation_id, run_code) WHERE is_deleted = 0} → HTTP 500 on
 * {@code startRun}. Two racing {@code startRun} calls for the same exercise are the live form; the
 * soft-delete gap is latent (no run soft-delete route). Same shape as F2-337 / F2-48.
 *
 * <p>These are hermetic (mocked repository) — a unit test cannot hit a real 8152, so the proof is
 * the MINTING logic: the code is derived from the MAX row's ordinal, the skip-loop steps over a
 * live code, and a caller override bypasses generation entirely. Reverting to the count-based body
 * makes the max/skip legs FAIL: the STRICT stubs on the new finders go unused and the reverted body
 * calls the un-stubbed {@code countBy…} finder (F2-274 — the test is coupled to the max contract).
 */
@ExtendWith(MockitoExtension.class)
class ExerciseRunCodeMintTest {

    private static final Long ORG_ID = 9L;
    private static final Long EXERCISE_ID = 700L;
    private static final Long RUN_ID = 5000L;
    private static final String PREFIX = "RUN-9-EX700-";

    @Mock private ExerciseRunRepository runRepo;
    @Mock private ExerciseRunInjectRepository runInjectRepo;
    @Mock private ExerciseRunResponseRepository responseRepo;
    @Mock private BcmExerciseRepository exerciseRepo;
    @Mock private ExerciseScenarioInjectRepository scenarioInjectRepo;
    @Mock private ExerciseObservationRepository observationRepo;
    @Mock private ExerciseObserverAssignmentRepository observerAssignmentRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private RealtimeBroadcaster realtime;
    @InjectMocks private ExerciseRuntimeService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        7L, ORG_ID, "facilitator", "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }

    @AfterEach void tearDown() {
        TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /** An exercise with no scenario link, so startRun clones no injects — isolates the minter. */
    private void stubExerciseAndSave() {
        BcmExercise ex = new BcmExercise();
        ex.setId(EXERCISE_ID);
        ex.setOrganisationId(ORG_ID);
        ex.setExerciseNameEn("Q2 Ransom Tabletop");
        ex.setExerciseNameAr("تمرين");
        ex.setExerciseType("TABLETOP");
        ex.setStatus("SCHEDULED");
        ex.setScenarioId(null);
        when(exerciseRepo.findById(EXERCISE_ID)).thenReturn(Optional.of(ex));
        when(runRepo.save(any(ExerciseRun.class))).thenAnswer(inv -> {
            ExerciseRun r = inv.getArgument(0);
            r.setId(RUN_ID);
            return r;
        });
    }

    private static ExerciseRun runWithCode(String code) {
        ExerciseRun r = new ExerciseRun();
        r.setId(4000L);
        r.setOrganisationId(ORG_ID);
        r.setExerciseId(EXERCISE_ID);
        r.setRunCode(code);
        return r;
    }

    private String mintedCode() {
        service.startRun(new StartRunRequest(EXERCISE_ID, null, null, null, null, null, null));
        ArgumentCaptor<ExerciseRun> saved = ArgumentCaptor.forClass(ExerciseRun.class);
        verify(runRepo).save(saved.capture());
        return saved.getValue().getRunCode();
    }

    @Test
    void firstRunOfAnExercise_mintsOrdinal001() {
        stubExerciseAndSave();
        when(runRepo.findTopByOrganisationIdAndExerciseIdOrderByRunCodeDesc(ORG_ID, EXERCISE_ID))
            .thenReturn(Optional.empty());
        when(runRepo.existsByOrganisationIdAndRunCode(ORG_ID, PREFIX + "001")).thenReturn(false);

        assertThat(mintedCode()).isEqualTo(PREFIX + "001");
    }

    /**
     * The DISCRIMINATING leg: the seed is the MAX row's ordinal, and {@code countBy…} is never
     * consulted. On the pre-fix (count-based) body this fails — the count finder is un-stubbed and
     * the STRICT stubs here go unused.
     */
    @Test
    void seedsFromMaxOrdinal_notCount() {
        stubExerciseAndSave();
        when(runRepo.findTopByOrganisationIdAndExerciseIdOrderByRunCodeDesc(ORG_ID, EXERCISE_ID))
            .thenReturn(Optional.of(runWithCode(PREFIX + "005")));
        when(runRepo.existsByOrganisationIdAndRunCode(ORG_ID, PREFIX + "006")).thenReturn(false);

        assertThat(mintedCode()).isEqualTo(PREFIX + "006");
        verify(runRepo, never()).countByOrganisationIdAndExerciseId(anyLong(), anyLong());
    }

    /** The skip-loop steps over a live code (the concurrency-race / re-use backstop). */
    @Test
    void skipsAnExistingLiveCode() {
        stubExerciseAndSave();
        when(runRepo.findTopByOrganisationIdAndExerciseIdOrderByRunCodeDesc(ORG_ID, EXERCISE_ID))
            .thenReturn(Optional.of(runWithCode(PREFIX + "005")));
        when(runRepo.existsByOrganisationIdAndRunCode(ORG_ID, PREFIX + "006")).thenReturn(true);
        when(runRepo.existsByOrganisationIdAndRunCode(ORG_ID, PREFIX + "007")).thenReturn(false);

        assertThat(mintedCode()).isEqualTo(PREFIX + "007");
    }

    /** A caller override (F2-350) bypasses generation entirely — no MAX read, no skip-loop. */
    @Test
    void callerOverrideBypassesGeneration() {
        stubExerciseAndSave();

        service.startRun(new StartRunRequest(
            EXERCISE_ID, null, null, null, null, null, "  RUN-CUSTOM-XYZ  "));

        ArgumentCaptor<ExerciseRun> saved = ArgumentCaptor.forClass(ExerciseRun.class);
        verify(runRepo).save(saved.capture());
        assertThat(saved.getValue().getRunCode()).isEqualTo("RUN-CUSTOM-XYZ");   // trimmed
        verify(runRepo, never())
            .findTopByOrganisationIdAndExerciseIdOrderByRunCodeDesc(anyLong(), anyLong());
        verify(runRepo, never()).existsByOrganisationIdAndRunCode(anyLong(), anyString());
    }

    /** A non-auto-format MAX (e.g. a prior override) leaves the seed at 1; the skip-loop is safe. */
    @Test
    void nonAutoFormatMaxLeavesSeedAtOne() {
        stubExerciseAndSave();
        when(runRepo.findTopByOrganisationIdAndExerciseIdOrderByRunCodeDesc(ORG_ID, EXERCISE_ID))
            .thenReturn(Optional.of(runWithCode(PREFIX + "CUSTOM")));   // tail is not digits
        when(runRepo.existsByOrganisationIdAndRunCode(ORG_ID, PREFIX + "001")).thenReturn(false);

        assertThat(mintedCode()).isEqualTo(PREFIX + "001");
    }
}
