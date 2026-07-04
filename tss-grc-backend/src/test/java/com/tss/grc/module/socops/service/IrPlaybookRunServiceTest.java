package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.socops.dto.IrPlaybookRunDetail;
import com.tss.grc.module.socops.entity.IrPlaybook;
import com.tss.grc.module.socops.entity.IrPlaybookRun;
import com.tss.grc.module.socops.entity.IrPlaybookRunTask;
import com.tss.grc.module.socops.entity.IrPlaybookStep;
import com.tss.grc.module.socops.repository.IrPlaybookRepository;
import com.tss.grc.module.socops.repository.IrPlaybookRunRepository;
import com.tss.grc.module.socops.repository.IrPlaybookRunTaskRepository;
import com.tss.grc.module.socops.repository.IrPlaybookStepRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IrPlaybookRunService (epic SOC-B) — locks the invoke→run→tasks instantiation
 * and the complete→advance→COMPLETED progression.
 *
 * Invoking a playbook must create one clocked, owner-assigned task per step
 * (owner falls back to a default, due is cumulative from run start); completing
 * the last task flips the run to COMPLETED.
 */
@ExtendWith(MockitoExtension.class)
class IrPlaybookRunServiceTest {

    private static final Long ORG_ID  = 1L;
    private static final Long PB_ID   = 5L;
    private static final Long RUN_ID  = 77L;
    private static final Long TASK_ID = 88L;

    @Mock private IrPlaybookRepository        playbooks;
    @Mock private IrPlaybookStepRepository    steps;
    @Mock private IrPlaybookRunRepository     runs;
    @Mock private IrPlaybookRunTaskRepository runTasks;
    @Mock private AuditLogService             audit;
    @Mock private ApplicationEventPublisher   publisher;
    @Mock private NotificationService         notifications;
    @InjectMocks private IrPlaybookRunService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static IrPlaybook playbook() {
        IrPlaybook p = new IrPlaybook();
        p.setId(PB_ID); p.setOrganisationId(ORG_ID); p.setPlaybookCode("IR-TEST-01");
        p.setTitleEn("Test playbook"); p.setCategory("PHISHING");
        p.setTargetMttrMinutes(240); p.setInvocationCount(3);
        return p;
    }

    private static IrPlaybookStep step(long id, int order, String role, Integer minutes) {
        IrPlaybookStep s = new IrPlaybookStep();
        s.setId(id); s.setOrganisationId(ORG_ID); s.setPlaybookId(PB_ID);
        s.setStepOrder(order); s.setPhase("CONTAIN"); s.setTitleEn("Step " + order);
        s.setResponsibleRole(role); s.setEstimatedMinutes(minutes); s.setIsCritical(order == 1);
        return s;
    }

    @Test
    void invoke_instantiatesRunPlusOneClockedTaskPerStep() {
        when(playbooks.findByIdAndOrganisationId(PB_ID, ORG_ID)).thenReturn(Optional.of(playbook()));
        when(steps.findByOrganisationIdAndPlaybookIdOrderByStepOrderAsc(ORG_ID, PB_ID))
            .thenReturn(List.of(
                step(101, 1, "IR_LEAD", 30),
                step(102, 2, null,      null),   // owner + minutes fall back to defaults
                step(103, 3, "CISO",    45)));
        when(runs.save(any(IrPlaybookRun.class))).thenAnswer(inv -> {
            IrPlaybookRun r = inv.getArgument(0); r.setId(RUN_ID); return r;
        });
        when(runTasks.save(any(IrPlaybookRunTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(playbooks.save(any(IrPlaybook.class))).thenAnswer(inv -> inv.getArgument(0));

        IrPlaybookRunDetail detail = service.invoke(PB_ID, null, "kickoff");

        // run header
        assertThat(detail.run().getStatus()).isEqualTo("RUNNING");
        assertThat(detail.run().getTotalTasks()).isEqualTo(3);
        assertThat(detail.run().getCompletedTasks()).isZero();
        assertThat(detail.run().getPlaybookCode()).isEqualTo("IR-TEST-01");

        // one task per step, each owned + clocked (due cumulative & strictly increasing)
        ArgumentCaptor<IrPlaybookRunTask> cap = ArgumentCaptor.forClass(IrPlaybookRunTask.class);
        verify(runTasks, times(3)).save(cap.capture());
        List<IrPlaybookRunTask> saved = cap.getAllValues();
        assertThat(saved).hasSize(3);
        assertThat(saved).allSatisfy(t -> {
            assertThat(t.getOwnerRole()).isNotBlank();      // fallback keeps every task assigned
            assertThat(t.getDueAt()).isNotNull();           // fallback keeps every task clocked
            assertThat(t.getStatus()).isEqualTo("PENDING");
        });
        assertThat(saved.get(1).getOwnerRole()).isEqualTo("SOC Analyst");   // null role → default
        assertThat(saved.get(0).getDueAt()).isBefore(saved.get(1).getDueAt());
        assertThat(saved.get(1).getDueAt()).isBefore(saved.get(2).getDueAt());

        // legacy invocation counter still bumped
        assertThat(detail.tasks()).hasSize(3);
        verify(playbooks).save(any(IrPlaybook.class));
    }

    @Test
    void invoke_playbookWithNoSteps_throws() {
        when(playbooks.findByIdAndOrganisationId(PB_ID, ORG_ID)).thenReturn(Optional.of(playbook()));
        when(steps.findByOrganisationIdAndPlaybookIdOrderByStepOrderAsc(ORG_ID, PB_ID))
            .thenReturn(List.of());
        assertThatThrownBy(() -> service.invoke(PB_ID, null, null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completeTask_lastTask_flipsRunToCompleted() {
        IrPlaybookRun run = new IrPlaybookRun();
        run.setId(RUN_ID); run.setOrganisationId(ORG_ID); run.setStatus("RUNNING");
        run.setTotalTasks(2); run.setCompletedTasks(1); run.setPlaybookCode("IR-TEST-01");

        IrPlaybookRunTask task = new IrPlaybookRunTask();
        task.setId(TASK_ID); task.setOrganisationId(ORG_ID); task.setRunId(RUN_ID);
        task.setStepOrder(2); task.setPhase("RECOVER"); task.setTitleEn("Last step");
        task.setStatus("PENDING");

        when(runs.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runTasks.findByIdAndOrganisationId(TASK_ID, ORG_ID)).thenReturn(Optional.of(task));
        when(runTasks.save(any(IrPlaybookRunTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(runTasks.countByOrganisationIdAndRunIdAndStatus(ORG_ID, RUN_ID, "DONE")).thenReturn(2L);
        when(runs.save(any(IrPlaybookRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(runTasks.findByOrganisationIdAndRunIdOrderByStepOrderAsc(ORG_ID, RUN_ID))
            .thenReturn(List.of(task));

        IrPlaybookRunDetail detail = service.completeTask(RUN_ID, TASK_ID, "handled");

        assertThat(task.getStatus()).isEqualTo("DONE");
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(detail.run().getStatus()).isEqualTo("COMPLETED");
        assertThat(detail.run().getCompletedTasks()).isEqualTo(2);
        assertThat(detail.run().getCompletedAt()).isNotNull();
        // both a run-completed audit + F8 event fire (typed matcher disambiguates
        // the publishEvent(Object) vs publishEvent(ApplicationEvent) overload)
        verify(publisher).publishEvent(any(DomainEvent.class));
        verify(notifications).broadcastToRole(eq(ORG_ID), any(), any(), any(), any(), any(), any(), eq(RUN_ID));
    }

    @Test
    void completeTask_taskNotInRun_throws() {
        IrPlaybookRun run = new IrPlaybookRun();
        run.setId(RUN_ID); run.setOrganisationId(ORG_ID); run.setStatus("RUNNING"); run.setTotalTasks(2);
        IrPlaybookRunTask task = new IrPlaybookRunTask();
        task.setId(TASK_ID); task.setOrganisationId(ORG_ID); task.setRunId(999L);   // different run

        when(runs.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.of(run));
        when(runTasks.findByIdAndOrganisationId(TASK_ID, ORG_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.completeTask(RUN_ID, TASK_ID, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getRunDetail_unknownRun_throws() {
        when(runs.findByIdAndOrganisationId(RUN_ID, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRunDetail(RUN_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
