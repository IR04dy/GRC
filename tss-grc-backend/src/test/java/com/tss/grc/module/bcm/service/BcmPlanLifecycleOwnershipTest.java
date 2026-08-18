package com.tss.grc.module.bcm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.bcm.repository.BcmPlanRepository;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGapRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-234 — the legacy BCM plan register at {@code /api/v1/bcm/legacy-plans}, still routed and still
 * rendered by {@code /bcm/legacy}.
 *
 * <p>{@code createPlan} was a bare {@code save(req)} — no state guard and <b>no vocabulary test of
 * any kind</b> — over a table with <b>zero CHECK constraints</b> on a NOT NULL {@code status}
 * (measured 2026-08-18, dev and prod identical). So a plan could be created APPROVED or INVOKED, and
 * an arbitrary string was storable; since each transition guard is an equality test against a named
 * state, such a row is <b>permanently untransitionable by every route</b> (F2-220's
 * {@code platform.issues} shape).
 *
 * <p>{@code updatePlan} wrote the status unconditionally, so the ordinary edit route performed
 * exactly the transitions the four guarded routes own, under the same {@code BCM:WRITE} permission.
 * There is no escalation to find: the object-level machine was the only control (F2-219's shape).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmPlanLifecycleOwnershipTest {

    private static final Long ORG_ID = 1L;

    @Mock private BcmPlanRepository               planRepo;
    @Mock private CriticalProcessRepository       processRepo;
    @Mock private BiaRecordRepository             biaRepo;
    @Mock private BcmExerciseRepository           exerciseRepo;
    @Mock private BcmGapRepository                gapRepo;
    @Mock private BcmCorrectiveActionRepository   capaRepo;

    @InjectMocks private BcmService service;

    @BeforeEach void tenant() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(i -> i.getArgument(0));
    }
    @AfterEach void clear() { TenantContext.clear(); }

    private static BcmPlan plan(String status) {
        BcmPlan p = new BcmPlan();
        p.setId(7L);
        p.setOrganisationId(ORG_ID);
        p.setPlanNameEn("Group continuity plan");
        p.setPlanNameAr("خطة استمرارية المجموعة");
        p.setPlanType("BCP");
        p.setStatus(status);
        return p;
    }

    @Test
    void createPlan_refusesAStatusOtherThanDraft() {
        BcmPlan p = plan("APPROVED");
        p.setId(null);

        assertThatThrownBy(() -> service.createPlan(p))
            .as("a plan created APPROVED was never submitted for review, and approvePlan() is the "
                + "only route that may set that state")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("submit-for-review");

        verify(planRepo, never()).save(any());
    }

    @Test
    void createPlan_refusesAStatusOutsideTheVocabularyAltogether() {
        BcmPlan p = plan("TOTALLY_MADE_UP");
        p.setId(null);

        assertThatThrownBy(() -> service.createPlan(p))
            .as("bcm.bcm_plans has no CHECK constraint, so before this an arbitrary string was "
                + "storable — and every transition guard is an equality test, which makes such a "
                + "row permanently untransitionable by any route")
            .isInstanceOf(IllegalArgumentException.class);

        verify(planRepo, never()).save(any());
    }

    @Test
    void createPlan_landsAtDraftWhenTheStatusIsOmitted() {
        BcmPlan p = plan(null);
        p.setId(null);

        assertThat(service.createPlan(p).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void updatePlan_refusesToPerformATransitionTheGuardedRoutesOwn() {
        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("DRAFT")));
        BcmPlan edit = plan("APPROVED");

        assertThatThrownBy(() -> service.updatePlan(7L, edit))
            .as("the edit route reached APPROVED without approvePlan()'s state check — same "
                + "permission on both routes, so the machine was the only control")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("submit-for-review");
    }

    /**
     * The narrowing that stops this failing on correct code (F2-214/F2-231): a form that
     * round-trips the row sends the status back unchanged, and that is not a transition.
     */
    @Test
    void updatePlan_acceptsAnEchoOfTheCurrentStatus() {
        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("UNDER_REVIEW")));
        BcmPlan edit = plan("UNDER_REVIEW");
        edit.setPlanNameEn("Group continuity plan (rev 2)");

        BcmPlan out = service.updatePlan(7L, edit);
        assertThat(out.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(out.getPlanNameEn()).isEqualTo("Group continuity plan (rev 2)");
    }

    @Test
    void updatePlan_acceptsAnOmittedStatus() {
        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("INVOKED")));
        BcmPlan edit = plan(null);
        edit.setPlanNameEn("Group continuity plan (rev 3)");

        assertThat(service.updatePlan(7L, edit).getStatus())
            .as("an absent status leaves the stored state alone rather than nulling a NOT NULL "
                + "column (F2-213's reset-is-worse-than-a-null)")
            .isEqualTo("INVOKED");
    }

    /** The four guarded routes must still work — a fix that closed the bypass by breaking the
     *  lifecycle would score identically on the assertions above. */
    @Test
    void theGuardedLifecycleRoutesStillWork() {
        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("DRAFT")));
        assertThat(service.submitForReview(7L).getStatus()).isEqualTo("UNDER_REVIEW");

        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("UNDER_REVIEW")));
        assertThat(service.approvePlan(7L).getStatus()).isEqualTo("APPROVED");

        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("APPROVED")));
        assertThat(service.invokePlan(7L).getStatus()).isEqualTo("INVOKED");

        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("INVOKED")));
        assertThat(service.restorePlan(7L).getStatus()).isEqualTo("APPROVED");

        when(planRepo.findById(7L)).thenReturn(Optional.of(plan("DRAFT")));
        assertThatThrownBy(() -> service.invokePlan(7L))
            .as("out-of-order calls must still be refused")
            .isInstanceOf(IllegalArgumentException.class);
    }
}
