package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.exception.ChangeAuthorityNotConfiguredException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeAuthorisationMatrixRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeCommentRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>ADR-017 D-2 (A17-D2)</b> — a CAB-dependent approval must be refused when the tenant has not named
 * the committee its own authorisation matrix demands.
 *
 * <h3>The state this locks down</h3>
 * <p>Measured on the running stack, 2026-08-01, before the fix: in organisation 2 — whose matrix
 * demands CAB on 5 cells and ECAB on 6, and which has zero committees and no gate-map row — a
 * {@code NORMAL/LOW} change reached {@code CAB_APPROVED} with <b>200</b> and an {@code EMERGENCY/LOW}
 * change reached {@code EMERGENCY_APPROVED} with <b>200 and zero recorded votes</b>. Neither involved
 * a committee, because the committee branch engaged only when a referral already existed and a referral
 * could not be created for an unmapped gate. {@code GET /api/v1/changes/governance-health} answered
 * {@code healthy=false, unmapped=[CAB, ECAB]} for the same tenant at the same moment, and the boot log
 * had been WARNing "will fall back to the legacy approval tally" since V314.
 *
 * <h3>Why a unit test and not only a probe</h3>
 * <p>The rule cannot live in a {@code @PreAuthorize} — it depends on the change's (type, risk) cell and
 * on tenant configuration — so, exactly as with {@code ChangeDecisionAuthorityTest}, no annotation scan
 * can see it and a test is the only artefact that fails when someone removes it.
 *
 * <p>Four of these tests assert what the gate must <b>not</b> do. That is deliberate: a fail-closed
 * check is easy to write too widely, and every one of those four cases would look like a bug to a
 * customer rather than a control.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeAuthorityFailClosedTest {

    private static final Long ORG_ID    = 2L;
    private static final Long CHANGE_ID = 900L;

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private ChangeApprovalRepository approvalRepo;
    @Mock private ChangeTaskRepository taskRepo;
    @Mock private ChangeLinkRepository linkRepo;
    @Mock private ChangeCommentRepository commentRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ChangeAuthorisationMatrixRepository authMatrixRepo;
    @Mock private ChangeGovernanceHealthService governanceHealth;
    @Mock private CommitteePort committeePort;
    @InjectMocks private ChangeManagementService service;

    @BeforeEach
    void setUp() {
        // ⚠ ChangeManagementService is @RequiredArgsConstructor, so @InjectMocks uses CONSTRUCTOR
        // injection and leaves every @Autowired(required = false) FIELD null — including the two this
        // rule depends on. Left implicit, all three fail-closed assertions pass vacuously: the check
        // returns early on a null governanceHealth and nothing throws. Wire them by hand.
        ReflectionTestUtils.setField(service, "governanceHealth", governanceHealth);
        ReflectionTestUtils.setField(service, "authMatrixRepo", authMatrixRepo);
        // S5/CE-1: the field is now the port, not the peer service. Renaming a
        // reflection-injected field silently re-vacuums exactly the assertions this
        // comment warns about, so the rename must move with it.
        ReflectionTestUtils.setField(service, "committeePort", committeePort);
        TenantContext.setCurrentTenant(ORG_ID);
        // CHANGE_APPROVE so F2-29's authority check passes and this test measures only D-2's gate.
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("probe", "n/a",
                List.of(new SimpleGrantedAuthority("CHANGE_READ"),
                        new SimpleGrantedAuthority("CHANGE_WRITE"),
                        new SimpleGrantedAuthority("CHANGE_APPROVE"))));
        when(committeePort.activeReferral(anyString(), anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); SecurityContextHolder.clearContext(); }

    // ─── fixtures ────────────────────────────────────────────────────────────────

    private void change(String status, String type, String risk) {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG_ID);
        c.setStatus(status);
        c.setChangeType(type);
        c.setRiskLevel(risk);
        c.setChangeCode("RT-D2");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        // one APPROVE on record, so the legacy tally is satisfied and cannot be the reason for a refusal
        ChangeApproval vote = new ChangeApproval();
        vote.setDecision("APPROVE");
        vote.setApproverName("Bob CAB");
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of(vote));
    }

    private void matrixDemands(String gate, String type, String risk) {
        ChangeAuthorisationMatrix m = new ChangeAuthorisationMatrix();
        m.setOrganisationId(ORG_ID);
        m.setChangeType(type);
        m.setRiskLevel(risk);
        m.setGate(gate);
        m.setMinApprovals(1);
        when(authMatrixRepo.findByOrganisationIdAndChangeTypeAndRiskLevel(ORG_ID, type, risk))
            .thenReturn(Optional.of(m));
    }

    private void gateFault(String gate, String fault) {
        when(governanceHealth.gateConfigurationFault(ORG_ID, gate)).thenReturn(fault);
    }

    // ─── the defect, closed ──────────────────────────────────────────────────────

    @Test
    void cabApproval_isRefused_whenNoCommitteeIsMappedToTheGate() {
        change("CAB_PENDING", "NORMAL", "LOW");
        matrixDemands("CAB", "NORMAL", "LOW");
        gateFault("CAB", ChangeAuthorityNotConfiguredException.NOT_MAPPED);

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>()))
            .as("a change whose matrix demands CAB must not be approved by the legacy tally when "
                + "the tenant has no CAB — that was the measured fail-open")
            .isInstanceOf(ChangeAuthorityNotConfiguredException.class)
            .hasMessageContaining("requires CAB authorisation")
            // D-2: the refusal must name the screen, or an administrator cannot act on it
            .hasMessageContaining(ChangeAuthorityNotConfiguredException.REMEDY_SCREEN)
            .extracting(e -> ((ChangeAuthorityNotConfiguredException) e).getReason())
            .isEqualTo(ChangeAuthorityNotConfiguredException.NOT_MAPPED);
    }

    @Test
    void emergencyApproval_isRefused_whenNoCommitteeIsMappedToTheEcabGate() {
        change("DRAFT", "EMERGENCY", "LOW");
        matrixDemands("ECAB", "EMERGENCY", "LOW");
        gateFault("ECAB", ChangeAuthorityNotConfiguredException.NOT_MAPPED);

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "EMERGENCY_APPROVED", new HashMap<>()))
            .as("EMERGENCY_APPROVED consulted neither a committee nor a vote — it was the weakest "
                + "of the three decision targets, not the strongest")
            .isInstanceOf(ChangeAuthorityNotConfiguredException.class)
            .extracting(e -> ((ChangeAuthorityNotConfiguredException) e).getGate())
            .isEqualTo("ECAB");
    }

    @Test
    void approval_isRefused_whenTheMappedCommitteeIsDissolved() {
        change("CAB_PENDING", "NORMAL", "MEDIUM");
        matrixDemands("CAB", "NORMAL", "MEDIUM");
        gateFault("CAB", ChangeAuthorityNotConfiguredException.COMMITTEE_UNAVAILABLE);

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>()))
            .as("a row pointing at a dissolved committee is not a configured gate — the health "
                + "report used to call this healthy while refer() rejected it (F2-62's shape)")
            .isInstanceOf(ChangeAuthorityNotConfiguredException.class)
            .hasMessageContaining("dissolved or no longer exists")
            .extracting(e -> ((ChangeAuthorityNotConfiguredException) e).getReason())
            .isEqualTo(ChangeAuthorityNotConfiguredException.COMMITTEE_UNAVAILABLE);
    }

    // ─── what the gate must NOT do ───────────────────────────────────────────────

    @Test
    void configuredGate_approvesExactlyAsBefore() {
        change("CAB_PENDING", "NORMAL", "LOW");
        matrixDemands("CAB", "NORMAL", "LOW");
        gateFault("CAB", null);

        ChangeRequest out = service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>());
        assertThat(out.getStatus())
            .as("org 1 holds a CAB row and must be untouched by this change — probed live: 200 before "
                + "and 200 after")
            .isEqualTo("CAB_APPROVED");
    }

    @Test
    void rejection_isNeverFailedClosed_evenWithNoCommitteeAtAll() {
        change("CAB_PENDING", "NORMAL", "LOW");
        matrixDemands("CAB", "NORMAL", "LOW");
        gateFault("CAB", ChangeAuthorityNotConfiguredException.NOT_MAPPED);

        ChangeRequest out = service.transition(CHANGE_ID, "CAB_REJECTED", new HashMap<>());
        assertThat(out.getStatus())
            .as("refusing a REJECTION because no committee exists would keep alive a change somebody "
                + "tried to kill — fail-closed means refusing the permissive transition, not every "
                + "transition that mentions the CAB")
            .isEqualTo("CAB_REJECTED");
    }

    @Test
    void autoAuthorisedCell_needsNoCommittee() {
        change("CAB_PENDING", "STANDARD", "LOW");
        matrixDemands("AUTO", "STANDARD", "LOW");
        gateFault("CAB", ChangeAuthorityNotConfiguredException.NOT_MAPPED);

        assertThatCode(() -> service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>()))
            .as("an AUTO cell demands no committee, so an unmapped CAB is irrelevant to it")
            .doesNotThrowAnyException();
    }

    @Test
    void silentMatrix_needsNoCommittee() {
        change("CAB_PENDING", "NORMAL", "LOW");
        // no matrix cell at all for this (type, risk)
        when(authMatrixRepo.findByOrganisationIdAndChangeTypeAndRiskLevel(ORG_ID, "NORMAL", "LOW"))
            .thenReturn(Optional.empty());
        gateFault("CAB", ChangeAuthorityNotConfiguredException.NOT_MAPPED);

        assertThatCode(() -> service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>()))
            .as("the matrix IS the tenant's documented change authority — the absence of a cell is "
                + "not a demand for a committee, and failing closed there would refuse approvals in "
                + "tenants that never asked for a CAB")
            .doesNotThrowAnyException();
    }

    @Test
    void referredChange_isDecidedByItsOwnCommittee_notTheTenantDefault() {
        change("CAB_PENDING", "NORMAL", "LOW");
        matrixDemands("CAB", "NORMAL", "LOW");
        gateFault("CAB", ChangeAuthorityNotConfiguredException.NOT_MAPPED);
        CommitteePort.ReferralView ref = new CommitteePort.ReferralView(
            5L, ORG_ID, "CHANGE", CHANGE_ID, 77L, null, null, "CAB", "APPROVAL",
            "PENDING", "secretary", null, null);
        when(committeePort.activeReferral("CHANGE", CHANGE_ID)).thenReturn(Optional.of(ref));

        service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>());
        // The referral path asserts the committee's own decision instead; D-2's "unset" condition
        // does not hold for a change whose deciding committee is named explicitly.
        assertThat(ref.committeeId()).isEqualTo(77L);
        // And it must be the PORT that was asked — a green assertion on a local record would
        // hold even if the referral branch were never taken.
        verify(committeePort).assertDecisionApproves("CHANGE", CHANGE_ID, 1);
    }

    // ─── anti-drift ──────────────────────────────────────────────────────────────

    /**
     * Every CAB decision target must be <b>consciously</b> classified as fail-closed or not. A new
     * decision target added to {@code DECISION_TARGETS} without a decision here fails the build
     * rather than silently defaulting to "permitted with no configured authority" — which is the
     * shape the original defect had.
     */
    @Test
    @SuppressWarnings("unchecked")
    void everyDecisionTargetIsClassifiedAsFailClosedOrNot() throws Exception {
        Field f = ChangeManagementService.class.getDeclaredField("DECISION_TARGETS");
        f.setAccessible(true);
        Set<String> targets = (Set<String>) f.get(null);

        Set<String> failClosed = Set.of("CAB_APPROVED", "EMERGENCY_APPROVED");
        Set<String> deliberatelyOpen = Set.of("CAB_REJECTED");

        assertThat(targets)
            .as("D-2 classification must cover every CAB decision target")
            .isNotEmpty()
            .allSatisfy(t -> assertThat(failClosed.contains(t) || deliberatelyOpen.contains(t))
                .as("target '%s' is a CAB decision with no A17-D2 classification — decide whether an "
                    + "unconfigured authority must refuse it, and add it to failClosed or "
                    + "deliberatelyOpen here", t)
                .isTrue());
        assertThat(failClosed).allSatisfy(t -> assertThat(targets).contains(t));
    }

    /**
     * <b>The report and the enforcement must share one predicate, textually.</b> F2-62 was an access
     * report whose predicate had drifted from the enforcement point it described, and each half looked
     * defensible alone. Here the gap report ({@code GAP_SQL}) and the single-gate check
     * ({@code CONFIGURED_ONE_SQL}) are both built by interpolating {@code CONFIGURED_PREDICATE}, so
     * this asserts the shared text actually reaches both — two queries that merely agree today are not
     * one predicate.
     */
    @Test
    void theReportAndTheEnforcementShareOnePredicate() throws Exception {
        String predicate = sqlConstant("CONFIGURED_PREDICATE");
        String gapSql    = sqlConstant("GAP_SQL");
        String oneSql    = sqlConstant("CONFIGURED_ONE_SQL");

        // The shared skeleton, minus the two correlated placeholders that differ by design.
        List<String> clauses = Arrays.stream(predicate.split("\n"))
            .map(String::trim).filter(s -> !s.isEmpty())
            .filter(s -> !s.contains("%s"))
            .toList();

        assertThat(clauses).as("the predicate should have real clauses to share").hasSizeGreaterThan(3);
        for (String clause : clauses) {
            assertThat(gapSql).as("GAP_SQL must carry the shared clause: %s", clause).contains(clause);
            assertThat(oneSql).as("CONFIGURED_ONE_SQL must carry the shared clause: %s", clause).contains(clause);
        }
        assertThat(predicate)
            .as("the shared predicate must include the committee's own state — a gate-map row pointing "
                + "at a DISSOLVED committee is what made the report and refer() disagree")
            .contains("c.status <> 'DISSOLVED'")
            .contains("c.is_deleted = 0");
    }

    private static String sqlConstant(String name) throws Exception {
        Field f = ChangeGovernanceHealthService.class.getDeclaredField(name);
        f.setAccessible(true);
        return (String) f.get(null);
    }
}
