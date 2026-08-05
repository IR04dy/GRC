package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeAuthorisationMatrixRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeCommentRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-113 — the <b>producing</b> side of Track D's force-recusal: what {@code referToCommittee} hands
 * the committee engine, and what {@code /sod-coverage} says about how much of it can bite.
 *
 * <h3>The defect</h3>
 * A change referred to its CAB carries a list of parties barred from casting the deciding vote.
 * Until V358 that list was <b>names only</b>, and the engine compared them against
 * {@code committee_member_terms.member_name}. A change's people are team labels — measured in dev,
 * 13 requester labels and 15 owner labels, of which <b>0 resolved to any user</b>: 'AppSec',
 * 'CIO Office', 'SRE', 'IAM Team', 'CloudOps'. A committee's members are people: 'Khalid
 * Al-Rashidi', 'Noura Al-Zahrani'. The comparison could not match, so the bar had never engaged for
 * any change in either database.
 *
 * <p>And it was worse than a mismatch. All <b>4</b> live referrals carried a NULL recusal list,
 * because the two changes that had been referred recorded neither a requester name nor an owner name
 * and the CSV builder turned two blanks into nothing. The gate was not weakly enforced; it was
 * unarmed, and the audit line said only "Referred to committee 13" either way.
 *
 * <h3>Why these tests are on the producer and not only on the engine</h3>
 * {@code CommitteeVoteRecusalTallyTest} pins the refusal. It cannot see this defect, because a unit
 * test supplies the recusal list it wants and the production path supplied a different one — which is
 * exactly how a control with green tests reached three releases without ever being able to fire
 * (F2-112's keeper: a green test proves a rule is right; only a measurement proves it can run).
 * These tests assert what the <em>real</em> call site produces.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeCommitteeRecusalTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE_ID = 261L;

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private ChangeApprovalRepository approvalRepo;
    @Mock private ChangeTaskRepository taskRepo;
    @Mock private ChangeLinkRepository linkRepo;
    @Mock private ChangeCommentRepository commentRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ChangeAuthorisationMatrixRepository authMatrixRepo;
    @Mock private CommitteePort committeePort;
    @InjectMocks private ChangeManagementService service;

    @BeforeEach
    void setUp() {
        // ⚠ @RequiredArgsConstructor means @InjectMocks uses CONSTRUCTOR injection and leaves every
        // @Autowired(required = false) FIELD null. Left implicit, referToCommittee throws
        // "Committee referral is not available" and every assertion below would be about the wrong
        // thing — the trap ChangeAuthorityFailClosedTest documents at length.
        ReflectionTestUtils.setField(service, "committeePort", committeePort);
        ReflectionTestUtils.setField(service, "authMatrixRepo", authMatrixRepo);
        TenantContext.setCurrentTenant(ORG);
        when(committeePort.refer(anyString(), anyLong(), anyString(), anyString(), any(), any(),
                                 any(), any()))
            .thenAnswer(i -> view(i.getArgument(4)));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────

    /** A change as the platform actually stores one: ids where CE-15 captured them, labels as typed. */
    private ChangeRequest change(Long requesterId, String requesterName,
                                 Long ownerId, String ownerName) {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-2026-0261");
        c.setTitleEn("Rotate the gateway certificates");
        c.setTitleAr("تدوير شهادات البوابة");
        c.setChangeType("NORMAL");
        c.setRiskLevel("HIGH");
        c.setStatus("CAB_PENDING");
        c.setRequestedById(requesterId);
        c.setRequestedByName(requesterName);
        c.setChangeOwnerId(ownerId);
        c.setChangeOwnerName(ownerName);
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));
        ChangeAuthorisationMatrix m = new ChangeAuthorisationMatrix();
        m.setOrganisationId(ORG);
        m.setChangeType("NORMAL");
        m.setRiskLevel("HIGH");
        m.setGate("CAB");
        m.setMinApprovals(2);
        when(authMatrixRepo.findByOrganisationIdAndChangeTypeAndRiskLevel(ORG, "NORMAL", "HIGH"))
            .thenReturn(Optional.of(m));
        return c;
    }

    private static CommitteePort.ReferralView view(Object committeeId) {
        return new CommitteePort.ReferralView(
            9L, ORG, "CHANGE", CHANGE_ID, (Long) committeeId, null, null, "CAB", "APPROVAL",
            "PENDING", "admin", null, null, "CHG-2026-0261", "Rotate the gateway certificates",
            null, null);
    }

    private CommitteePort.Recusal referAndCaptureRecusal() {
        service.referToCommittee(CHANGE_ID, 13L, "admin");
        ArgumentCaptor<CommitteePort.Recusal> captor =
            ArgumentCaptor.forClass(CommitteePort.Recusal.class);
        verify(committeePort).refer(eq("CHANGE"), eq(CHANGE_ID), eq("CAB"), eq("APPROVAL"), eq(13L),
                                   eq("admin"), captor.capture(), any());
        return captor.getValue();
    }

    private String auditDetail() {
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("REFER_TO_COMMITTEE"), eq("ChangeRequest"), eq(CHANGE_ID),
                          detail.capture());
        return detail.getValue();
    }

    // ── what the referral now carries ───────────────────────────────────────────────────────────

    /**
     * ⚠ THE FIX, AT ITS SOURCE. Delete the id half from {@code referToCommittee} and this fails —
     * and the gate downstream goes back to comparing 'CloudOps' with 'Khalid Al-Rashidi'.
     */
    @Test
    @DisplayName("F2-113 — the referral carries the requester's and owner's user ids, not just labels")
    void theRecusalListCarriesCapturedIdentity() {
        change(8L, "CloudOps", 3L, "IAM Team");

        CommitteePort.Recusal recusal = referAndCaptureRecusal();

        assertThat(recusal.userIds())
            .as("the only half a committee seat can be matched against: member_user_id")
            .containsExactly(8L, 3L);
        assertThat(recusal.names())
            .as("⚠ and the labels must still travel — 29 of dev's 42 active seats carry no user id, "
              + "so for those the label is the only comparable thing there is. Adding the ids must "
              + "not retire the comparison the unlinked seats depend on (the CE-15 lesson)")
            .containsExactly("CloudOps", "IAM Team");
    }

    /**
     * Dev's real shape for the only two changes that carry a requester id (261 and 262, both raised
     * after CE-15): the id is set and the label is NULL. Before this slice the referral was built from
     * the labels alone, so both produced an empty CSV — which is why 4 of 4 live referrals bar nobody.
     */
    @Test
    @DisplayName("F2-113 — an id with no label still arms the gate; before, it produced an empty CSV")
    void anIdWithNoLabelIsStillARecusal() {
        change(8L, null, null, null);

        CommitteePort.Recusal recusal = referAndCaptureRecusal();

        assertThat(recusal.userIds()).containsExactly(8L);
        assertThat(recusal.names()).isEmpty();
        assertThat(recusal.isPresent())
            .as("this exact change referred to a CAB barred NOBODY before V358")
            .isTrue();
    }

    @Test
    @DisplayName("a label with no captured id is still a recusal — the pre-CE-15 population")
    void aLabelWithNoIdIsStillARecusal() {
        change(null, "AppSec", null, "CISO");

        CommitteePort.Recusal recusal = referAndCaptureRecusal();

        assertThat(recusal.userIds()).isEmpty();
        assertThat(recusal.names()).containsExactly("AppSec", "CISO");
    }

    @Test
    @DisplayName("the same person as requester and owner is barred once, not twice")
    void duplicatePartiesAreCollapsed() {
        change(8L, "CloudOps", 8L, "CloudOps");

        CommitteePort.Recusal recusal = referAndCaptureRecusal();

        assertThat(recusal.userIds()).containsExactly(8L);
        assertThat(recusal.names()).containsExactly("CloudOps");
    }

    /**
     * ⚠ The sentence that let F2-113 sit undetected. "Referred to committee 13" reads identically
     * whether the SoD bar is armed or unarmed, so the ledger for all 4 live referrals asserts a
     * referral happened and says nothing about the control that did not.
     */
    @Test
    @DisplayName("F2-113 — the audit line says whether the bar is armed, and names the gap when it is not")
    void theAuditLineReportsWhetherAnybodyCanBeBarred() {
        change(null, null, null, null);

        service.referToCommittee(CHANGE_ID, 13L, "admin");

        assertThat(auditDetail())
            .as("a change recording neither a requester nor an owner cannot bar anyone from its own "
              + "deciding vote, and the ledger has to say so rather than imply a control ran")
            .contains("NO party recused")
            .contains("Referred to committee 13");
    }

    @Test
    @DisplayName("and when it IS armed the line counts both halves")
    void theAuditLineCountsWhatWasBarred() {
        change(8L, "CloudOps", 3L, "IAM Team");

        service.referToCommittee(CHANGE_ID, 13L, "admin");

        assertThat(auditDetail())
            .contains("recusing 2 user id(s) and 2 label(s)")
            .doesNotContain("NO party recused");
    }

    // ── the coverage row ────────────────────────────────────────────────────────────────────────

    /**
     * The fourth row of {@code GET /api/v1/changes/sod-coverage}, and the only one this module does
     * not compute. Its reach depends on the committee's roster as much as on the change's captured
     * people, and only the engine sees both — a count taken here would have read as full coverage
     * once CE-15's changes accumulated while 29 of 42 seats stayed uncomparable, which is F2-112's
     * shape reproduced inside the instrument built to detect it.
     */
    @Test
    @DisplayName("F2-113 — /sod-coverage publishes the committee gate, sourced from the engine")
    void theCommitteeGateAppearsInTheCoverageReport() {
        when(changeRepo.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any()))
            .thenReturn(new PageImpl<>(List.of()));
        ChangeSoD.Coverage fromEngine = new ChangeSoD.Coverage(
            ChangeSoD.GATE_COMMITTEE_VOTE_NOT_REQUESTER, 4, 0, 0, 4);
        when(committeePort.recusalCoverage("CHANGE")).thenReturn(fromEngine);

        List<ChangeSoD.Coverage> rows = service.sodCoverage();

        assertThat(rows).extracting(ChangeSoD.Coverage::gate)
            .as("every gate in ChangeSoD.GATES must have a row, or a control's reach is unpublished")
            .containsExactlyElementsOf(ChangeSoD.GATES);
        assertThat(rows).contains(fromEngine);
        verify(committeePort).recusalCoverage("CHANGE");

        ChangeSoD.Coverage committee = rows.stream()
            .filter(r -> ChangeSoD.GATE_COMMITTEE_VOTE_NOT_REQUESTER.equals(r.gate()))
            .findFirst().orElseThrow();
        assertThat(committee.enabledButBlind())
            .as("dev on 2026-08-05: 4 live referrals, none of which bars anybody — a gate switched "
              + "on that has never been able to refuse a single vote")
            .isTrue();
        assertThat(committee.coveragePct()).isZero();
    }

    /**
     * No engine is an UNKNOWN reach, and an unknown reach must not read as a covered one (S6b-i).
     * Dropping the row entirely would be worse still: a gate absent from the report is
     * indistinguishable from a gate that does not exist.
     */
    @Test
    @DisplayName("with no committee engine the row is still published, with a zero population")
    void theRowSurvivesTheEnginesAbsence() {
        when(changeRepo.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any()))
            .thenReturn(new PageImpl<>(List.of()));
        ReflectionTestUtils.setField(service, "committeePort", null);

        List<ChangeSoD.Coverage> rows = service.sodCoverage();

        assertThat(rows).extracting(ChangeSoD.Coverage::gate)
            .containsExactlyElementsOf(ChangeSoD.GATES);
        ChangeSoD.Coverage committee = rows.stream()
            .filter(r -> ChangeSoD.GATE_COMMITTEE_VOTE_NOT_REQUESTER.equals(r.gate()))
            .findFirst().orElseThrow();
        assertThat(committee.population()).isZero();
        assertThat(committee.enabledButBlind())
            .as("not blind — not present. The two are different facts and the report must not merge them")
            .isFalse();
    }
}
