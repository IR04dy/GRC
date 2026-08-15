package com.tss.grc.module.audit_lc.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.AnnualPlan;
import com.tss.grc.module.audit_lc.entity.CapaTask;
import com.tss.grc.module.audit_lc.entity.WorkingPaper;
import com.tss.grc.module.audit_lc.repository.AnnualPlanRepository;
import com.tss.grc.module.audit_lc.repository.CapaTaskRepository;
import com.tss.grc.module.audit_lc.repository.WorkingPaperRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-217 — a create may not name a governance state, nor the authority that reached it.
 *
 * <p>All three {@code audit_lc} registers persist the request entity, and each validated the
 * supplied {@code status} only for membership of its vocabulary. Probed live 2026-08-15 as
 * {@code compliance}: {@code POST /api/audit-lc/annual-plans} with
 * {@code status=AC_APPROVED, acApprovedBy="Nobody Who Approved This",
 * acApprovedAt=2001-01-01} answered <b>200</b> and stored all three, while
 * {@code POST /annual-plans/{id}/transition {"to":"AC_APPROVED"}} answered <b>409</b> on a DRAFT
 * plan. Same for a working paper created {@code APPROVED} with a chosen {@code reviewer} (IIA
 * Standard 2330 review attribution) and a CAPA created {@code CLOSED} with a backdated
 * {@code closedAt}.
 *
 * <p>Each register gets four legs — the refusal, the cleared stamp, the two shapes that must keep
 * working (an omitted status and an explicit initial one) — plus a leg proving the transition
 * still stamps what the create no longer accepts, because a fix that merely stopped the column
 * being written would be indistinguishable here from one that broke the feature.
 */
@ExtendWith(MockitoExtension.class)
class AuditLifecycleCreateAuthorityTest {

    private static final Long ORG_ID = 1L;

    @Mock private AnnualPlanRepository   annualPlans;
    @Mock private WorkingPaperRepository workingPapers;
    @Mock private CapaTaskRepository     capas;
    @InjectMocks private AuditLifecycleService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        lenient().when(annualPlans.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(workingPapers.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(capas.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private static AnnualPlan plan() {
        AnnualPlan p = new AnnualPlan();
        p.setExternalId("AP-2031");
        p.setYear(2031);
        return p;
    }

    private static WorkingPaper wp() {
        WorkingPaper w = new WorkingPaper();
        w.setExternalId("WP-X");
        w.setEngagementCode("IA-2026-001");
        w.setTitleEn("Design test");
        return w;
    }

    private static CapaTask capa() {
        CapaTask c = new CapaTask();
        c.setExternalId("CAPA-X");
        c.setFindingId("F-2026-001");
        c.setTargetDate(LocalDate.now().plusDays(30));
        return c;
    }

    // ─── Annual plan: the Audit Committee approval ───────────────────────────

    @Test
    void anAnnualPlanCannotBeCreatedAlreadyApprovedByTheAuditCommittee() {
        AnnualPlan p = plan();
        p.setStatus("AC_APPROVED");

        assertThatThrownBy(() -> service.createAnnualPlan(p))
            .as("Creating straight into AC_APPROVED skips DRAFT -> AC_REVIEW -> AC_APPROVED and the "
                + "actor stamp that transitionAnnualPlan applies. It answered 200 before F2-217.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("AC_APPROVED")
            .hasMessageContaining("transition");

        verify(annualPlans, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void anAnnualPlanCreateNeverKeepsAnApproverTheCallerSupplied() {
        AnnualPlan p = plan();
        p.setAcApprovedBy("Nobody Who Approved This");
        p.setAcApprovedAt(LocalDateTime.of(2001, 1, 1, 0, 0));

        AnnualPlan saved = service.createAnnualPlan(p);

        assertThat(saved.getAcApprovedBy())
            .as("acApprovedBy is stamped by transitionAnnualPlan when it reaches AC_APPROVED. A plan "
                + "being created has been approved by nobody, so a supplied value can only be a "
                + "claim about an approval that did not happen.")
            .isNull();
        assertThat(saved.getAcApprovedAt()).isNull();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void anAnnualPlanCreateStillAcceptsAnOmittedOrExplicitlyInitialStatus() {
        assertThat(service.createAnnualPlan(plan()).getStatus())
            .as("A caller that says nothing gets the initial state — unchanged behaviour")
            .isEqualTo("DRAFT");

        AnnualPlan explicit = plan();
        explicit.setStatus("DRAFT");
        assertThat(service.createAnnualPlan(explicit).getStatus())
            .as("lib/auditLifecycle.ts sends the initial state explicitly on two of its three "
                + "creates; refusing that would break a working request, which a guard must not do")
            .isEqualTo("DRAFT");
    }

    @Test
    void theGuardedTransitionStillStampsTheApproverTheCreateRefuses() {
        AnnualPlan existing = plan();
        existing.setStatus("AC_REVIEW");
        when(annualPlans.findByOrganisationIdAndExternalId(ORG_ID, "AP-2031"))
            .thenReturn(Optional.of(existing));

        AnnualPlan approved = service.transitionAnnualPlan("AP-2031", "AC_APPROVED", "so_officer");

        assertThat(approved.getStatus()).isEqualTo("AC_APPROVED");
        assertThat(approved.getAcApprovedBy())
            .as("The route that IS allowed to approve must still record who did — otherwise this "
                + "slice would have closed the bypass by disabling the feature")
            .isEqualTo("so_officer");
        assertThat(approved.getAcApprovedAt()).isNotNull();
    }

    // ─── Working paper: the IIA Standard 2330 review attribution ─────────────

    @Test
    void aWorkingPaperCannotBeCreatedAlreadyReviewed() {
        WorkingPaper w = wp();
        w.setStatus("APPROVED");

        assertThatThrownBy(() -> service.createWorkingPaper(w))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("transition");
    }

    @Test
    void aWorkingPaperCreateNeverKeepsAReviewerTheCallerSupplied() {
        WorkingPaper w = wp();
        w.setReviewer("Nobody Who Reviewed This");
        w.setReviewedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        w.setAuthor("stamped-by-the-controller");

        WorkingPaper saved = service.createWorkingPaper(w);

        assertThat(saved.getReviewer())
            .as("F2-203 fixed `author` on this very method and its comment says the same rule "
                + "'already applies to reviewer'. True of transitionWorkingPaper; false of the "
                + "create it is written on — the live probe returned the correct author beside a "
                + "reviewer the caller had chosen.")
            .isNull();
        assertThat(saved.getReviewedAt()).isNull();
        assertThat(saved.getAuthor())
            .as("author is the controller's to stamp and this fix must not disturb it")
            .isEqualTo("stamped-by-the-controller");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void theGuardedTransitionStillStampsTheReviewerTheCreateRefuses() {
        WorkingPaper existing = wp();
        existing.setStatus("PEER_REVIEW");
        when(workingPapers.findByOrganisationIdAndExternalId(ORG_ID, "WP-X"))
            .thenReturn(Optional.of(existing));

        WorkingPaper approved = service.transitionWorkingPaper("WP-X", "APPROVED", "auditor");

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getReviewer()).isEqualTo("auditor");
        assertThat(approved.getReviewedAt()).isNotNull();
    }

    // ─── CAPA: closure without verification ─────────────────────────────────

    @Test
    void aCapaCannotBeCreatedAlreadyClosed() {
        CapaTask c = capa();
        c.setStatus("CLOSED");

        assertThatThrownBy(() -> service.createCapa(c))
            .as("CLOSED is reachable only from AWAITING_VERIFICATION. A CAPA created CLOSED is a "
                + "corrective action that was never verified.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("transition");
    }

    @Test
    void aCapaCreateNeverKeepsAClosureTimestampTheCallerSupplied() {
        CapaTask c = capa();
        c.setClosedAt(LocalDateTime.of(2001, 1, 1, 0, 0));

        CapaTask saved = service.createCapa(c);

        assertThat(saved.getClosedAt())
            .as("closedAt is stamped by transitionCapa on CLOSED; a backdated one on a create is "
                + "evidence of a closure that never happened")
            .isNull();
        assertThat(saved.getStatus()).isEqualTo("OPEN");
    }

    /**
     * The distinction the whole fix draws: a state the SERVER derives is still allowed to differ
     * from the initial one. Without this leg, "the create always ends up in the initial state"
     * would be an equally passing — and wrong — reading of the change.
     */
    @Test
    void aCapaCreateStillLetsTheServerDeriveOverdueFromTheTargetDate() {
        CapaTask c = capa();
        c.setTargetDate(LocalDate.now().minusDays(1));

        ArgumentCaptor<CapaTask> captor = ArgumentCaptor.forClass(CapaTask.class);
        service.createCapa(c);
        verify(capas).save(captor.capture());

        assertThat(captor.getValue().getStatus())
            .as("applyOverdue runs after the guard. Server-derived state is not a caller naming a "
                + "state, and this fix must not have collapsed the two.")
            .isEqualTo("OVERDUE");
    }
}
