package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.*;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-29 — deciding a change is a different privilege from raising one.
 *
 * <h3>What was wrong</h3>
 * <p>{@code POST /api/v1/changes/{id}/transition} carried one guard, {@code CHANGE:WRITE}, for every
 * target status — including the three that <b>are</b> CAB decisions. Reproduced live before the fix
 * with {@code ranalyst}, holding {@code CHANGE:WRITE} and not {@code CHANGE:APPROVE}:
 *
 * <pre>
 *   POST /{id}/approvals                          -> 403   (correctly refused)
 *   POST /{id}/transition {"EMERGENCY_APPROVED"}  -> 200   status=EMERGENCY_APPROVED, cabDecisionAt set
 *   POST /{id}/transition {"CAB_REJECTED"}        -> 200   a CAB rejection with zero votes on record
 * </pre>
 *
 * <p>33 of 57 roles hold {@code CHANGE:WRITE} without {@code CHANGE:APPROVE}. No migration was needed:
 * all 9 {@code CHANGE:APPROVE} roles already hold {@code WRITE} and {@code READ}, so the V301 grant
 * matrix was right and only the endpoint disagreed with it.
 *
 * <h3>Why this class exists at all</h3>
 * <p>The rule is enforced in the service, not in a {@code @PreAuthorize}, because the target arrives in
 * the request body. That means <b>the annotation cannot tell a reader the whole rule</b> — anything
 * scanning {@code @PreAuthorize} (the authorization ratchet included) sees only {@code CHANGE:WRITE} on
 * this endpoint. A test is the only place the full rule is legible and the only thing that will fail if
 * someone deletes the check.
 *
 * <p>The last test is the anti-drift ratchet: every target reachable in the ITIL state machine must be
 * consciously classified as a decision or an operational move. Adding a status to
 * {@code NORMAL_TRANSITIONS} without deciding which it is fails the build, rather than silently
 * defaulting to {@code WRITE} — which is exactly how the original defect arrived.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // targets that refuse never reach the repositories
class ChangeDecisionAuthorityTest {

    private static final Long ORG_ID = 1L;
    private static final Long CHANGE_ID = 100L;

    /** The three targets that record a CAB decision, with a state they are legally reachable from. */
    private static final Map<String, String[]> DECISIONS = Map.of(
        "EMERGENCY_APPROVED", new String[]{"DRAFT",       "EMERGENCY"},
        "CAB_APPROVED",       new String[]{"CAB_PENDING", "NORMAL"},
        "CAB_REJECTED",       new String[]{"CAB_PENDING", "NORMAL"});

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private ChangeApprovalRepository approvalRepo;
    @Mock private ChangeTaskRepository taskRepo;
    @Mock private ChangeLinkRepository linkRepo;
    @Mock private ChangeCommentRepository commentRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private ChangeManagementService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); SecurityContextHolder.clearContext(); }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private static ChangeRequest change(String status, String type) {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG_ID);
        c.setStatus(status);
        c.setChangeType(type);
        return c;
    }

    private void authenticatedWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("probe", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    /** Make the change readable, saveable, and (for CAB_APPROVED) backed by one APPROVE vote. */
    private void stubRepos(String status, String type) {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change(status, type)));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        ChangeApproval vote = new ChangeApproval();
        vote.setDecision("APPROVE");
        vote.setApproverName("Bob CAB");
        when(approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of(vote));
    }

    // ─── the defect, in both directions ──────────────────────────────────────

    @Test
    void writeOnlyPersona_cannot_record_any_CAB_decision() {
        authenticatedWith("CHANGE_READ", "CHANGE_WRITE");
        DECISIONS.forEach((target, from) -> {
            when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change(from[0], from[1])));
            assertThatThrownBy(() -> service.transition(CHANGE_ID, target, new HashMap<>()))
                .as("CHANGE:WRITE alone must not record the CAB decision '%s'", target)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("requires CHANGE:APPROVE");
        });
    }

    @Test
    void approverPersona_can_record_every_CAB_decision() {
        authenticatedWith("CHANGE_READ", "CHANGE_WRITE", "CHANGE_APPROVE");
        DECISIONS.forEach((target, from) -> {
            stubRepos(from[0], from[1]);
            ChangeRequest out = service.transition(CHANGE_ID, target, new HashMap<>());
            assertThat(out.getStatus())
                .as("CHANGE:APPROVE must be able to record '%s'", target)
                .isEqualTo(target);
        });
    }

    /**
     * PLATFORM_ADMIN bypasses in code and does <b>not</b> hold CHANGE:APPROVE in the grant matrix.
     * Pinned deliberately: it is why probing this defect as {@code admin} showed nothing, and why the
     * measurement had to be run as a real persona.
     */
    @Test
    void platformAdmin_bypasses_without_holding_the_permission() {
        authenticatedWith("ROLE_PLATFORM_ADMIN");
        stubRepos("DRAFT", "EMERGENCY");
        assertThat(service.transition(CHANGE_ID, "EMERGENCY_APPROVED", new HashMap<>()).getStatus())
            .isEqualTo("EMERGENCY_APPROVED");
    }

    @Test
    void unauthenticated_context_is_refused_not_waved_through() {
        SecurityContextHolder.clearContext();
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("DRAFT", "EMERGENCY")));
        assertThatThrownBy(() -> service.transition(CHANGE_ID, "EMERGENCY_APPROVED", new HashMap<>()))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("identified user");
    }

    // ─── the other half: no over-tightening ──────────────────────────────────

    /**
     * The failure mode opposite to the defect. Six of seven prescriptions in this programme were
     * falsified by measurement, most of them for asserting more than the platform could support — so
     * the operational moves are pinned as explicitly as the decisions.
     */
    @Test
    void writeOnlyPersona_keeps_every_operational_transition() {
        authenticatedWith("CHANGE_READ", "CHANGE_WRITE");
        Map<String, String[]> operational = Map.of(
            "SUBMITTED",       new String[]{"DRAFT",           "NORMAL"},
            "IMPACT_ASSESSED", new String[]{"SUBMITTED",       "NORMAL"},
            "CAB_PENDING",     new String[]{"IMPACT_ASSESSED", "NORMAL"},
            "SCHEDULED",       new String[]{"CAB_APPROVED",    "NORMAL"},
            "IMPLEMENTING",    new String[]{"SCHEDULED",       "NORMAL"},
            "IMPLEMENTED",     new String[]{"IMPLEMENTING",    "NORMAL"},
            "CANCELLED",       new String[]{"DRAFT",           "NORMAL"},
            "DRAFT",           new String[]{"CAB_REJECTED",    "NORMAL"});
        operational.forEach((target, from) -> {
            stubRepos(from[0], from[1]);
            assertThatCode(() -> service.transition(CHANGE_ID, target, new HashMap<>()))
                .as("'%s' is an operational move and must stay on CHANGE:WRITE", target)
                .doesNotThrowAnyException();
        });
    }

    /**
     * Authority is checked <b>before</b> the state machine, so an unauthorised caller cannot use the
     * error type to learn which transitions would have been legal for this change.
     */
    @Test
    void authority_is_checked_before_the_state_machine() {
        authenticatedWith("CHANGE_READ", "CHANGE_WRITE");
        // CLOSED -> CAB_APPROVED is an illegal transition AND an unauthorised one.
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change("CLOSED", "NORMAL")));
        assertThatThrownBy(() -> service.transition(CHANGE_ID, "CAB_APPROVED", new HashMap<>()))
            .isInstanceOf(AccessDeniedException.class);   // not IllegalArgumentException
    }

    // ─── anti-drift ratchet ──────────────────────────────────────────────────

    /**
     * Every target the ITIL state machine can reach must be consciously classified: a CAB decision
     * (requires {@code CHANGE:APPROVE}) or an operational move (keeps {@code CHANGE:WRITE}). A new
     * status added to {@code NORMAL_TRANSITIONS} and to neither list fails here.
     *
     * <p>Without this, the default for a new state is {@code WRITE} — silently, which is precisely how
     * {@code EMERGENCY_APPROVED} came to be self-servable. X-2 is queued to extend the status set
     * 14 → 17 ({@code CAB_DEFERRED}, {@code MORE_INFO_REQUIRED}, {@code PENDING_RATIFICATION}); at
     * least two of those three are plainly CAB decisions, and this test is what will make whoever
     * lands X-2 say so.
     */
    @Test
    @SuppressWarnings("unchecked")
    void every_state_machine_target_is_classified_as_decision_or_operational() throws Exception {
        Field tf = ChangeManagementService.class.getDeclaredField("NORMAL_TRANSITIONS");
        tf.setAccessible(true);
        Map<String, Set<String>> transitions = (Map<String, Set<String>>) tf.get(null);

        Field df = ChangeManagementService.class.getDeclaredField("DECISION_TARGETS");
        df.setAccessible(true);
        Set<String> decisions = (Set<String>) df.get(null);

        // Mirrors the operational set asserted above, plus the terminal/documentation states that
        // have no authority question: they record what happened, they do not authorise it.
        Set<String> operational = Set.of(
            "DRAFT", "SUBMITTED", "IMPACT_ASSESSED", "CAB_PENDING", "SCHEDULED",
            "IMPLEMENTING", "IMPLEMENTED", "ROLLED_BACK", "POST_REVIEW", "CLOSED", "CANCELLED");

        Set<String> reachable = new LinkedHashSet<>();
        transitions.values().forEach(reachable::addAll);

        Set<String> unclassified = new LinkedHashSet<>(reachable);
        unclassified.removeAll(decisions);
        unclassified.removeAll(operational);

        assertThat(unclassified)
            .as("new lifecycle target(s) %s are neither a CAB decision nor an operational move — "
              + "add them to ChangeManagementService.DECISION_TARGETS or to the operational set here, "
              + "because the silent default is CHANGE:WRITE and that is the F2-29 defect",
                unclassified)
            .isEmpty();

        // Anti-vacuity: if the reflection ever stops resolving the real fields, the assertion above
        // passes trivially over an empty set. Pin the measured sizes so it cannot go green empty.
        assertThat(reachable).hasSizeGreaterThanOrEqualTo(13);
        assertThat(decisions).containsExactlyInAnyOrder(
            "CAB_APPROVED", "CAB_REJECTED", "EMERGENCY_APPROVED");
    }
}
