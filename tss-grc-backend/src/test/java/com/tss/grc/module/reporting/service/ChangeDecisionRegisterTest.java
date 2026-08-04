package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionVoteRepository;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.service.ChangeManagementService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * S8d / CE-14b — the decision register must not invent a decision, and must not hide one.
 *
 * <p>The measurement this slice started from: <b>11</b> changes in dev have passed a decision point
 * and <b>1</b> names its author. So the two failure modes are equally easy and equally wrong —
 * printing one row (hiding ten real governance events) or printing eleven attributed rows (inventing
 * ten decision authors). Every test here pins one half of that.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeDecisionRegisterTest {

    private static final Long ORG = 1L;

    @Mock ChangeRequestRepository changeRepo;
    @Mock ChangeApprovalRepository approvalRepo;
    @Mock CommitteeReferralRepository referralRepo;
    @Mock CommitteeResolutionRepository resolutionRepo;
    @Mock CommitteeResolutionVoteRepository voteRepo;
    @InjectMocks ChangeDecisionRegisterService svc;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(anyLong(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(anyLong())).thenReturn(List.of());
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static ChangeRequest ch(long id, String code, String status) {
        ChangeRequest c = new ChangeRequest();
        c.setId(id);
        c.setChangeCode(code);
        c.setTitleEn("Change " + code);
        c.setTitleAr("تغيير " + code);
        c.setChangeType("NORMAL");
        c.setRiskLevel("MEDIUM");
        c.setStatus(status);
        return c;
    }

    private static ChangeApproval vote(String decision, String who) {
        ChangeApproval a = new ChangeApproval();
        a.setDecision(decision);
        a.setApproverName(who);
        return a;
    }

    private void given(ChangeRequest... changes) {
        when(changeRepo.findByOrganisationIdOrderByCreatedAtDesc(any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(changes)));
    }

    // ── what belongs in the register ────────────────────────────────────────

    @Test
    @DisplayName("a change still before the committee is not a decision")
    void pendingIsNotADecision() {
        given(ch(1, "CHG-1", "CAB_PENDING"), ch(2, "CHG-2", "DRAFT"),
              ch(3, "CHG-3", "SUBMITTED"), ch(4, "CHG-4", "IMPACT_ASSESSED"),
              ch(5, "CHG-5", "CANCELLED"));
        assertThat(svc.build().rows())
            .as("a register that lists undecided changes tells a reader the CAB has ruled on them")
            .isEmpty();
    }

    @Test
    @DisplayName("a deferral and a request for more information ARE decisions")
    void deferAndMoreInfoAreDecisions() {
        given(ch(1, "CHG-1", "CAB_DEFERRED"), ch(2, "CHG-2", "MORE_INFO_REQUIRED"));
        List<ChangeDecisionRegisterService.DecisionRow> rows = svc.build().rows();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(ChangeDecisionRegisterService.DecisionRow::decision)
            .as("S7c exists because a committee that defers had nowhere to say so; dropping those "
                + "rows here would re-open the same hole from the reporting end")
            .containsExactlyInAnyOrder("DEFERRED", "MORE_INFO_REQUIRED");
    }

    @Test
    @DisplayName("every decided state the machine has is either mapped or deliberately excluded")
    void decidedStatesTrackTheStateMachine() throws Exception {
        Field f = ChangeManagementService.class.getDeclaredField("NORMAL_TRANSITIONS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> transitions = (Map<String, Set<String>>) f.get(null);
        Set<String> all = new java.util.TreeSet<>(transitions.keySet());
        transitions.values().forEach(all::addAll);

        // The states a decision register must NOT treat as decided, stated once so a new state
        // cannot join the machine and be silently omitted from the register.
        Set<String> notYetDecided = Set.of("DRAFT", "SUBMITTED", "IMPACT_ASSESSED", "CAB_PENDING",
                                          "CANCELLED");
        Set<String> unclassified = new java.util.TreeSet<>(all);
        unclassified.removeAll(ChangeDecisionRegisterService.DECIDED_STATES);
        unclassified.removeAll(notYetDecided);
        assertThat(unclassified)
            .as("a new state exists in the machine and this register does not know whether it is a "
                + "decision. Classify it — do not let it fall through as undecided.")
            .isEmpty();
    }

    // ── the two axes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("an unstamped decision is INFERRED and names nobody")
    void unstampedIsInferred() {
        given(ch(1, "CHG-1", "IMPLEMENTING"));
        ChangeDecisionRegisterService.DecisionRow r = svc.build().rows().get(0);
        assertThat(r.basis()).isEqualTo(ChangeDecisionRegisterService.BASIS_INFERRED);
        assertThat(r.decision()).as("the state proves an approval happened").isEqualTo("APPROVED");
        assertThat(r.decidedAt()).isNull();
        assertThat(r.decidedBy()).isNull();
        assertThat(r.isAttributed())
            .as("nothing recorded when or by whom, so the register may not attribute it")
            .isFalse();
    }

    @Test
    @DisplayName("a stamped decision is RECORDED and carries its author")
    void stampedIsRecorded() {
        ChangeRequest c = ch(1, "CHG-1", "CAB_APPROVED");
        c.setCabDecisionAt(LocalDateTime.of(2026, 7, 25, 18, 22));
        c.setCabDecisionByName("so_officer");
        given(c);
        ChangeDecisionRegisterService.DecisionRow r = svc.build().rows().get(0);
        assertThat(r.basis()).isEqualTo(ChangeDecisionRegisterService.BASIS_RECORDED);
        assertThat(r.decidedBy()).isEqualTo("so_officer");
        assertThat(r.isAttributed()).isTrue();
    }

    @Test
    @DisplayName("basis and evidence are independent — an inferred decision can still have votes")
    void axesAreIndependent() {
        given(ch(1, "CHG-1", "CLOSED"));
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(1L))
            .thenReturn(List.of(vote("APPROVE", "a"), vote("APPROVE", "b"), vote("DEFER", "c")));
        ChangeDecisionRegisterService.DecisionRow r = svc.build().rows().get(0);
        assertThat(r.basis()).isEqualTo(ChangeDecisionRegisterService.BASIS_INFERRED);
        assertThat(r.evidence()).isEqualTo(ChangeDecisionRegisterService.EVIDENCE_VOTES);
        assertThat(r.voteSummary())
            .as("the tally, never an interpretation of it")
            .isEqualTo("APPROVE 2, DEFER 1");
    }

    @Test
    @DisplayName("a carried resolution outranks a legacy tally and brings its provenance")
    void resolutionOutranksVotes() {
        given(ch(1, "CHG-1", "CAB_APPROVED"));
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(1L))
            .thenReturn(List.of(vote("APPROVE", "a")));

        CommitteeReferral ref = new CommitteeReferral();
        ref.setResolutionId(77L);
        when(referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(ORG, "CHANGE", 1L))
            .thenReturn(Optional.of(ref));

        CommitteeResolution res = new CommitteeResolution();
        res.setId(77L);
        res.setResolutionCode("RES-2026-014");
        res.setOutcome("CARRIED");
        res.setTallySource("ASSERTED_LEGACY");
        res.setVoteFor(3);
        res.setVoteAgainst(0);
        res.setVoteAbstain(1);
        when(resolutionRepo.findByIdAndOrganisationId(77L, ORG)).thenReturn(Optional.of(res));

        CommitteeResolutionVote recused = new CommitteeResolutionVote();
        recused.setVote("RECUSED");
        recused.setMemberName("Fahad Al-Otaibi");
        CommitteeResolutionVote forVote = new CommitteeResolutionVote();
        forVote.setVote("FOR");
        forVote.setMemberName("Noura Al-Qahtani");
        when(voteRepo.findByResolutionId(77L)).thenReturn(List.of(recused, forVote));

        ChangeDecisionRegisterService.DecisionRow r = svc.build().rows().get(0);
        assertThat(r.evidence())
            .as("the resolution is what the committee passed; the tally is what somebody typed "
                + "against the change")
            .isEqualTo(ChangeDecisionRegisterService.EVIDENCE_RESOLUTION);
        assertThat(r.resolutionCode()).isEqualTo("RES-2026-014");
        assertThat(r.tallySource())
            .as("S7f: a tally with no stated source is a figure somebody typed, and the register must "
                + "say which")
            .isEqualTo("ASSERTED_LEGACY");
        assertThat(r.recusals()).containsExactly("Fahad Al-Otaibi");
        assertThat(r.voteSummary()).as("the legacy tally is still carried, just not as the evidence")
            .isEqualTo("APPROVE 1");
    }

    @Test
    @DisplayName("no resolution and no votes is NONE, not an empty string")
    void nothingIsNone() {
        given(ch(1, "CHG-1", "SCHEDULED"));
        ChangeDecisionRegisterService.DecisionRow r = svc.build().rows().get(0);
        assertThat(r.evidence())
            .as("correct for an AUTO-gated change — no committee was ever asked — and a reader must "
                + "be able to see that rather than infer it from blank cells")
            .isEqualTo(ChangeDecisionRegisterService.EVIDENCE_NONE);
        assertThat(r.voteSummary()).isEmpty();
    }

    // ── the header counts ───────────────────────────────────────────────────

    @Test
    @DisplayName("the register publishes how many of its rows are attributed")
    void publishesAttributionCount() {
        ChangeRequest stamped = ch(1, "CHG-1", "CAB_APPROVED");
        stamped.setCabDecisionAt(LocalDateTime.of(2026, 7, 25, 18, 22));
        given(stamped, ch(2, "CHG-2", "IMPLEMENTING"), ch(3, "CHG-3", "CLOSED"));

        ChangeDecisionRegisterService.Register reg = svc.build();
        assertThat(reg.total()).isEqualTo(3);
        assertThat(reg.attributed())
            .as("F2-107's shape in table form: '3 decisions' with no statement of how many are "
                + "attributed lets a reader assume three recorded acts")
            .isEqualTo(1);
        assertThat(reg.byBasis()).containsEntry(ChangeDecisionRegisterService.BASIS_INFERRED, 2L);
        assertThat(reg.byDecision()).containsEntry("APPROVED", 3L);
    }

    @Test
    @DisplayName("attributed rows sort first — a register opening on blank rows looks broken")
    void attributedRowsSortFirst() {
        ChangeRequest older = ch(1, "CHG-1", "CAB_APPROVED");
        older.setCabDecisionAt(LocalDateTime.of(2026, 5, 1, 9, 0));
        ChangeRequest newer = ch(2, "CHG-2", "CAB_APPROVED");
        newer.setCabDecisionAt(LocalDateTime.of(2026, 7, 25, 18, 22));
        given(ch(3, "CHG-3", "IMPLEMENTING"), older, newer);

        assertThat(svc.build().rows())
            .extracting(ChangeDecisionRegisterService.DecisionRow::changeCode)
            .containsExactly("CHG-2", "CHG-1", "CHG-3");
    }

    @Test
    @DisplayName("an empty register is empty, not a zero-row table of invented totals")
    void emptyIsEmpty() {
        given();
        ChangeDecisionRegisterService.Register reg = svc.build();
        assertThat(reg.total()).isZero();
        assertThat(reg.attributed()).isZero();
        assertThat(reg.byDecision()).isEmpty();
    }

    @Test
    @DisplayName("the vocabularies are closed")
    void vocabulariesAreClosed() {
        assertThat(Set.of(ChangeDecisionRegisterService.BASIS_RECORDED,
                          ChangeDecisionRegisterService.BASIS_INFERRED)).hasSize(2);
        assertThat(Set.of(ChangeDecisionRegisterService.EVIDENCE_RESOLUTION,
                          ChangeDecisionRegisterService.EVIDENCE_VOTES,
                          ChangeDecisionRegisterService.EVIDENCE_NONE)).hasSize(3);
        assertThat(ChangeDecisionRegisterService.DECIDED_STATES)
            .doesNotContain("CAB_PENDING", "DRAFT", "CANCELLED");
    }
}
