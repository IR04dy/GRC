package com.tss.grc.module.workflow.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.workflow.entity.*;
import com.tss.grc.module.workflow.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic state-machine evaluator. Implements buyer-assessment §6.1 — the
 * single highest-leverage strategic feature: workflows defined in data, not
 * code. Once existing modules' hard-coded workflows are migrated into this
 * engine, every future state-machine change becomes a configuration tweak
 * instead of a code-+-deploy cycle.
 *
 * MVP scope (this commit):
 *  · Read-only definition browsing + state-machine viewer.
 *  · Per-target-entity instance lifecycle: create, currentState, allowedTransitions, executeTransition.
 *  · Action log captures every transition for audit.
 *  · One reference workflow seeded (RISK_LIFECYCLE_V1) — exercised as the
 *    integration test target. Migrating real Risk records onto the engine is
 *    a follow-up tracked in docs/diagrams/19-workflow-engine-roadmap.md.
 */
@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowStateRepository      stateRepo;
    private final WorkflowTransitionRepository transitionRepo;
    private final WorkflowInstanceRepository   instanceRepo;
    private final WorkflowActionLogRepository  logRepo;

    // ── Definitions ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> listDefinitions() {
        return defRepo.findByOrganisationIdAndIsActiveTrue(TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> listDefinitionsForModule(String targetModule) {
        return defRepo.findByOrganisationIdAndTargetModule(TenantContext.getCurrentTenant(), targetModule);
    }

    /**
     * Returns a definition with its states + transitions inline as a single
     * payload — what the frontend needs to render the state-machine diagram.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDefinitionWithGraph(Long definitionId) {
        WorkflowDefinition def = defRepo.findById(definitionId)
            .filter(d -> d.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Workflow definition not found: " + definitionId));

        List<WorkflowState> states = stateRepo.findByDefinitionIdOrderBySortOrder(definitionId);
        List<WorkflowTransition> transitions = transitionRepo.findByDefinitionIdOrderBySortOrder(definitionId);

        // Resolve transition state codes for easier frontend rendering.
        Map<Long, String> stateCodeById = new HashMap<>();
        for (WorkflowState s : states) stateCodeById.put(s.getId(), s.getCode());

        List<Map<String, Object>> transitionViews = transitions.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",            t.getId());
            m.put("fromStateCode", stateCodeById.get(t.getFromStateId()));
            m.put("toStateCode",   stateCodeById.get(t.getToStateId()));
            m.put("actionCode",    t.getActionCode());
            m.put("actionNameEn",  t.getActionNameEn());
            m.put("actionNameAr",  t.getActionNameAr());
            m.put("requiredRole",  t.getRequiredRole());
            m.put("slaHours",      t.getSlaHours());
            m.put("descriptionEn", t.getDescriptionEn());
            return m;
        }).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("definition",  def);
        out.put("states",      states);
        out.put("transitions", transitionViews);
        return out;
    }

    // ── Instances (per-target-entity) ──────────────────────────────────────

    @Transactional
    public WorkflowInstance startInstance(Long definitionId, String targetModule, String targetEntity, Long targetEntityId, Long ownerId) {
        Long org = TenantContext.getCurrentTenant();
        WorkflowDefinition def = defRepo.findById(definitionId)
            .filter(d -> d.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Workflow definition not found: " + definitionId));

        if (instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
                org, targetModule, targetEntity, targetEntityId).isPresent()) {
            throw new IllegalArgumentException("Workflow instance already exists for this target entity");
        }

        WorkflowState initial = stateRepo.findByDefinitionIdAndIsInitialTrue(definitionId)
            .orElseThrow(() -> new IllegalArgumentException("Definition has no initial state"));

        WorkflowInstance i = new WorkflowInstance();
        i.setOrganisationId(org);
        i.setDefinitionId(definitionId);
        i.setTargetModule(targetModule);
        i.setTargetEntity(targetEntity);
        i.setTargetEntityId(targetEntityId);
        i.setCurrentStateId(initial.getId());
        i.setOwnerId(ownerId);
        i.setStartedAt(LocalDateTime.now());
        i.setLastTransitionAt(LocalDateTime.now());
        i = instanceRepo.save(i);

        // Audit-log the initial state entry.
        WorkflowActionLog log = new WorkflowActionLog();
        log.setInstanceId(i.getId());
        log.setActionCode("__init__");
        log.setFromStateId(null);
        log.setToStateId(initial.getId());
        // F2-431 — this recorded the ASSIGNEE as having performed the start. `ownerId` is a
        // legitimate assignment (i.setOwnerId above, the caller's to choose) and it was ALSO
        // being reused as the actor: two different facts from one value, which is F2-226's
        // distinction between a name who WILL act and the person who DID.
        log.setExecutedById(CurrentActor.requireUserId("start a workflow instance"));
        log.setExecutedAt(LocalDateTime.now());
        log.setComments("Instance started; entered initial state " + initial.getCode());
        logRepo.save(log);
        return i;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInstance(Long instanceId) {
        Long org = TenantContext.getCurrentTenant();
        WorkflowInstance i = instanceRepo.findById(instanceId)
            .filter(x -> x.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Workflow instance not found: " + instanceId));

        WorkflowState current = stateRepo.findById(i.getCurrentStateId()).orElse(null);
        List<WorkflowTransition> allowed = transitionRepo
            .findByDefinitionIdAndFromStateIdOrderBySortOrder(i.getDefinitionId(), i.getCurrentStateId());
        List<WorkflowActionLog> history = logRepo.findByInstanceIdOrderByExecutedAtDesc(instanceId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instance",            i);
        out.put("currentState",        current);
        out.put("allowedTransitions",  allowed);
        out.put("history",             history);
        return out;
    }

    @Transactional
    public WorkflowInstance executeTransition(Long instanceId, String actionCode, String comments) {
        Long org = TenantContext.getCurrentTenant();
        WorkflowInstance i = instanceRepo.findById(instanceId)
            .filter(x -> x.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Workflow instance not found: " + instanceId));

        // F2-304 — a well-formed request for an action that is not available from where the
        // instance stands is a CONFLICT, not malformed input, and the caller needs to know which
        // actions ARE available. This machine is data-driven (rows in workflow.transitions), so its
        // next states are reached BY ACTION CODE: `to` is the action requested and `allowed` is the
        // set of action codes the current state offers, which the sort-ordered finder already
        // returns. That repurposing is deliberate and stated, because IllegalTransitionException's
        // javadoc describes `to`/`allowed` as states.
        //
        // The lookups sit inside orElseThrow, so they run only on the refusal path.
        WorkflowTransition tr = transitionRepo
            .findByDefinitionIdAndFromStateIdAndActionCode(i.getDefinitionId(), i.getCurrentStateId(), actionCode)
            .orElseThrow(() -> new IllegalTransitionException(
                stateRepo.findById(i.getCurrentStateId())
                    .map(WorkflowState::getCode)
                    .orElse("state#" + i.getCurrentStateId()),
                actionCode,
                transitionRepo
                    .findByDefinitionIdAndFromStateIdOrderBySortOrder(
                        i.getDefinitionId(), i.getCurrentStateId())
                    .stream()
                    .map(WorkflowTransition::getActionCode)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))));

        Long fromStateId = i.getCurrentStateId();
        i.setCurrentStateId(tr.getToStateId());
        i.setLastTransitionAt(LocalDateTime.now());

        WorkflowState toState = stateRepo.findById(tr.getToStateId()).orElseThrow();
        if (Boolean.TRUE.equals(toState.getIsTerminal())) {
            i.setCompletedAt(LocalDateTime.now());
        }
        instanceRepo.save(i);

        WorkflowActionLog log = new WorkflowActionLog();
        log.setInstanceId(i.getId());
        log.setTransitionId(tr.getId());
        log.setActionCode(actionCode);
        log.setFromStateId(fromStateId);
        log.setToStateId(tr.getToStateId());
        // F2-431 — the caller used to name the executor, beside setExecutedAt(now()) two
        // lines down. workflow.action_log IS the trail of who moved an instance through
        // which transition, on the engine 14 modules adopt, so a caller-declared executor
        // is the one field in it that could not be relied on.
        log.setExecutedById(CurrentActor.requireUserId("execute a workflow transition"));
        log.setExecutedAt(LocalDateTime.now());
        log.setComments(comments);
        logRepo.save(log);
        return i;
    }

    @Transactional(readOnly = true)
    public WorkflowInstance findInstanceForTarget(String targetModule, String targetEntity, Long targetEntityId) {
        return instanceRepo.findByOrganisationIdAndTargetModuleAndTargetEntityAndTargetEntityId(
            TenantContext.getCurrentTenant(), targetModule, targetEntity, targetEntityId).orElse(null);
    }
}
