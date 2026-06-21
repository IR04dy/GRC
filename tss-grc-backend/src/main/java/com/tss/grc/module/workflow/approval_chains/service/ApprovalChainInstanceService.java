package com.tss.grc.module.workflow.approval_chains.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.ActionResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceActionRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceStartRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceWithdrawRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.PendingDecisionResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.StepResponse;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainAction;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainInstance;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainStep;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainTemplate;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainActionRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainInstanceRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainStepRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sprint 13 — Instance lifecycle service for approval chains.
 *
 * <p>Owns the state machine and the rung-advancement rules:
 *
 * <ul>
 *   <li>{@link #startChain(InstanceStartRequest)} — materialises an
 *       instance from a template, sets {@code currentStepOrder=1},
 *       emits {@code APPROVAL_CHAIN_STARTED}.</li>
 *   <li>{@link #recordAction(Long, InstanceActionRequest)} — records
 *       one actor decision, then evaluates whether the current step's
 *       advancement conditions are satisfied (all mandatory rows at
 *       this {@code step_order} have an APPROVED action). Advances if
 *       so; finalises to APPROVED/REJECTED on the last rung or a
 *       REJECTED action.</li>
 *   <li>{@link #withdraw(Long, InstanceWithdrawRequest)} — originator
 *       cancels a not-yet-completed chain.</li>
 *   <li>{@link #escalate(Long)} — flag a stale instance; called from
 *       the scheduler. Does NOT change the assigned actor — that's a
 *       human decision via DELEGATED.</li>
 * </ul>
 *
 * <h3>Advancement logic — parallel completion</h3>
 * <p>When a step row has {@code stepType=PARALLEL}, several rows
 * share the same {@code stepOrder}. The chain advances only when
 * every mandatory row at the current {@code stepOrder} has an
 * APPROVED action. Non-mandatory rows are best-effort: they may be
 * actioned but do not block. A REJECTED action on any row at the
 * current step immediately ends the chain.
 *
 * <h3>Delegation</h3>
 * <p>A {@code DELEGATED} action records the hand-off in the action
 * log but does NOT advance the chain — the delegate's eventual
 * APPROVED / REJECTED is what counts. The step's {@code canDelegate}
 * flag gates whether DELEGATED is accepted at all.
 */
@Service
@RequiredArgsConstructor
public class ApprovalChainInstanceService {

    public static final Set<String> TERMINAL_STATUSES = Set.of("APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED");

    private final ApprovalChainTemplateRepository templateRepo;
    private final ApprovalChainStepRepository stepRepo;
    private final ApprovalChainInstanceRepository instanceRepo;
    private final ApprovalChainActionRepository actionRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InstanceResponse> list() {
        Long orgId = org();
        return instanceRepo.findByOrganisationIdAndIsDeletedFalseOrderByStartedAtDesc(orgId)
            .stream().map(i -> toResponse(i, orgId)).toList();
    }

    @Transactional(readOnly = true)
    public InstanceResponse get(Long id) {
        Long orgId = org();
        ApprovalChainInstance i = loadOrThrow(id);
        return toResponse(i, orgId);
    }

    /**
     * "What approvals are waiting on me right now?" — the dashboard widget.
     *
     * <p>Looks at every in-flight instance, walks its current step, and
     * returns one row per step-definition where:
     * <ul>
     *   <li>the step is at {@code currentStepOrder}, and</li>
     *   <li>the actor either matches via {@code requiredUserId} or
     *       via {@code requiredRole} (we accept any of those names —
     *       the frontend's role catalogue is the authority here), and</li>
     *   <li>no APPROVED action by this user already exists for this
     *       step row.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<PendingDecisionResponse> pendingForUser(Long userId, Set<String> userRoles) {
        Long orgId = org();
        Set<String> rolesUpper = new HashSet<>();
        if (userRoles != null) {
            for (String r : userRoles) if (r != null) rolesUpper.add(r.toUpperCase());
        }

        List<PendingDecisionResponse> out = new ArrayList<>();
        List<ApprovalChainInstance> live = new ArrayList<>();
        live.addAll(instanceRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByStartedAtDesc(orgId, "PENDING"));
        live.addAll(instanceRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByStartedAtDesc(orgId, "IN_PROGRESS"));

        for (ApprovalChainInstance i : live) {
            List<ApprovalChainStep> stepsAtCurrent = stepRepo
                .findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(
                    orgId, i.getTemplateId(), i.getCurrentStepOrder());
            ApprovalChainTemplate t = templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, i.getTemplateId())
                .orElse(null);
            String tplCode = t != null ? t.getTemplateCode() : null;
            String tplEn = t != null ? t.getTitleEn() : null;
            String tplAr = t != null ? t.getTitleAr() : null;

            for (ApprovalChainStep s : stepsAtCurrent) {
                if (!matchesActor(s, userId, rolesUpper)) continue;
                if (hasActorApproved(orgId, i.getId(), s.getId(), userId)) continue;
                out.add(new PendingDecisionResponse(
                    i.getId(), i.getTemplateId(), tplCode, tplEn, tplAr,
                    s.getStepOrder(), s.getId(), s.getTitleEn(), s.getTitleAr(),
                    s.getStepType(), s.getRequiredRole(), s.getSlaHours(),
                    i.getStartedAt(), i.getUpdatedAt() != null ? i.getUpdatedAt() : i.getStartedAt(),
                    i.getOriginatorUserId()
                ));
            }
        }
        return out;
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public InstanceResponse startChain(InstanceStartRequest req) {
        Long orgId = org();
        if (req.sourceWorkflowSubmissionId() == null
            && (req.sourceEntityType() == null || req.sourceEntityId() == null)) {
            throw new IllegalArgumentException(
                "Either sourceWorkflowSubmissionId OR (sourceEntityType + sourceEntityId) is required");
        }
        ApprovalChainTemplate t = templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.templateId())
            .orElseThrow(() -> new EntityNotFoundException("Approval-chain template not found: " + req.templateId()));
        long stepCount = stepRepo.countByOrganisationIdAndTemplateIdAndIsDeletedFalse(orgId, t.getId());
        if (stepCount == 0) {
            throw new IllegalArgumentException("Template " + t.getTemplateCode() + " has no steps configured");
        }

        ApprovalChainInstance i = new ApprovalChainInstance();
        i.setOrganisationId(orgId);
        i.setTemplateId(t.getId());
        i.setSourceWorkflowSubmissionId(req.sourceWorkflowSubmissionId());
        i.setSourceEntityType(req.sourceEntityType());
        i.setSourceEntityId(req.sourceEntityId());
        i.setStatus("IN_PROGRESS"); // Started chains immediately move from PENDING -> IN_PROGRESS.
        i.setStartedAt(LocalDateTime.now());
        i.setCurrentStepOrder(1);
        i.setOriginatorUserId(req.originatorUserId());
        i.setTitleEn(req.titleEn() != null ? req.titleEn() : t.getTitleEn());
        i.setTitleAr(req.titleAr() != null ? req.titleAr() : t.getTitleAr());
        ApprovalChainInstance saved = instanceRepo.save(i);

        audit.log("CREATE", "ApprovalChainInstance", saved.getId(),
            "Approval-chain instance started: template=" + t.getTemplateCode()
                + (saved.getSourceWorkflowSubmissionId() != null
                    ? ", submission=" + saved.getSourceWorkflowSubmissionId()
                    : ", source=" + saved.getSourceEntityType() + "#" + saved.getSourceEntityId()));
        emit(saved, t, "APPROVAL_CHAIN_STARTED", null);

        return toResponse(saved, orgId);
    }

    /**
     * Record an approval action. SECURITY: the acting identity ({@code actingUserId}
     * + {@code actingRoles}) MUST be derived from the authenticated principal by the
     * controller — never from the request body. {@code req.actorUserId()} is ignored.
     * The acting principal must satisfy the target step's required user/role, else a
     * {@link AccessDeniedException} (403) is thrown.
     */
    @Transactional
    public InstanceResponse recordAction(Long instanceId, InstanceActionRequest req,
                                         Long actingUserId, Set<String> actingRoles) {
        Long orgId = org();
        ApprovalChainInstance i = loadOrThrow(instanceId);
        if (TERMINAL_STATUSES.contains(i.getStatus())) {
            throw new IllegalTransitionException(i.getStatus(), "ACTION", TERMINAL_STATUSES);
        }
        if (!Objects.equals(i.getCurrentStepOrder(), req.stepOrder())) {
            throw new IllegalArgumentException(
                "Cannot action step " + req.stepOrder()
                    + " — current step is " + i.getCurrentStepOrder());
        }

        // Find the specific step row this actor satisfies at the current
        // step_order. If multiple rows match (parallel rung), we accept
        // any one — the actor signs against that specific row.
        List<ApprovalChainStep> stepsAtOrder = stepRepo
            .findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(
                orgId, i.getTemplateId(), req.stepOrder());
        if (stepsAtOrder.isEmpty()) {
            throw new IllegalArgumentException(
                "Template " + i.getTemplateId() + " has no step at order " + req.stepOrder());
        }

        // Pick the step row the AUTHENTICATED actor satisfies. Throws 403 if
        // the principal is neither the step's required user nor holder of its
        // required role (no silent rows.get(0) fallback for restricted steps).
        ApprovalChainStep target = resolveStepForActor(stepsAtOrder, actingUserId, actingRoles);

        if ("DELEGATED".equals(req.action()) && !Boolean.TRUE.equals(target.getCanDelegate())) {
            throw new IllegalArgumentException(
                "Step " + target.getTitleEn() + " does not allow delegation");
        }
        if ("DELEGATED".equals(req.action()) && req.delegatedToUserId() == null) {
            throw new IllegalArgumentException("DELEGATED action requires delegatedToUserId");
        }

        ApprovalChainAction a = new ApprovalChainAction();
        a.setOrganisationId(orgId);
        a.setInstanceId(i.getId());
        a.setStepOrder(req.stepOrder());
        a.setStepId(target.getId());
        a.setActorUserId(actingUserId);
        a.setAction(req.action());
        a.setDecidedAt(LocalDateTime.now());
        a.setCommentEn(req.commentEn());
        a.setCommentAr(req.commentAr());
        a.setDelegatedToUserId(req.delegatedToUserId());
        actionRepo.save(a);

        // ── Audit + emit per-action event ──────────────────────────
        audit.log("ACTION", "ApprovalChainInstance", i.getId(),
            "Step " + req.stepOrder() + " action=" + req.action()
                + " by user=" + actingUserId
                + " on step '" + target.getTitleEn() + "'");

        if ("DELEGATED".equals(req.action())) {
            emit(i, null, "APPROVAL_CHAIN_DELEGATED",
                Map.of("delegatedToUserId", String.valueOf(req.delegatedToUserId())));
        }

        // ── Evaluate state machine ─────────────────────────────────
        String previousStatus = i.getStatus();

        if ("REJECTED".equals(req.action())) {
            i.setStatus("REJECTED");
            i.setCompletedAt(LocalDateTime.now());
            i.setUpdatedAt(LocalDateTime.now());
            instanceRepo.save(i);
            emit(i, null, "APPROVAL_CHAIN_REJECTED", Map.of("rejectedAtStep", String.valueOf(req.stepOrder())));
            return toResponse(i, orgId);
        }

        // For APPROVED actions, check whether the rung is complete.
        if ("APPROVED".equals(req.action())) {
            boolean rungSatisfied = isStepOrderSatisfied(orgId, i, req.stepOrder(), stepsAtOrder);
            if (rungSatisfied) {
                // Step completed — emit event, then either advance or finalise.
                emit(i, null, "APPROVAL_CHAIN_STEP_COMPLETED",
                    Map.of("completedStepOrder", String.valueOf(req.stepOrder())));

                Integer nextOrder = nextStepOrder(orgId, i.getTemplateId(), req.stepOrder());
                if (nextOrder == null) {
                    // No more rungs — APPROVED.
                    i.setStatus("APPROVED");
                    i.setCompletedAt(LocalDateTime.now());
                    i.setUpdatedAt(LocalDateTime.now());
                    instanceRepo.save(i);
                    emit(i, null, "APPROVAL_CHAIN_COMPLETED", null);
                } else {
                    i.setCurrentStepOrder(nextOrder);
                    i.setUpdatedAt(LocalDateTime.now());
                    instanceRepo.save(i);
                }
            } else {
                // Rung not yet satisfied — record progress only.
                i.setUpdatedAt(LocalDateTime.now());
                instanceRepo.save(i);
            }
        } else {
            // REQUESTED_CHANGES / RECUSED / DELEGATED — no state advance.
            i.setUpdatedAt(LocalDateTime.now());
            instanceRepo.save(i);
        }
        return toResponse(i, orgId);
    }

    /**
     * Withdraw an in-flight chain. SECURITY: {@code actingUserId} MUST be the
     * authenticated principal id (not a body field). Only the originator may
     * withdraw; any other principal gets a 403.
     */
    @Transactional
    public InstanceResponse withdraw(Long instanceId, InstanceWithdrawRequest req, Long actingUserId) {
        Long orgId = org();
        ApprovalChainInstance i = loadOrThrow(instanceId);
        if (TERMINAL_STATUSES.contains(i.getStatus())) {
            throw new IllegalTransitionException(i.getStatus(), "WITHDRAWN", TERMINAL_STATUSES);
        }
        if (!Objects.equals(i.getOriginatorUserId(), actingUserId)) {
            throw new AccessDeniedException("Only the originator can withdraw the chain");
        }
        i.setStatus("WITHDRAWN");
        i.setCompletedAt(LocalDateTime.now());
        i.setUpdatedAt(LocalDateTime.now());
        instanceRepo.save(i);
        audit.log("WITHDRAW", "ApprovalChainInstance", i.getId(),
            "Approval-chain withdrawn by originator " + actingUserId
                + (req.reasonEn() != null ? " — " + req.reasonEn() : ""));
        emit(i, null, "APPROVAL_CHAIN_WITHDRAWN", null);
        return toResponse(i, orgId);
    }

    /**
     * Mark an in-flight instance as needing attention because its
     * current step has been outstanding past the configured
     * {@code slaHours}. The scheduler discovers these and feeds the IDs
     * here one at a time so each escalation is its own audit row and
     * its own F8 event.
     *
     * <p>Returns {@code true} when the instance qualified and an
     * escalation event fired; {@code false} when it was already
     * terminal or no SLA was configured.
     */
    @Transactional
    public boolean escalate(Long instanceId) {
        Long orgId = org();
        ApprovalChainInstance i = instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, instanceId).orElse(null);
        if (i == null) return false;
        if (TERMINAL_STATUSES.contains(i.getStatus())) return false;

        // Walk the current step's row(s) to find an SLA breach.
        List<ApprovalChainStep> stepsAtOrder = stepRepo
            .findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(
                orgId, i.getTemplateId(), i.getCurrentStepOrder());

        LocalDateTime currentSince = i.getUpdatedAt() != null ? i.getUpdatedAt() : i.getStartedAt();
        for (ApprovalChainStep s : stepsAtOrder) {
            if (s.getSlaHours() == null) continue;
            LocalDateTime deadline = currentSince.plusHours(s.getSlaHours());
            if (LocalDateTime.now().isAfter(deadline)) {
                // Don't re-fire if we already escalated at this step.
                List<ApprovalChainAction> existing = actionRepo
                    .findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(
                        orgId, i.getId(), i.getCurrentStepOrder());
                boolean alreadyEscalated = existing.stream()
                    .anyMatch(a -> "ESCALATION_NOTE".equals(a.getAction()));
                if (alreadyEscalated) continue;

                audit.log("ESCALATE", "ApprovalChainInstance", i.getId(),
                    "Step " + i.getCurrentStepOrder() + " SLA breached ("
                        + s.getSlaHours() + "h) — escalating to " + s.getEscalationRole());
                emit(i, null, "APPROVAL_CHAIN_ESCALATED",
                    Map.of("escalationRole", String.valueOf(s.getEscalationRole()),
                           "breachedStepOrder", String.valueOf(i.getCurrentStepOrder())));
                // Record the escalation as a synthetic action row so we
                // don't escalate the same step twice. action="ESCALATION_NOTE"
                // is a marker, not a state-changing action.
                ApprovalChainAction note = new ApprovalChainAction();
                note.setOrganisationId(orgId);
                note.setInstanceId(i.getId());
                note.setStepOrder(i.getCurrentStepOrder());
                note.setStepId(s.getId());
                note.setActorUserId(0L); // 0 = system actor
                note.setAction("ESCALATION_NOTE");
                note.setDecidedAt(LocalDateTime.now());
                note.setCommentEn("Automatic SLA escalation to role " + s.getEscalationRole());
                actionRepo.save(note);

                i.setUpdatedAt(LocalDateTime.now());
                instanceRepo.save(i);
                return true;
            }
        }
        return false;
    }

    // ─── Advancement helpers ────────────────────────────────────────

    /**
     * True if every mandatory step row at {@code stepOrder} has at
     * least one APPROVED action.
     */
    boolean isStepOrderSatisfied(Long orgId, ApprovalChainInstance i, Integer stepOrder,
                                  List<ApprovalChainStep> rowsAtOrder) {
        List<ApprovalChainAction> actionsAtOrder = actionRepo
            .findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(orgId, i.getId(), stepOrder);
        for (ApprovalChainStep s : rowsAtOrder) {
            if (!Boolean.TRUE.equals(s.getIsMandatory())) continue;
            boolean approved = actionsAtOrder.stream()
                .anyMatch(a -> Objects.equals(a.getStepId(), s.getId()) && "APPROVED".equals(a.getAction()));
            if (!approved) return false;
        }
        return true;
    }

    /**
     * Returns the next step_order present in the template after
     * {@code current}, or {@code null} if {@code current} was the last.
     */
    Integer nextStepOrder(Long orgId, Long templateId, Integer current) {
        List<ApprovalChainStep> all = stepRepo
            .findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(orgId, templateId);
        Integer next = null;
        for (ApprovalChainStep s : all) {
            if (s.getStepOrder() > current && (next == null || s.getStepOrder() < next)) {
                next = s.getStepOrder();
            }
        }
        return next;
    }

    /**
     * Pick the step row the AUTHENTICATED actor is authorised to sign.
     *
     * <p>SECURITY (SEC-IAM-001): a restricted step (one that declares a
     * {@code requiredUserId} or {@code requiredRole}) may only be actioned by a
     * principal that matches it. If no restricted row matches the principal, and
     * no open (unconstrained) row exists, a {@link AccessDeniedException} (403) is
     * thrown — there is no {@code rows.get(0)} fallback that previously let any
     * authenticated user forge an approval.
     */
    ApprovalChainStep resolveStepForActor(List<ApprovalChainStep> rows, Long actingUserId, Set<String> actingRoles) {
        // 1) A row the principal is explicitly authorised for (user id or role).
        for (ApprovalChainStep s : rows) {
            if (matchesActor(s, actingUserId, actingRoles)) {
                return s;
            }
        }
        // 2) An open row that imposes no actor/role constraint — any authenticated
        //    user may sign these (preserves legitimately-unrestricted steps).
        for (ApprovalChainStep s : rows) {
            if (s.getRequiredUserId() == null && s.getRequiredRole() == null) {
                return s;
            }
        }
        throw new AccessDeniedException(
            "Not an authorised approver for step "
                + (rows.isEmpty() ? "?" : String.valueOf(rows.get(0).getStepOrder())));
    }

    /**
     * True when the step's matcher would accept this actor.
     */
    static boolean matchesActor(ApprovalChainStep s, Long actorUserId, Set<String> rolesUpper) {
        if (s.getRequiredUserId() != null && Objects.equals(s.getRequiredUserId(), actorUserId)) {
            return true;
        }
        if (s.getRequiredRole() != null && rolesUpper.contains(s.getRequiredRole().toUpperCase())) {
            return true;
        }
        return false;
    }

    boolean hasActorApproved(Long orgId, Long instanceId, Long stepId, Long actorUserId) {
        return actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(orgId, instanceId)
            .stream().anyMatch(a -> Objects.equals(a.getStepId(), stepId)
                                  && Objects.equals(a.getActorUserId(), actorUserId)
                                  && "APPROVED".equals(a.getAction()));
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private ApprovalChainInstance loadOrThrow(Long id) {
        return instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Approval-chain instance not found: " + id));
    }

    InstanceResponse toResponse(ApprovalChainInstance i, Long orgId) {
        ApprovalChainTemplate t = templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, i.getTemplateId())
            .orElse(null);
        List<ApprovalChainAction> actions = actionRepo
            .findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(orgId, i.getId());
        List<ApprovalChainStep> currentRows = stepRepo
            .findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(orgId, i.getTemplateId(), i.getCurrentStepOrder());
        List<ActionResponse> actionRows = actions.stream().map(a -> new ActionResponse(
            a.getId(), a.getInstanceId(), a.getStepOrder(), a.getStepId(),
            a.getActorUserId(), a.getAction(), a.getDecidedAt(),
            a.getCommentEn(), a.getCommentAr(), a.getDelegatedToUserId()
        )).toList();
        List<StepResponse> currentDefs = currentRows.stream()
            .map(ApprovalChainTemplateService::toStepResponse).toList();
        return new InstanceResponse(
            i.getId(), i.getExternalId(), i.getOrganisationId(),
            i.getTemplateId(), t != null ? t.getTemplateCode() : null,
            i.getSourceWorkflowSubmissionId(), i.getSourceEntityType(), i.getSourceEntityId(),
            i.getStatus(), i.getStartedAt(), i.getCompletedAt(), i.getCurrentStepOrder(),
            i.getOriginatorUserId(), i.getTitleEn(), i.getTitleAr(),
            actionRows, currentDefs, i.getCreatedAt(), i.getUpdatedAt()
        );
    }

    void emit(ApprovalChainInstance i, ApprovalChainTemplate maybeTemplate, String trigger, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", String.valueOf(i.getId()));
        payload.put("templateId", String.valueOf(i.getTemplateId()));
        if (maybeTemplate != null) {
            payload.put("templateCode", maybeTemplate.getTemplateCode());
            payload.put("appliesToModule", String.valueOf(maybeTemplate.getAppliesToModule()));
        }
        payload.put("status", i.getStatus());
        payload.put("currentStepOrder", String.valueOf(i.getCurrentStepOrder()));
        payload.put("originatorUserId", String.valueOf(i.getOriginatorUserId()));
        payload.put("sourceWorkflowSubmissionId", String.valueOf(i.getSourceWorkflowSubmissionId()));
        payload.put("sourceEntityType", String.valueOf(i.getSourceEntityType()));
        payload.put("sourceEntityId", String.valueOf(i.getSourceEntityId()));
        if (extra != null) payload.putAll(extra);
        publisher.publishEvent(new DomainEvent(
            i.getOrganisationId(), trigger, "ApprovalChainInstance", i.getId(),
            "approval-chain:" + i.getId(), payload));
    }
}
