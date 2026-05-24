package com.tss.grc.module.workflow.approval_chains.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceActionRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceResponse;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceStartRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.InstanceWithdrawRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.PendingDecisionResponse;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainAction;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainInstance;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainStep;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainTemplate;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainActionRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainInstanceRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainStepRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link ApprovalChainInstanceService} unit tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>Start instance + first-step pending</li>
 *   <li>Serial advancement (CAPA chain) — each APPROVE moves step+1</li>
 *   <li>Parallel completion — both CISO+DPO required before advancing</li>
 *   <li>REJECTED terminates immediately</li>
 *   <li>Mandatory enforcement — non-mandatory step doesn't block</li>
 *   <li>Delegation records the hand-off but does NOT advance</li>
 *   <li>Withdraw — originator only</li>
 *   <li>Escalate finds stale steps and flips to escalation_role</li>
 *   <li>F8 event emission for each canonical trigger</li>
 *   <li>Tenant isolation — every read filters by org</li>
 *   <li>Pending-for-me returns rows for matched actor</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalChainInstanceServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long TPL_ID = 1L;
    private static final Long INSTANCE_ID = 50L;

    @Mock private ApprovalChainTemplateRepository templateRepo;
    @Mock private ApprovalChainStepRepository stepRepo;
    @Mock private ApprovalChainInstanceRepository instanceRepo;
    @Mock private ApprovalChainActionRepository actionRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private ApprovalChainInstanceService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new ApprovalChainInstanceService(templateRepo, stepRepo, instanceRepo, actionRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static ApprovalChainTemplate template() {
        ApprovalChainTemplate t = new ApprovalChainTemplate();
        t.setId(TPL_ID);
        t.setOrganisationId(ORG_ID);
        t.setTemplateCode("CHAIN-CAPA-CLOSURE");
        t.setTitleEn("CAPA Closure");
        t.setTitleAr("إغلاق الإجراءات التصحيحية");
        t.setAppliesToModule("COMPLIANCE");
        return t;
    }

    private static ApprovalChainStep step(long id, int order, String type, String role, boolean mandatory) {
        ApprovalChainStep s = new ApprovalChainStep();
        s.setId(id);
        s.setOrganisationId(ORG_ID);
        s.setTemplateId(TPL_ID);
        s.setStepOrder(order);
        s.setStepType(type);
        s.setTitleEn("Step " + order);
        s.setTitleAr("الخطوة " + order);
        s.setRequiredRole(role);
        s.setIsMandatory(mandatory);
        s.setCanDelegate(true);
        s.setSlaHours(48);
        s.setEscalationRole("PLATFORM_ADMIN");
        return s;
    }

    private static ApprovalChainInstance instance(String status, int currentOrder) {
        ApprovalChainInstance i = new ApprovalChainInstance();
        i.setId(INSTANCE_ID);
        i.setExternalId(UUID.randomUUID());
        i.setOrganisationId(ORG_ID);
        i.setTemplateId(TPL_ID);
        i.setStatus(status);
        i.setStartedAt(LocalDateTime.now().minusHours(1));
        i.setCurrentStepOrder(currentOrder);
        i.setOriginatorUserId(99L);
        i.setSourceWorkflowSubmissionId(900L);
        return i;
    }

    private static InstanceActionRequest action(int order, long actor, String act) {
        return new InstanceActionRequest(order, actor, act, "ok", "حسناً", null);
    }

    // ─── Start ──────────────────────────────────────────────────────

    @Test void startChain_persistsInstanceAndEmitsStartedEvent() {
        InstanceStartRequest req = new InstanceStartRequest(
            TPL_ID, 900L, null, null, 99L, "CAPA closure", "إغلاق CAPA");
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(stepRepo.countByOrganisationIdAndTemplateIdAndIsDeletedFalse(ORG_ID, TPL_ID)).thenReturn(4L);
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> {
            ApprovalChainInstance i = inv.getArgument(0);
            i.setId(INSTANCE_ID);
            return i;
        });

        InstanceResponse out = service.startChain(req);

        assertThat(out.status()).isEqualTo("IN_PROGRESS");
        assertThat(out.currentStepOrder()).isEqualTo(1);

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evCap.capture());
        assertThat(evCap.getValue().triggerEvent()).isEqualTo("APPROVAL_CHAIN_STARTED");
        assertThat(evCap.getValue().entityType()).isEqualTo("ApprovalChainInstance");
        assertThat(evCap.getValue().organisationId()).isEqualTo(ORG_ID);
    }

    @Test void startChain_requiresSomeSource() {
        InstanceStartRequest req = new InstanceStartRequest(
            TPL_ID, null, null, null, 99L, null, null);
        assertThatThrownBy(() -> service.startChain(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("source");
    }

    @Test void startChain_rejectsTemplateWithZeroSteps() {
        InstanceStartRequest req = new InstanceStartRequest(
            TPL_ID, 900L, null, null, 99L, null, null);
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(stepRepo.countByOrganisationIdAndTemplateIdAndIsDeletedFalse(ORG_ID, TPL_ID)).thenReturn(0L);
        assertThatThrownBy(() -> service.startChain(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no steps configured");
    }

    // ─── Record action — serial advance ──────────────────────────────

    @Test void recordAction_serialApprove_advancesToNextStep() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);
        ApprovalChainStep s2 = step(12L, 2, "SERIAL", "DEPARTMENT_HEAD", true);
        ApprovalChainStep s3 = step(13L, 3, "SERIAL", "DPO", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of(s1, s2, s3));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(ORG_ID, INSTANCE_ID, 1))
            .thenReturn(List.of(approvedAction(11L)));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 2))
            .thenReturn(List.of(s2));

        InstanceResponse out = service.recordAction(INSTANCE_ID, action(1, 42L, "APPROVED"));

        assertThat(out.currentStepOrder()).isEqualTo(2);
        assertThat(out.status()).isEqualTo("IN_PROGRESS");
    }

    @Test void recordAction_lastSerialStep_finalisesAsApproved() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 4);
        ApprovalChainStep s4 = step(14L, 4, "SERIAL", "CISO", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 4))
            .thenReturn(List.of(s4));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of(s4));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(ORG_ID, INSTANCE_ID, 4))
            .thenReturn(List.of(approvedAction(14L)));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());

        InstanceResponse out = service.recordAction(INSTANCE_ID, action(4, 42L, "APPROVED"));

        assertThat(out.status()).isEqualTo("APPROVED");
        assertThat(out.completedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evCap.capture());
        assertThat(evCap.getAllValues().stream().map(DomainEvent::triggerEvent))
            .contains("APPROVAL_CHAIN_STEP_COMPLETED", "APPROVAL_CHAIN_COMPLETED");
    }

    // ─── Reject terminates ───────────────────────────────────────────

    @Test void recordAction_rejected_movesToRejectedTerminal() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 2);
        ApprovalChainStep s2 = step(12L, 2, "SERIAL", "DEPARTMENT_HEAD", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 2))
            .thenReturn(List.of(s2));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());

        InstanceResponse out = service.recordAction(INSTANCE_ID, action(2, 42L, "REJECTED"));

        assertThat(out.status()).isEqualTo("REJECTED");
        assertThat(out.completedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evCap.capture());
        assertThat(evCap.getAllValues().stream().map(DomainEvent::triggerEvent))
            .contains("APPROVAL_CHAIN_REJECTED");
    }

    // ─── Parallel completion ─────────────────────────────────────────

    @Test void recordAction_parallel_requiresBothCisoAndDpoBeforeAdvance() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 2);
        ApprovalChainStep ciso = step(21L, 2, "PARALLEL", "CISO", true);
        ApprovalChainStep dpo  = step(22L, 2, "PARALLEL", "DPO",  true);
        ApprovalChainStep s3   = step(33L, 3, "SERIAL",   "CHANGE_MANAGER", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 2))
            .thenReturn(List.of(ciso, dpo));
        // Only CISO has approved so far — DPO row still pending.
        when(actionRepo.findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(ORG_ID, INSTANCE_ID, 2))
            .thenReturn(List.of(approvedActionForStep(21L)));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());

        InstanceResponse out = service.recordAction(INSTANCE_ID, action(2, 42L, "APPROVED"));

        // CISO approved, DPO not yet — chain MUST stay on step 2.
        assertThat(out.currentStepOrder()).isEqualTo(2);
        assertThat(out.status()).isEqualTo("IN_PROGRESS");
    }

    @Test void recordAction_parallel_advancesOnceBothApproved() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 2);
        ApprovalChainStep ciso = step(21L, 2, "PARALLEL", "CISO", true);
        ApprovalChainStep dpo  = step(22L, 2, "PARALLEL", "DPO",  true);
        ApprovalChainStep s3   = step(33L, 3, "SERIAL",   "CHANGE_MANAGER", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 2))
            .thenReturn(List.of(ciso, dpo));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(ORG_ID, INSTANCE_ID, 2))
            .thenReturn(List.of(approvedActionForStep(21L), approvedActionForStep(22L)));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of(ciso, dpo, s3));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 3))
            .thenReturn(List.of(s3));

        InstanceResponse out = service.recordAction(INSTANCE_ID, action(2, 42L, "APPROVED"));

        assertThat(out.currentStepOrder()).isEqualTo(3);
        assertThat(out.status()).isEqualTo("IN_PROGRESS");
    }

    @Test void recordAction_parallel_nonMandatoryDoesNotBlock() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 2);
        ApprovalChainStep ciso  = step(21L, 2, "PARALLEL", "CISO", true);
        ApprovalChainStep dpoOptional = step(22L, 2, "PARALLEL", "DPO", false); // not mandatory
        ApprovalChainStep s3    = step(33L, 3, "SERIAL", "CHANGE_MANAGER", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 2))
            .thenReturn(List.of(ciso, dpoOptional));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(ORG_ID, INSTANCE_ID, 2))
            .thenReturn(List.of(approvedActionForStep(21L))); // only CISO approved
        when(stepRepo.findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of(ciso, dpoOptional, s3));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 3))
            .thenReturn(List.of(s3));

        InstanceResponse out = service.recordAction(INSTANCE_ID, action(2, 42L, "APPROVED"));

        // CISO is mandatory and approved, DPO is non-mandatory — chain advances.
        assertThat(out.currentStepOrder()).isEqualTo(3);
    }

    // ─── Delegation ──────────────────────────────────────────────────

    @Test void recordAction_delegated_recordsHandoffAndDoesNotAdvance() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());

        InstanceActionRequest delReq = new InstanceActionRequest(
            1, 42L, "DELEGATED", "stepping out", "متغيب", 77L);
        InstanceResponse out = service.recordAction(INSTANCE_ID, delReq);

        assertThat(out.currentStepOrder()).isEqualTo(1); // unchanged
        assertThat(out.status()).isEqualTo("IN_PROGRESS");

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evCap.capture());
        assertThat(evCap.getAllValues().stream().map(DomainEvent::triggerEvent))
            .contains("APPROVAL_CHAIN_DELEGATED");
    }

    @Test void recordAction_delegateRequiresFlag() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);
        s1.setCanDelegate(false);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));

        InstanceActionRequest delReq = new InstanceActionRequest(
            1, 42L, "DELEGATED", null, null, 77L);
        assertThatThrownBy(() -> service.recordAction(INSTANCE_ID, delReq))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not allow delegation");
    }

    @Test void recordAction_delegateRequiresTargetUser() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));

        InstanceActionRequest delReq = new InstanceActionRequest(
            1, 42L, "DELEGATED", null, null, null);
        assertThatThrownBy(() -> service.recordAction(INSTANCE_ID, delReq))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("delegatedToUserId");
    }

    // ─── Terminal status rejection ───────────────────────────────────

    @Test void recordAction_alreadyTerminal_throws() {
        ApprovalChainInstance i = instance("APPROVED", 4);
        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        assertThatThrownBy(() -> service.recordAction(INSTANCE_ID, action(4, 42L, "APPROVED")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void recordAction_wrongStepOrder_throws() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        assertThatThrownBy(() -> service.recordAction(INSTANCE_ID, action(3, 42L, "APPROVED")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("current step");
    }

    // ─── Withdraw ────────────────────────────────────────────────────

    @Test void withdraw_byOriginator_terminates() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 2);
        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());

        InstanceResponse out = service.withdraw(INSTANCE_ID,
            new InstanceWithdrawRequest(99L, "no longer needed", "لم يعد ضرورياً"));

        assertThat(out.status()).isEqualTo("WITHDRAWN");
        assertThat(out.completedAt()).isNotNull();
    }

    @Test void withdraw_byNonOriginator_throws() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 2);
        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        assertThatThrownBy(() -> service.withdraw(INSTANCE_ID,
                new InstanceWithdrawRequest(123L, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("originator");
    }

    @Test void withdraw_alreadyTerminal_throws() {
        ApprovalChainInstance i = instance("APPROVED", 4);
        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        assertThatThrownBy(() -> service.withdraw(INSTANCE_ID,
                new InstanceWithdrawRequest(99L, null, null)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Escalate ────────────────────────────────────────────────────

    @Test void escalate_breachedStep_firesEscalatedEventAndNote() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        i.setStartedAt(LocalDateTime.now().minusHours(50)); // older than 48h SLA
        i.setUpdatedAt(LocalDateTime.now().minusHours(50));
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(ORG_ID, INSTANCE_ID, 1))
            .thenReturn(new ArrayList<>());
        when(instanceRepo.save(any(ApprovalChainInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean fired = service.escalate(INSTANCE_ID);

        assertThat(fired).isTrue();
        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evCap.capture());
        assertThat(evCap.getValue().triggerEvent()).isEqualTo("APPROVAL_CHAIN_ESCALATED");
        // A synthetic ESCALATION_NOTE action was recorded for idempotency.
        verify(actionRepo).save(any(ApprovalChainAction.class));
    }

    @Test void escalate_alreadyTerminal_isNoop() {
        ApprovalChainInstance i = instance("APPROVED", 4);
        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        boolean fired = service.escalate(INSTANCE_ID);
        assertThat(fired).isFalse();
    }

    @Test void escalate_freshInstance_doesNotFire() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        i.setStartedAt(LocalDateTime.now().minusMinutes(5)); // way under SLA
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);

        when(instanceRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, INSTANCE_ID))
            .thenReturn(Optional.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));

        boolean fired = service.escalate(INSTANCE_ID);
        assertThat(fired).isFalse();
    }

    // ─── Pending-for-me ──────────────────────────────────────────────

    @Test void pendingForUser_returnsRowsForMatchingRole() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);

        when(instanceRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByStartedAtDesc(ORG_ID, "PENDING"))
            .thenReturn(List.of());
        when(instanceRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByStartedAtDesc(ORG_ID, "IN_PROGRESS"))
            .thenReturn(List.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of());

        List<PendingDecisionResponse> out = service.pendingForUser(42L, Set.of("RISK_OWNER", "DEPARTMENT_HEAD"));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).stepOrder()).isEqualTo(1);
        assertThat(out.get(0).requiredRole()).isEqualTo("RISK_OWNER");
    }

    @Test void pendingForUser_skipsAlreadyActioned() {
        ApprovalChainInstance i = instance("IN_PROGRESS", 1);
        ApprovalChainStep s1 = step(11L, 1, "SERIAL", "RISK_OWNER", true);

        when(instanceRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByStartedAtDesc(ORG_ID, "PENDING"))
            .thenReturn(List.of());
        when(instanceRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByStartedAtDesc(ORG_ID, "IN_PROGRESS"))
            .thenReturn(List.of(i));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(ORG_ID, TPL_ID, 1))
            .thenReturn(List.of(s1));
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, TPL_ID))
            .thenReturn(Optional.of(template()));
        ApprovalChainAction prior = new ApprovalChainAction();
        prior.setStepId(11L);
        prior.setActorUserId(42L);
        prior.setAction("APPROVED");
        when(actionRepo.findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(ORG_ID, INSTANCE_ID))
            .thenReturn(List.of(prior));

        List<PendingDecisionResponse> out = service.pendingForUser(42L, Set.of("RISK_OWNER"));
        assertThat(out).isEmpty();
    }

    // ─── Tenant isolation ───────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(instanceRepo.findByOrganisationIdAndIsDeletedFalseOrderByStartedAtDesc(ORG_ID)).thenReturn(List.of());
        service.list();
        verify(instanceRepo, times(1)).findByOrganisationIdAndIsDeletedFalseOrderByStartedAtDesc(ORG_ID);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private static ApprovalChainAction approvedAction(Long stepId) {
        return approvedActionForStep(stepId);
    }

    private static ApprovalChainAction approvedActionForStep(Long stepId) {
        ApprovalChainAction a = new ApprovalChainAction();
        a.setStepId(stepId);
        a.setAction("APPROVED");
        a.setOrganisationId(ORG_ID);
        a.setInstanceId(INSTANCE_ID);
        a.setActorUserId(42L);
        a.setDecidedAt(LocalDateTime.now());
        return a;
    }
}
