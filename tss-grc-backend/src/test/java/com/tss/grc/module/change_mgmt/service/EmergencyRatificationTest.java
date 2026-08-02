package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeRatificationPolicy;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.model.RatificationStates;
import com.tss.grc.module.change_mgmt.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A17-D3 — the ECAB retrospective-ratification SLA.
 *
 * <p>ADR-017 D-3 decided five business days from implementation, escalating to the CISO
 * on breach. What this class pins is mostly the <em>edges</em>, because the happy path is
 * one field assignment and the edges are where the decision was unbuildable as written:
 *
 * <ul>
 *   <li>the clock arms at <b>implementation</b>, not at approval — so ECAB-approved but
 *       not yet live is a third answer, neither compliant nor breached;</li>
 *   <li>the ratifier cannot be the emergency approver — this is the entire point of a
 *       retrospective control and it is F2-29 one lifecycle step later;</li>
 *   <li>the SLA override is bounded, and the bound is enforced in the service too,
 *       because server-side callers never see the DTO;</li>
 *   <li>a grandfathered row is exempt, not compliant.</li>
 * </ul>
 *
 * <p>Note the deliberate absence of a policy-repository stub in most tests: the field is
 * {@code @Autowired(required = false)} and when absent the service falls back to the ADR
 * default of 5 rather than to "no clock". That polarity is the opposite of every other
 * optional dependency in the service, and {@link #aMissingPolicyRowStillArmsTheClock}
 * asserts it — an optional config that no-ops the control is how F2-62 happened.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmergencyRatificationTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE = 500L;
    private static final Long APPROVER_ID = 11L;
    private static final Long RAISER_ID = 12L;
    private static final Long RATIFIER_ID = 13L;

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private ChangeApprovalRepository approvalRepo;
    @Mock private ChangeTaskRepository taskRepo;
    @Mock private ChangeLinkRepository linkRepo;
    @Mock private ChangeCommentRepository commentRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ChangeRatificationPolicyRepository policyRepo;

    private ChangeManagementService service;

    @BeforeEach void setUp() {
        service = new ChangeManagementService(changeRepo, approvalRepo, taskRepo, linkRepo,
                                             commentRepo, audit, publisher);
        TenantContext.setCurrentTenant(ORG);
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(i -> i.getArgument(0));
        // The emergency approver is a CHANGE:APPROVE holder — this class tests ratification,
        // not the decision guard (ChangeDecisionAuthorityTest owns that).
        authenticateAs("ecab-approver", APPROVER_ID);
    }

    @AfterEach void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String username, Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new com.tss.grc.security.GrcUserDetails(
                    userId, ORG, username, "n/a", true, true,
                    List.of(new SimpleGrantedAuthority("CHANGE_WRITE"),
                            new SimpleGrantedAuthority("CHANGE_APPROVE"))),
                "n/a",
                List.of(new SimpleGrantedAuthority("CHANGE_WRITE"),
                        new SimpleGrantedAuthority("CHANGE_APPROVE"))));
    }

    private static ChangeRequest emergency(String status) {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-REVIEW-TEST-D3");
        c.setStatus(status);
        c.setChangeType("EMERGENCY");
        c.setRequestedById(RAISER_ID);
        c.setRequestedByName("raiser");
        return c;
    }

    private void withPolicy(int days) {
        ChangeRatificationPolicy p = new ChangeRatificationPolicy();
        p.setId(9L);
        p.setOrganisationId(ORG);
        p.setSlaBusinessDays(days);
        p.setEscalationRole("CISO");
        when(policyRepo.findByOrganisationId(ORG)).thenReturn(Optional.of(p));
        ReflectionTestUtils.setField(service, "ratificationPolicyRepo", policyRepo);
    }

    // ── arming ────────────────────────────────────────────────────────────────

    @Test
    void emergencyApprovalArmsTheDutyButNotTheClock() {
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(emergency("DRAFT")));

        ChangeRequest out = service.transition(CHANGE, "EMERGENCY_APPROVED", new HashMap<>());

        assertThat(out.getRatificationState())
            .as("ADR-017 D-3 starts the clock at implementation, not at approval — a change "
              + "authorised but not yet live is neither compliant nor breached")
            .isEqualTo(RatificationStates.AWAITING_IMPLEMENTATION);
        assertThat(out.getRatificationDueAt()).isNull();
    }

    @Test
    void theApproverIsRecordedOnTheChangeAndNotOnlyInTheLedger() {
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(emergency("DRAFT")));

        ChangeRequest out = service.transition(CHANGE, "EMERGENCY_APPROVED", new HashMap<>());

        // F2-93. Without this the SoD test below has nothing to compare against, and an
        // examiner asking "who authorised this emergency change?" is sent to a log.
        assertThat(out.getCabDecisionById()).isEqualTo(APPROVER_ID);
        assertThat(out.getCabDecisionByName()).isEqualTo("ecab-approver");
    }

    @Test
    void implementationStartsTheClockAtFiveSaudiBusinessDays() {
        withPolicy(5);
        ChangeRequest c = emergency("EMERGENCY_APPROVED");
        c.setRatificationState(RatificationStates.AWAITING_IMPLEMENTATION);
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        ChangeRequest out = service.transition(CHANGE, "IMPLEMENTING", new HashMap<>());

        assertThat(out.getRatificationState()).isEqualTo(RatificationStates.PENDING);
        assertThat(out.getRatificationDueAt())
            .as("PENDING without a due date is impossible by DB constraint; the service must "
              + "not be the thing that tries to create it")
            .isNotNull();
        // Five business days out, on the Sunday–Thursday week: never a Friday or Saturday.
        assertThat(out.getRatificationDueAt().getDayOfWeek())
            .as("the Saudi working week is Sunday–Thursday, so a deadline must not land on "
              + "the weekend — a calendar-day SLA would")
            .isNotIn(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        assertThat(out.getRatificationDueAt()).isAfter(out.getImplementationStarted());
        // 5 business days spans at least 7 calendar days once the weekend is skipped.
        assertThat(out.getRatificationDueAt()).isAfterOrEqualTo(out.getImplementationStarted().plusDays(6));
    }

    @Test
    void aMissingPolicyRowStillArmsTheClock() {
        // No policyRepo injected at all — the F2-62 polarity check. An absent config row
        // must mean "the ADR default", never "no control".
        ChangeRequest c = emergency("EMERGENCY_APPROVED");
        c.setRatificationState(RatificationStates.AWAITING_IMPLEMENTATION);
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        ChangeRequest out = service.transition(CHANGE, "IMPLEMENTING", new HashMap<>());

        assertThat(out.getRatificationState()).isEqualTo(RatificationStates.PENDING);
        assertThat(out.getRatificationDueAt()).isNotNull();
    }

    @Test
    void aNormalChangeIsNeverDraggedIntoTheRatificationRegister() {
        ChangeRequest c = emergency("CAB_APPROVED");
        c.setChangeType("NORMAL");
        c.setRiskLevel(null);
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        ChangeRequest out = service.transition(CHANGE, "SCHEDULED", new HashMap<>());

        assertThat(out.getRatificationState()).isEqualTo(RatificationStates.NOT_REQUIRED);
        assertThat(out.getRatificationDueAt()).isNull();
    }

    // ── the SoD gate, which is the point of the feature ───────────────────────

    @Test
    void theEmergencyApproverCannotRatifyTheirOwnChange() {
        ChangeRequest c = pending();
        c.setCabDecisionById(APPROVER_ID);
        c.setCabDecisionByName("ecab-approver");
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.ratify(CHANGE, "RATIFY", "looks fine to me",
                                                APPROVER_ID, "ecab-approver"))
            .isInstanceOf(IllegalStateException.class)   // -> 409
            .hasMessageContaining("Segregation of duties")
            .hasMessageContaining("emergency-approved");

        verify(changeRepo, never()).save(any(ChangeRequest.class));
    }

    @Test
    void theRaiserCannotRatifyEither() {
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.ratify(CHANGE, "RATIFY", null, RAISER_ID, "raiser"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("raised or owns");
    }

    @Test
    void anIndependentApproverCanRatify() {
        ChangeRequest c = pending();
        c.setCabDecisionById(APPROVER_ID);
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        ChangeRequest out = service.ratify(CHANGE, "RATIFY", "Reviewed at CAB 2026-08-09", RATIFIER_ID, "cab-chair");

        assertThat(out.getRatificationState()).isEqualTo(RatificationStates.RATIFIED);
        assertThat(out.getRatifiedById()).isEqualTo(RATIFIER_ID);
        assertThat(out.getRatifiedByName()).isEqualTo("cab-chair");
        assertThat(out.getRatificationNotes()).isEqualTo("Reviewed at CAB 2026-08-09");
        assertThat(out.getRatifiedAt()).isNotNull();
    }

    @Test
    void refusingToRatifyLeavesTheChangeMarkedUnauthorised() {
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(pending()));

        ChangeRequest out = service.ratify(CHANGE, "REJECT", "No business case on record",
                                           RATIFIER_ID, "cab-chair");

        assertThat(out.getRatificationState()).isEqualTo(RatificationStates.REJECTED);
    }

    @Test
    void anAnonymousCallerCannotRatify() {
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.ratify(CHANGE, "RATIFY", null, null, "  "))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("identified user");
    }

    // ── the states that must refuse ───────────────────────────────────────────

    @Test
    void aChangeThatOwesNoRatificationCannotBeRatified() {
        ChangeRequest c = emergency("CLOSED");
        c.setChangeType("NORMAL");
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.ratify(CHANGE, "RATIFY", null, RATIFIER_ID, "cab-chair"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not require retrospective ratification");
    }

    @Test
    void aGrandfatheredChangeIsExemptRatherThanRatifiable() {
        ChangeRequest c = emergency("IMPLEMENTED");
        c.setRatificationState(RatificationStates.GRANDFATHERED);
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.ratify(CHANGE, "RATIFY", null, RATIFIER_ID, "cab-chair"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pre-dates the ratification clock");
    }

    @Test
    void aChangeNotYetImplementedHasNothingToRatifyRetrospectively() {
        ChangeRequest c = emergency("EMERGENCY_APPROVED");
        c.setRatificationState(RatificationStates.AWAITING_IMPLEMENTATION);
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.ratify(CHANGE, "RATIFY", null, RATIFIER_ID, "cab-chair"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("has not been implemented yet");
    }

    @Test
    void aRatificationDecisionIsRecordedOnce() {
        ChangeRequest c = pending();
        c.setRatificationState(RatificationStates.RATIFIED);
        c.setRatifiedAt(LocalDateTime.now().minusDays(1));
        c.setRatifiedByName("cab-chair");
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.ratify(CHANGE, "REJECT", "changed my mind", RATIFIER_ID, "someone-else"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already ratified");
    }

    @Test
    void anInvalidDecisionIsRefusedAtTheBoundary() {
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.ratify(CHANGE, "MAYBE", null, RATIFIER_ID, "cab-chair"))
            .isInstanceOf(IllegalArgumentException.class)          // -> 400, not 500 (F2-91)
            .hasMessageContaining("Invalid ratification decision");
    }

    // ── late ratification keeps the breach on the record ──────────────────────

    @Test
    void ratifyingLateDoesNotEraseTheBreach() {
        ChangeRequest c = pending();
        c.setRatificationDueAt(LocalDateTime.now().minusDays(10));
        c.setRatificationEscalatedAt(LocalDateTime.now().minusDays(3));
        when(changeRepo.findById(CHANGE)).thenReturn(Optional.of(c));

        ChangeRequest out = service.ratify(CHANGE, "RATIFY", "Late CAB review", RATIFIER_ID, "cab-chair");

        assertThat(out.getRatificationState()).isEqualTo(RatificationStates.RATIFIED);
        assertThat(out.getRatificationEscalatedAt())
            .as("\"ratified late\" and \"never breached\" are different governance facts and must "
              + "not collapse into the same record")
            .isNotNull();

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(org.mockito.ArgumentMatchers.eq("RATIFICATION_RATIFY"),
                          org.mockito.ArgumentMatchers.eq("ChangeRequest"),
                          org.mockito.ArgumentMatchers.eq(CHANGE), msg.capture());
        assertThat(msg.getValue())
            .as("the ledger line must say it was late, not merely that it happened")
            .contains("AFTER the SLA deadline");
    }

    // ── the bound (amendment 2) ───────────────────────────────────────────────

    @Test
    void theTenantCannotSetA365DayRatificationSla() {
        withPolicy(5);

        assertThatThrownBy(() -> service.updateRatificationPolicy(365, null, RATIFIER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 10 business days")
            .hasMessageContaining("two weekly CAB cycles");
    }

    @Test
    void theTenantCanTightenTheSlaWithinTheBound() {
        withPolicy(5);
        when(policyRepo.save(any(ChangeRatificationPolicy.class))).thenAnswer(i -> i.getArgument(0));

        ChangeRatificationPolicy out = service.updateRatificationPolicy(3, "CISO", RATIFIER_ID);

        assertThat(out.getSlaBusinessDays()).isEqualTo(3);
    }

    @Test
    void aZeroDaySlaIsAlsoRefused() {
        withPolicy(5);

        assertThatThrownBy(() -> service.updateRatificationPolicy(0, null, RATIFIER_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOutOfBoundStoredValueFallsBackToTheAdrDefaultRatherThanBeingHonoured() {
        // If a row somehow carries an illegal value (constraint dropped, restore from an
        // older schema), the service must not honour it. Reading 365 from the database and
        // using it would defeat the ceiling by the back door.
        withPolicy(365);

        assertThat(service.slaBusinessDays(ORG))
            .isEqualTo(RatificationStates.DEFAULT_SLA_BUSINESS_DAYS);
    }

    private static ChangeRequest pending() {
        ChangeRequest c = emergency("IMPLEMENTED");
        c.setRatificationState(RatificationStates.PENDING);
        c.setImplementationStarted(LocalDateTime.now().minusDays(2));
        c.setRatificationDueAt(LocalDateTime.now().plusDays(3));
        return c;
    }
}
