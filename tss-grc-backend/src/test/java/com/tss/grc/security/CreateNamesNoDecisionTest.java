package com.tss.grc.security;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import com.tss.grc.module.admin.committee.repository.CommitteeActionItemRepository;
import com.tss.grc.module.admin.committee.service.CommitteeActionItemService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.privacy.dsa.entity.DataSharingAgreement;
import com.tss.grc.module.privacy.dsa.repository.DataSharingAgreementRepository;
import com.tss.grc.module.privacy.dsa.service.DataSharingAgreementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * F2-219 — the other two registers whose create could name a state a guarded route owns.
 *
 * <p>Both were found by correcting {@link ServerStampedFieldContractTest}: it had been counting
 * {@code if (x == null) x = INITIAL} as having <i>settled</i> the field, so a default dropped out of
 * the accepted set and neither register appeared on F2-218's work list. 🔑 <b>F2-218's own headline
 * — a default and a guard are indistinguishable to any scan that only asks whether the setter is
 * present — inside the gate F2-218 built to enforce it.</b>
 *
 * <p>Both probed live 2026-08-16 as {@code compliance}: a create naming {@code ACTIVE} (agreement,
 * with an invented signer) and {@code COMPLETED} (action item) each answered 201/200 and stored it.
 * The behaviour each bypasses is asserted here as well as its absence, because a slice that closed
 * the bypass by breaking the lifecycle would score identically otherwise.
 */
@ExtendWith(MockitoExtension.class)
class CreateNamesNoDecisionTest {

    private static final Long ORG_ID = 1L;

    @Mock private DataSharingAgreementRepository dsaRepo;
    @Mock private CommitteeActionItemRepository actionRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private DataSharingAgreementService dsa;
    private CommitteeActionItemService actions;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        dsa = new DataSharingAgreementService(dsaRepo);
        actions = new CommitteeActionItemService(actionRepo, audit, publisher);
        lenient().when(dsaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(actionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── data sharing agreement (PDPL third-party disclosure) ─────────────────

    private static DataSharingAgreement agreement() {
        DataSharingAgreement a = new DataSharingAgreement();
        a.setTitleEn("Payroll processor feed");
        a.setRecipientName("Processor Ltd");
        return a;
    }

    @Test
    void anAgreementCannotBeCreatedAlreadyActive() {
        DataSharingAgreement a = agreement();
        a.setStatus("ACTIVE");

        assertThatThrownBy(() -> dsa.create(a))
            .as("ACTIVE is activate()'s, reached through submit(). A create landing there is an "
                + "executed data-sharing agreement that no one submitted or signed off.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ACTIVE")
            .hasMessageContaining("activate");
    }

    @Test
    void aCreateKeepsNoSignatureTheCallerSupplied() {
        DataSharingAgreement a = agreement();
        a.setSignerName("Nobody Who Signed This");
        a.setCounterpartySigner("Nobody Either");

        DataSharingAgreement saved = dsa.create(a);

        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getSignerName())
            .as("a DRAFT naming its signatories asserts an execution that has not happened")
            .isNull();
        assertThat(saved.getCounterpartySigner()).isNull();
        assertThat(saved.getTitleEn())
            .as("the agreement's own content is the caller's — this must stay a working create")
            .isEqualTo("Payroll processor feed");
    }

    @Test
    void theAgreementLifecycleStillRuns() {
        DataSharingAgreement draft = agreement();
        draft.setId(2L);
        draft.setStatus("DRAFT");
        when(dsaRepo.findByIdAndOrganisationId(2L, ORG_ID)).thenReturn(Optional.of(draft));

        assertThat(dsa.submit(2L).getStatus()).isEqualTo("IN_REVIEW");

        DataSharingAgreement live = dsa.activate(2L, "Real Signer", "Real Counterparty");
        assertThat(live.getStatus()).isEqualTo("ACTIVE");
        assertThat(live.getSignedAt())
            .as("activate() stamps the signature date from the clock — that is the whole reason a "
                + "create may not name it")
            .isNotNull();
        assertThat(live.getSignerName()).isEqualTo("Real Signer");
    }

    // ── committee action item (governance follow-up) ─────────────────────────

    private static CommitteeActionItem action() {
        CommitteeActionItem a = new CommitteeActionItem();
        a.setCommitteeId(1L);
        a.setMeetingId(1L);
        a.setActionCode("ACT-1");
        a.setTitleEn("Close the finding");
        a.setTitleAr("إغلاق الملاحظة");
        a.setOwnerName("compliance");
        a.setDueDate(LocalDate.of(2027, 1, 1));
        a.setPriority("HIGH");
        return a;
    }

    @Test
    void anActionItemCannotBeCreatedAlreadyCompleted() {
        CommitteeActionItem a = action();
        a.setStatus("COMPLETED");

        assertThatThrownBy(() -> actions.create(a))
            .as("complete() stamps completed_at, records the notes, writes the ACTION_COMPLETE "
                + "audit row and publishes the transition. A governance action closed on arrival "
                + "looks identical to one that was actually done.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("COMPLETED");
    }

    @Test
    void aCreateKeepsNoCompletionRecordTheCallerSupplied() {
        CommitteeActionItem a = action();
        a.setCompletedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        a.setCompletedBy(99L);
        a.setCompletionNotesEn("did not happen");

        CommitteeActionItem saved = actions.create(a);

        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getCompletedBy()).isNull();
        assertThat(saved.getCompletionNotesEn())
            .as("the gate could not see these three — complete() has no state check, so it is not "
                + "a guarded route by that measure. They are closed because they are the same "
                + "claim, not because a scan demanded it.")
            .isNull();
        assertThat(saved.getOwnerName()).isEqualTo("compliance");
    }

    /**
     * ⚠⚠ F2-219 — the climate register is here because the GATE CANNOT SEE IT, and that is a limit
     * worth naming rather than papering over.
     *
     * <p>{@code ClimateRiskOpportunityService#transition(Long id, String to)} enforces the linear
     * IDENTIFIED → ASSESSED → TREATED → CLOSED machine and then writes {@code cur.setStatus(to)} —
     * from a <b>parameter</b>. A scalar parameter is caller data (F2-218's third detector bug), so
     * {@code status} never enters the STAMPED set, so a create accepting it is not a contradiction
     * by this gate's stated question. The mutation that restores the default therefore SURVIVES the
     * whole build unless something asserts the behaviour directly.
     *
     * <p>🔑 <b>A surviving mutant the gate legitimately cannot see is an unasserted behaviour, not a
     * loose instrument</b> — widening the gate to catch it would mean treating a caller-supplied
     * parameter as a server stamp again, which is the correction that stopped it failing on correct
     * code. Filed as the next shape: every register whose transition route takes its target state
     * as an argument is outside this instrument by construction.
     */
    @Test
    void aClimateRiskCannotBeCreatedIntoAStateTheMachineWouldRefuse() {
        var repo = org.mockito.Mockito.mock(
            com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository.class);
        var svc = new com.tss.grc.module.sust.climate.service.ClimateRiskOpportunityService(
            repo, audit,
            org.mockito.Mockito.mock(com.tss.grc.module.risk.repository.RiskRepository.class),
            org.mockito.Mockito.mock(com.tss.grc.module.risk.service.RiskLinkService.class),
            org.mockito.Mockito.mock(com.tss.grc.module.risk.repository.RiskLinkRepository.class));
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var closed = new com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity();
        closed.setTitleEn("Coastal flooding exposure");
        closed.setKind("RISK");
        // validateAxes() runs BEFORE the status guard, so an incomplete fixture throws for the
        // wrong reason and the assertion passes on a message that has nothing to do with the
        // claim. The first draft did exactly that, and it also made every mutation routed through
        // this class read as KILLED. 🔑 A test that fails on the unmutated tree makes a mutation
        // harness report success it has not earned.
        closed.setCategory("PHYSICAL");
        closed.setLikelihood((short) 3);
        closed.setImpact((short) 4);
        closed.setStatus("CLOSED");

        assertThatThrownBy(() -> svc.create(closed))
            .as("CLOSED is three legal transitions away, each of which stamps its own date")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");

        var fresh = new com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity();
        fresh.setTitleEn("Coastal flooding exposure");
        fresh.setKind("RISK");
        fresh.setCategory("PHYSICAL");
        fresh.setLikelihood((short) 3);
        fresh.setImpact((short) 4);
        fresh.setAssessedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        fresh.setTreatedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        fresh.setClosedAt(LocalDateTime.of(2001, 1, 1, 0, 0));

        var saved = svc.create(fresh);
        assertThat(saved.getStatus()).isEqualTo("IDENTIFIED");
        assertThat(saved.getAssessedAt()).isNull();
        assertThat(saved.getTreatedAt()).isNull();
        assertThat(saved.getClosedAt()).isNull();
        assertThat(saved.getIdentifiedAt())
            .as("identifiedAt IS this route's own stamp and must survive the fix")
            .isNotNull();
    }

    @Test
    void theActionItemLifecycleStillRuns() {
        CommitteeActionItem open = action();
        open.setId(3L);
        open.setStatus("OPEN");
        when(actionRepo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(open));

        assertThat(actions.startProgress(3L).getStatus()).isEqualTo("IN_PROGRESS");

        CommitteeActionItem done = actions.complete(3L, "Evidence filed", 5L);
        assertThat(done.getStatus()).isEqualTo("COMPLETED");
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(done.getCompletionNotesEn()).isEqualTo("Evidence filed");
    }
}
