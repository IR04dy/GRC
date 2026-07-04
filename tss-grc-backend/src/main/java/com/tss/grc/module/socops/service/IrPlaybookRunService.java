package com.tss.grc.module.socops.service;

import com.tss.grc.common.security.CurrentActor;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Execution engine for IR playbooks (epic SOC-B).
 *
 * <p>Before SOC-B, invoking a playbook only incremented a counter — it created no
 * work. This service turns an invocation into a real, trackable execution: a
 * {@link IrPlaybookRun} header plus one clocked, owner-assigned
 * {@link IrPlaybookRunTask} per step. Analysts complete tasks; the run advances
 * and flips to {@code COMPLETED} once the last task is done.
 *
 * <p>Run/task content is snapshotted from the template at invocation time so later
 * edits to the playbook don't rewrite operating history. Kept separate from
 * {@link IrPlaybookService} (which owns the template CRUD) to keep each service
 * cohesive.
 */
@Service
@RequiredArgsConstructor
public class IrPlaybookRunService {

    /** Fallback per-step duration when a step has no estimate — keeps every task clocked. */
    private static final int    DEFAULT_STEP_MINUTES = 60;
    /** Fallback owner when a step names no responsible role — keeps every task assigned. */
    private static final String DEFAULT_OWNER_ROLE   = "SOC Analyst";
    /** Role that gets the "run started / run completed" broadcast (ties to SOC-A seeding). */
    private static final String NOTIFY_ROLE          = "SECURITY_ANALYST";

    private final IrPlaybookRepository          playbooks;
    private final IrPlaybookStepRepository      steps;
    private final IrPlaybookRunRepository       runs;
    private final IrPlaybookRunTaskRepository   runTasks;
    private final AuditLogService               audit;
    private final ApplicationEventPublisher     publisher;
    private final NotificationService           notifications;

    // ── Reads ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<IrPlaybookRun> listRuns() {
        return runs.findByOrganisationIdOrderByStartedAtDesc(TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public List<IrPlaybookRun> runsOf(Long playbookId) {
        return runs.findByOrganisationIdAndPlaybookIdOrderByStartedAtDesc(
            TenantContext.getCurrentTenant(), playbookId);
    }

    @Transactional(readOnly = true)
    public IrPlaybookRunDetail getRunDetail(Long runId) {
        Long org = TenantContext.getCurrentTenant();
        IrPlaybookRun run = runs.findByIdAndOrganisationId(runId, org)
            .orElseThrow(() -> new EntityNotFoundException("IrPlaybookRun " + runId));
        return new IrPlaybookRunDetail(run, tasksOf(org, runId));
    }

    // ── Invoke: instantiate a run + one clocked task per step ─────────────

    @Transactional
    public IrPlaybookRunDetail invoke(Long playbookId, Long incidentId, String note) {
        Long org = TenantContext.getCurrentTenant();
        IrPlaybook pb = playbooks.findByIdAndOrganisationId(playbookId, org)
            .orElseThrow(() -> new EntityNotFoundException("IrPlaybook " + playbookId));
        List<IrPlaybookStep> pbSteps =
            steps.findByOrganisationIdAndPlaybookIdOrderByStepOrderAsc(org, playbookId);
        if (pbSteps.isEmpty()) {
            throw new IllegalStateException("Playbook " + playbookId + " has no steps to run");
        }

        LocalDateTime now = LocalDateTime.now();
        IrPlaybookRun run = new IrPlaybookRun();
        run.setOrganisationId(org);
        run.setPlaybookId(pb.getId());
        run.setPlaybookCode(pb.getPlaybookCode());
        run.setTitleEn(pb.getTitleEn());
        run.setTitleAr(pb.getTitleAr());
        run.setStatus("RUNNING");
        run.setIncidentId(incidentId);
        run.setInvokedBy(CurrentActor.username());
        run.setStartedAt(now);
        run.setTotalTasks(pbSteps.size());
        run.setCompletedTasks(0);
        run.setTargetMttrMinutes(pb.getTargetMttrMinutes());
        if (note != null && !note.isBlank()) run.setNotes(note);
        IrPlaybookRun savedRun = runs.save(run);

        int cumulativeMinutes = 0;
        List<IrPlaybookRunTask> created = new ArrayList<>();
        for (IrPlaybookStep s : pbSteps) {
            int mins = (s.getEstimatedMinutes() != null && s.getEstimatedMinutes() > 0)
                ? s.getEstimatedMinutes() : DEFAULT_STEP_MINUTES;
            cumulativeMinutes += mins;

            IrPlaybookRunTask t = new IrPlaybookRunTask();
            t.setOrganisationId(org);
            t.setRunId(savedRun.getId());
            t.setPlaybookId(pb.getId());
            t.setStepId(s.getId());
            t.setStepOrder(s.getStepOrder());
            t.setPhase(s.getPhase());
            t.setTitleEn(s.getTitleEn());
            t.setTitleAr(s.getTitleAr());
            t.setInstructionsEn(s.getInstructionsEn());
            t.setInstructionsAr(s.getInstructionsAr());
            t.setOwnerRole((s.getResponsibleRole() != null && !s.getResponsibleRole().isBlank())
                ? s.getResponsibleRole() : DEFAULT_OWNER_ROLE);
            t.setStatus("PENDING");
            t.setIsCritical(Boolean.TRUE.equals(s.getIsCritical()));
            t.setEstimatedMinutes(mins);
            t.setDueAt(now.plusMinutes(cumulativeMinutes));      // the clock — cumulative from run start
            created.add(runTasks.save(t));
        }

        // Preserve the legacy invocation semantics the dashboard + F8 rules rely on.
        pb.setInvocationCount((pb.getInvocationCount() == null ? 0 : pb.getInvocationCount()) + 1);
        pb.setLastReviewedAt(now);
        playbooks.save(pb);

        audit.log("IR_PLAYBOOK_RUN_START", "IrPlaybookRun", savedRun.getId(),
            "Invoked playbook " + pb.getPlaybookCode() + " — " + created.size() + " tasks instantiated");

        // F8 — keep the existing IR_PLAYBOOK_INVOKED trigger (AR-119 / AR-128) firing,
        // now enriched with run context so downstream rules can reference the run.
        publisher.publishEvent(DomainEvent.of(org, "IR_PLAYBOOK_INVOKED", "IrPlaybook", pb.getId(),
            Map.of("playbookCode",    nz(pb.getPlaybookCode()),
                   "category",        nz(pb.getCategory()),
                   "severityTrigger", nz(pb.getSeverityTrigger()),
                   "invocationCount", pb.getInvocationCount() == null ? 0 : pb.getInvocationCount(),
                   "runId",           savedRun.getId(),
                   "taskCount",       created.size())));

        notifications.broadcastToRole(org, NOTIFY_ROLE,
            "IR playbook invoked — " + created.size() + " tasks",
            "تم تفعيل دليل استجابة — " + created.size() + " مهمة",
            "Playbook " + pb.getPlaybookCode() + " is now running with " + created.size()
                + " assigned, clocked tasks.",
            "IR_PLAYBOOK_RUN_STARTED", "IrPlaybookRun", savedRun.getId());

        return new IrPlaybookRunDetail(savedRun, created);
    }

    // ── Task transitions: work the run ────────────────────────────────────

    /** Mark a PENDING task IN_PROGRESS (optional convenience — completing works from any state). */
    @Transactional
    public IrPlaybookRunDetail startTask(Long runId, Long taskId) {
        Long org = TenantContext.getCurrentTenant();
        IrPlaybookRun run = requireRun(org, runId);
        IrPlaybookRunTask task = requireTaskOfRun(org, runId, taskId);
        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
            task.setStartedAt(LocalDateTime.now());
            runTasks.save(task);
            audit.log("IR_PLAYBOOK_TASK_START", "IrPlaybookRunTask", task.getId(),
                "Started task '" + task.getTitleEn() + "' in run " + runId);
        }
        return new IrPlaybookRunDetail(run, tasksOf(org, runId));
    }

    /** Complete a task and advance the run; the last completion flips the run to COMPLETED. */
    @Transactional
    public IrPlaybookRunDetail completeTask(Long runId, Long taskId, String note) {
        Long org = TenantContext.getCurrentTenant();
        IrPlaybookRun run = requireRun(org, runId);
        IrPlaybookRunTask task = requireTaskOfRun(org, runId, taskId);

        if (!"DONE".equals(task.getStatus())) {                 // idempotent — re-complete is a no-op
            task.setStatus("DONE");
            task.setCompletedAt(LocalDateTime.now());
            task.setCompletedBy(CurrentActor.username());
            if (note != null && !note.isBlank()) {
                String existing = task.getNotes() == null ? "" : task.getNotes() + "\n";
                task.setNotes(existing + note);
            }
            runTasks.save(task);
            audit.log("IR_PLAYBOOK_TASK_DONE", "IrPlaybookRunTask", task.getId(),
                "Completed task '" + task.getTitleEn() + "' in run " + runId);
        }

        // Recompute progress from the source of truth (resilient to double-clicks / races).
        long done = runTasks.countByOrganisationIdAndRunIdAndStatus(org, runId, "DONE");
        run.setCompletedTasks((int) done);
        boolean allDone = run.getTotalTasks() != null && done >= run.getTotalTasks();
        if (allDone && "RUNNING".equals(run.getStatus())) {
            run.setStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());
            audit.log("IR_PLAYBOOK_RUN_COMPLETED", "IrPlaybookRun", runId,
                "All " + run.getTotalTasks() + " tasks complete for playbook " + run.getPlaybookCode());
            publisher.publishEvent(DomainEvent.of(org, "IR_PLAYBOOK_RUN_COMPLETED", "IrPlaybookRun", runId,
                Map.of("playbookCode", nz(run.getPlaybookCode()),
                       "taskCount",    run.getTotalTasks())));
            notifications.broadcastToRole(org, NOTIFY_ROLE,
                "IR playbook run complete", "اكتمل تنفيذ دليل الاستجابة",
                "Run of " + run.getPlaybookCode() + " completed — all "
                    + run.getTotalTasks() + " tasks done.",
                "IR_PLAYBOOK_RUN_COMPLETED", "IrPlaybookRun", runId);
        }
        IrPlaybookRun savedRun = runs.save(run);
        return new IrPlaybookRunDetail(savedRun, tasksOf(org, runId));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private List<IrPlaybookRunTask> tasksOf(Long org, Long runId) {
        return runTasks.findByOrganisationIdAndRunIdOrderByStepOrderAsc(org, runId);
    }

    private IrPlaybookRun requireRun(Long org, Long runId) {
        return runs.findByIdAndOrganisationId(runId, org)
            .orElseThrow(() -> new EntityNotFoundException("IrPlaybookRun " + runId));
    }

    private IrPlaybookRunTask requireTaskOfRun(Long org, Long runId, Long taskId) {
        IrPlaybookRunTask task = runTasks.findByIdAndOrganisationId(taskId, org)
            .orElseThrow(() -> new EntityNotFoundException("IrPlaybookRunTask " + taskId));
        if (!runId.equals(task.getRunId())) {
            throw new IllegalArgumentException("Task " + taskId + " does not belong to run " + runId);
        }
        return task;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
