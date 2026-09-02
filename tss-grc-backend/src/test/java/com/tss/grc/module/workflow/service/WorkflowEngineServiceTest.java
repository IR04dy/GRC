package com.tss.grc.module.workflow.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.security.GrcUserDetails;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.workflow.entity.*;
import com.tss.grc.module.workflow.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowEngineService — Sprint 4 §4.1 test backlog.
 *
 * The workflow engine is the most strategic feature in the platform — once
 * modules migrate onto it, every state-machine change becomes a configuration
 * tweak instead of a deploy. The contracts below MUST hold:
 *
 *   1. startInstance() refuses duplicate instances per (org, module, entity, id).
 *   2. startInstance() requires the definition to have an initial state.
 *   3. startInstance() audit-logs the entry with action_code = "__init__".
 *   4. executeTransition() routes by (definitionId, currentStateId, actionCode);
 *      a missing transition throws IllegalArgumentException (caller error, not 404).
 *   5. executeTransition() entering an isTerminal state stamps completedAt.
 *   6. executeTransition() persists an audit log row with from→to and comment.
 *   7. Cross-tenant lookups (definition or instance) throw EntityNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long DEF_ID    = 10L;
    private static final Long INST_ID   = 50L;
    private static final Long S_DRAFT   = 100L;
    private static final Long S_REVIEW  = 101L;
    private static final Long S_DONE    = 102L;

    @Mock private WorkflowDefinitionRepository defRepo;
    @Mock private WorkflowStateRepository stateRepo;
    @Mock private WorkflowTransitionRepository transitionRepo;
    @Mock private WorkflowInstanceRepository instanceRepo;
    @Mock private WorkflowActionLogRepository logRepo;
    @InjectMocks private WorkflowEngineService service;

    /**
     * F2-431 — the actor is now the authenticated principal, so every test needs one. Its id
     * is deliberately NOT the {@code 99L} owner id the callers pass: if the two were equal,
     * an assertion could not tell an attribution taken from the principal from one copied out
     * of the argument, which is the whole distinction under test.
     */
    private static final Long ACTOR_ID = 3L;
    private static final Long OWNER_ID = 99L;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        GrcUserDetails u = new GrcUserDetails(ACTOR_ID, ORG_ID, "so_officer", "x",
                                              true, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, "x", u.getAuthorities()));
    }

    @AfterEach  void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static WorkflowDefinition def(Long orgId) {
        WorkflowDefinition d = new WorkflowDefinition();
        d.setId(DEF_ID);
        d.setOrganisationId(orgId);
        d.setCode("RISK_LIFECYCLE_V1");
        d.setNameEn("Risk Lifecycle");
        d.setTargetModule("RISK");
        d.setTargetEntity("Risk");
        return d;
    }

    private static WorkflowState state(Long id, String code, boolean initial, boolean terminal) {
        WorkflowState s = new WorkflowState();
        s.setId(id);
        s.setDefinitionId(DEF_ID);
        s.setCode(code);
        s.setIsInitial(initial);
        s.setIsTerminal(terminal);
        return s;
    }

    private static WorkflowTransition transition(Long id, Long fromId, Long toId, String action) {
        WorkflowTransition t = new WorkflowTransition();
        t.setId(id);
        t.setDefinitionId(DEF_ID);
        t.setFromStateId(fromId);
        t.setToStateId(toId);
        t.setActionCode(action);
        return t;
    }

    private static WorkflowInstance instance(Long curStateId, Long orgId) {
        WorkflowInstance i = new WorkflowInstance();
        i.setId(INST_ID);
        i.setOrganisationId(orgId);
        i.setDefinitionId(DEF_ID);
        i.setTargetModule("RISK");
        i.setTargetEntity("Risk");
        i.setTargetEntityId(7L);
        i.setCurrentStateId(curStateId);
        return i;
    }

    // ─── startInstance ───────────────────────────────────────────────

    @Test
    void startInstance_creates_instance_and_audit_log() {
        when(defRepo.findById(DEF_ID)).thenReturn(Optional.of(def(ORG_ID)));
        when(instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
                ORG_ID, "RISK", "Risk", 7L)).thenReturn(Optional.empty());
        when(stateRepo.findByDefinitionIdAndIsInitialTrue(DEF_ID))
            .thenReturn(Optional.of(state(S_DRAFT, "DRAFT", true, false)));
        when(instanceRepo.save(any(WorkflowInstance.class))).thenAnswer(inv -> {
            WorkflowInstance saved = inv.getArgument(0);
            saved.setId(INST_ID);
            return saved;
        });

        WorkflowInstance i = service.startInstance(DEF_ID, "RISK", "Risk", 7L, OWNER_ID);

        assertThat(i.getId()).isEqualTo(INST_ID);
        assertThat(i.getCurrentStateId()).isEqualTo(S_DRAFT);
        assertThat(i.getOwnerId())
            .as("the ASSIGNMENT is still the caller's to choose — F2-431 did not take it away")
            .isEqualTo(OWNER_ID);
        assertThat(i.getStartedAt()).isNotNull();
        assertThat(i.getLastTransitionAt()).isNotNull();

        // Verify the __init__ log entry
        ArgumentCaptor<WorkflowActionLog> logCaptor = ArgumentCaptor.forClass(WorkflowActionLog.class);
        verify(logRepo).save(logCaptor.capture());
        WorkflowActionLog log = logCaptor.getValue();
        assertThat(log.getActionCode()).isEqualTo("__init__");
        assertThat(log.getFromStateId()).isNull();
        assertThat(log.getToStateId()).isEqualTo(S_DRAFT);
        // F2-431 — the discriminating leg, and it carries F2-226's whole distinction in one
        // assertion pair: this used to read isEqualTo(99L), i.e. that the value the CALLER
        // passed is the value STORED. One value was serving two different facts — who the
        // instance is ASSIGNED to and who STARTED it — so the log credited the assignee with
        // an act they had not performed.
        assertThat(log.getExecutedById())
            .as("the ATTRIBUTION is the principal, never the owner id the caller sent")
            .isEqualTo(ACTOR_ID)
            .isNotEqualTo(OWNER_ID);
    }

    @Test
    void startInstance_duplicate_target_entity_throws() {
        when(defRepo.findById(DEF_ID)).thenReturn(Optional.of(def(ORG_ID)));
        when(instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
                ORG_ID, "RISK", "Risk", 7L)).thenReturn(Optional.of(instance(S_DRAFT, ORG_ID)));

        assertThatThrownBy(() -> service.startInstance(DEF_ID, "RISK", "Risk", 7L, OWNER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");

        verify(instanceRepo, never()).save(any());
        verify(logRepo, never()).save(any());
    }

    @Test
    void startInstance_definition_with_no_initial_state_throws() {
        when(defRepo.findById(DEF_ID)).thenReturn(Optional.of(def(ORG_ID)));
        when(instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
                ORG_ID, "RISK", "Risk", 7L)).thenReturn(Optional.empty());
        when(stateRepo.findByDefinitionIdAndIsInitialTrue(DEF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startInstance(DEF_ID, "RISK", "Risk", 7L, OWNER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("initial state");

        verify(instanceRepo, never()).save(any());
    }

    @Test
    void startInstance_definition_in_other_tenant_throws_EntityNotFound() {
        // Cross-tenant definition: must not be reachable
        when(defRepo.findById(DEF_ID)).thenReturn(Optional.of(def(OTHER_ORG)));

        assertThatThrownBy(() -> service.startInstance(DEF_ID, "RISK", "Risk", 7L, OWNER_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Workflow definition not found");
    }

    // ─── executeTransition ──────────────────────────────────────────

    @Test
    void executeTransition_advances_state_and_logs() {
        WorkflowInstance i = instance(S_DRAFT, ORG_ID);
        WorkflowTransition tr = transition(500L, S_DRAFT, S_REVIEW, "submit");
        when(instanceRepo.findById(INST_ID)).thenReturn(Optional.of(i));
        when(transitionRepo.findByDefinitionIdAndFromStateIdAndActionCode(DEF_ID, S_DRAFT, "submit"))
            .thenReturn(Optional.of(tr));
        when(stateRepo.findById(S_REVIEW))
            .thenReturn(Optional.of(state(S_REVIEW, "REVIEW", false, false)));
        when(instanceRepo.save(any(WorkflowInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowInstance after = service.executeTransition(INST_ID, "submit", "Looks ready");

        assertThat(after.getCurrentStateId()).isEqualTo(S_REVIEW);
        assertThat(after.getLastTransitionAt()).isNotNull();
        assertThat(after.getCompletedAt()).isNull();   // not terminal

        ArgumentCaptor<WorkflowActionLog> logCaptor = ArgumentCaptor.forClass(WorkflowActionLog.class);
        verify(logRepo).save(logCaptor.capture());
        WorkflowActionLog log = logCaptor.getValue();
        assertThat(log.getInstanceId()).isEqualTo(INST_ID);
        assertThat(log.getTransitionId()).isEqualTo(500L);
        assertThat(log.getActionCode()).isEqualTo("submit");
        assertThat(log.getFromStateId()).isEqualTo(S_DRAFT);
        assertThat(log.getToStateId()).isEqualTo(S_REVIEW);
        // F2-431 — was isEqualTo(99L): the caller named the executor of the transition, beside
        // setExecutedAt(now()) two lines down, on the engine 14 modules adopt.
        assertThat(log.getExecutedById())
            .as("workflow.action_log records who actually moved the instance")
            .isEqualTo(ACTOR_ID);
        assertThat(log.getComments()).isEqualTo("Looks ready");
    }

    @Test
    void executeTransition_into_terminal_state_stamps_completedAt() {
        WorkflowInstance i = instance(S_REVIEW, ORG_ID);
        WorkflowTransition tr = transition(501L, S_REVIEW, S_DONE, "approve");
        when(instanceRepo.findById(INST_ID)).thenReturn(Optional.of(i));
        when(transitionRepo.findByDefinitionIdAndFromStateIdAndActionCode(DEF_ID, S_REVIEW, "approve"))
            .thenReturn(Optional.of(tr));
        when(stateRepo.findById(S_DONE))
            .thenReturn(Optional.of(state(S_DONE, "DONE", false, /*terminal=*/true)));
        when(instanceRepo.save(any(WorkflowInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowInstance after = service.executeTransition(INST_ID, "approve", null);

        assertThat(after.getCurrentStateId()).isEqualTo(S_DONE);
        assertThat(after.getCompletedAt()).isNotNull();   // terminal → stamped
    }

    @Test
    /**
     * F2-304 — renamed. The old name asserted the old contract ({@code throws_IllegalArgument}),
     * which is the strongest form of a test pinning the thing being changed: a reader trusts the
     * name before the body.
     */
    void executeTransition_unavailableAction_isAConflictNamingTheAvailableOnes() {
        WorkflowInstance i = instance(S_DRAFT, ORG_ID);
        when(instanceRepo.findById(INST_ID)).thenReturn(Optional.of(i));
        when(transitionRepo.findByDefinitionIdAndFromStateIdAndActionCode(DEF_ID, S_DRAFT, "bogus"))
            .thenReturn(Optional.empty());

        // The engine is data-driven, so `allowed` is the set of action codes the current state
        // offers — queried only on the refusal path.
        when(transitionRepo.findByDefinitionIdAndFromStateIdOrderBySortOrder(DEF_ID, S_DRAFT))
            .thenReturn(List.of());
        when(stateRepo.findById(S_DRAFT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executeTransition(INST_ID, "bogus", null))
            .isInstanceOf(IllegalTransitionException.class)
            .hasMessageContaining("bogus");

        verify(instanceRepo, never()).save(any());
        verify(logRepo, never()).save(any());
    }

    @Test
    void executeTransition_other_tenant_throws_EntityNotFound() {
        when(instanceRepo.findById(INST_ID)).thenReturn(Optional.of(instance(S_DRAFT, OTHER_ORG)));

        assertThatThrownBy(() -> service.executeTransition(INST_ID, "submit", null))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Workflow instance not found");
    }


    /**
     * F2-431 — {@link com.tss.grc.common.security.CurrentActor#requireUserId} REFUSES rather than
     * recording a blank, so a workflow transition with no identified actor is not a transition
     * (F2-236's remedy). Both routes are {@code @PreAuthorize}-guarded, so a principal always
     * exists in production; this pins the refusal so a future off-request caller fails loudly
     * instead of writing an unattributed row into the trail.
     */
    @Test
    void executeTransition_withNoPrincipal_refusesRatherThanLoggingAnUnattributedRow() {
        SecurityContextHolder.clearContext();
        WorkflowInstance i = instance(S_DRAFT, ORG_ID);
        WorkflowTransition tr = transition(500L, S_DRAFT, S_REVIEW, "submit");
        when(instanceRepo.findById(INST_ID)).thenReturn(Optional.of(i));
        when(transitionRepo.findByDefinitionIdAndFromStateIdAndActionCode(DEF_ID, S_DRAFT, "submit"))
            .thenReturn(Optional.of(tr));
        when(stateRepo.findById(S_REVIEW))
            .thenReturn(Optional.of(state(S_REVIEW, "REVIEW", false, false)));

        assertThatThrownBy(() -> service.executeTransition(INST_ID, "submit", "no actor"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no identified actor");

        verify(logRepo, never()).save(any());
    }

    // ─── getInstance / getDefinitionWithGraph ───────────────────────

    @Test
    void getInstance_returns_instance_state_allowed_transitions_history() {
        WorkflowInstance i = instance(S_REVIEW, ORG_ID);
        when(instanceRepo.findById(INST_ID)).thenReturn(Optional.of(i));
        when(stateRepo.findById(S_REVIEW))
            .thenReturn(Optional.of(state(S_REVIEW, "REVIEW", false, false)));
        WorkflowTransition allowed = transition(501L, S_REVIEW, S_DONE, "approve");
        when(transitionRepo.findByDefinitionIdAndFromStateIdOrderBySortOrder(DEF_ID, S_REVIEW))
            .thenReturn(List.of(allowed));
        WorkflowActionLog log = new WorkflowActionLog();
        log.setActionCode("__init__");
        when(logRepo.findByInstanceIdOrderByExecutedAtDesc(INST_ID)).thenReturn(List.of(log));

        Map<String, Object> view = service.getInstance(INST_ID);
        assertThat(view).containsKeys("instance", "currentState", "allowedTransitions", "history");
        assertThat(view.get("instance")).isSameAs(i);
        assertThat(((List<?>) view.get("allowedTransitions"))).hasSize(1);
        assertThat(((List<?>) view.get("history"))).hasSize(1);
    }

    @Test
    void getInstance_other_tenant_throws_EntityNotFound() {
        when(instanceRepo.findById(INST_ID)).thenReturn(Optional.of(instance(S_DRAFT, OTHER_ORG)));

        assertThatThrownBy(() -> service.getInstance(INST_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getDefinitionWithGraph_resolves_state_codes_in_transitions() {
        when(defRepo.findById(DEF_ID)).thenReturn(Optional.of(def(ORG_ID)));
        WorkflowState a = state(S_DRAFT,  "DRAFT",  true,  false);
        WorkflowState b = state(S_REVIEW, "REVIEW", false, false);
        WorkflowState c = state(S_DONE,   "DONE",   false, true);
        when(stateRepo.findByDefinitionIdOrderBySortOrder(DEF_ID)).thenReturn(List.of(a, b, c));
        WorkflowTransition t1 = transition(1L, S_DRAFT,  S_REVIEW, "submit");
        WorkflowTransition t2 = transition(2L, S_REVIEW, S_DONE,   "approve");
        when(transitionRepo.findByDefinitionIdOrderBySortOrder(DEF_ID)).thenReturn(List.of(t1, t2));

        Map<String, Object> graph = service.getDefinitionWithGraph(DEF_ID);
        assertThat(graph).containsKeys("definition", "states", "transitions");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txs = (List<Map<String, Object>>) graph.get("transitions");
        assertThat(txs).hasSize(2);
        assertThat(txs.get(0)).containsEntry("fromStateCode", "DRAFT")
                              .containsEntry("toStateCode",   "REVIEW")
                              .containsEntry("actionCode",    "submit");
        assertThat(txs.get(1)).containsEntry("fromStateCode", "REVIEW")
                              .containsEntry("toStateCode",   "DONE");
    }

    @Test
    void getDefinitionWithGraph_other_tenant_throws() {
        when(defRepo.findById(DEF_ID)).thenReturn(Optional.of(def(OTHER_ORG)));

        assertThatThrownBy(() -> service.getDefinitionWithGraph(DEF_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── findInstanceForTarget ──────────────────────────────────────

    @Test
    void findInstanceForTarget_returns_null_when_absent() {
        when(instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
                eq(ORG_ID), eq("RISK"), eq("Risk"), eq(123L)))
            .thenReturn(Optional.empty());

        assertThat(service.findInstanceForTarget("RISK", "Risk", 123L)).isNull();
    }

    @Test
    void findInstanceForTarget_returns_instance_when_present() {
        WorkflowInstance i = instance(S_DRAFT, ORG_ID);
        when(instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
                eq(ORG_ID), eq("RISK"), eq("Risk"), eq(7L)))
            .thenReturn(Optional.of(i));

        assertThat(service.findInstanceForTarget("RISK", "Risk", 7L)).isSameAs(i);
    }
}
