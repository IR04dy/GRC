package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.change_mgmt.spi.WorkflowPort;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * S8h / CE-10 — the change side of workflow-engine adoption: raise engine requests against a change
 * or one of its tasks, list them, and report which task owners the engine could actually reach.
 *
 * <h3>What this service is for, stated as narrowly as the measurement allows</h3>
 * G-10 is "inconsistent task/SLA/escalation behaviour", and the inconsistency is concrete:
 * {@code change_tasks} carries a due date and nothing else, while {@code workflow.requests} carries
 * sixteen routing/SLA/escalation columns that fourteen other modules already use. The fix is to send
 * change tasks through the engine, not to grow a second escalation ladder on {@code change_tasks}.
 *
 * <p>⚠ It is <b>not</b> for authorising changes. See {@link WorkflowPort} — the engine has no
 * callback into any source module and emits domain events only for SENT and ESCALATED, so an engine
 * request cannot move a change's status even in principle, and an {@code APPROVAL}-typed one would
 * put an approval-shaped artefact in the trail that S8f's register would still, correctly, count as
 * unauthorised. {@code raise} refuses that type outright.
 *
 * <h3>The addressability report is the part that was worth building</h3>
 * {@link #addressability()} answers a question nobody could ask before: of the tenant's open change
 * tasks, how many have an owner the platform can actually notify? Measured on dev at build time —
 * 41 tasks, 23 overdue, 14 distinct owner labels, <b>zero</b> resolving to a user and every matching
 * department empty — the honest answer is "none of them", which is why CH-5's overdue sweep has been
 * firing into a void. A report that only counted overdue tasks would have shown a tidy number and
 * hidden that.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeWorkflowService {

    /** Tasks whose owner still matters — a DONE task's unreachable owner is not a live problem. */
    public static final List<String> OPEN_TASK_STATUSES = List.of("PENDING", "IN_PROGRESS", "BLOCKED");

    private final ChangeRequestRepository changes;
    private final ChangeTaskRepository tasks;
    private final AuditLogService audit;
    private final Optional<WorkflowPort> workflow;

    // ── raising ──────────────────────────────────────────────────────────────

    /**
     * Raise an engine request about a change.
     *
     * @throws IllegalArgumentException from the port when the type is refused or the addressee
     *         reaches nobody; the message names which, so the caller can act on it
     * @throws IllegalStateException when the engine adapter is absent — a caller who asked to route
     *         work and got silence would reasonably assume it was routed
     *
     * <p>⚠ Not {@code @Transactional}, and a live probe is what established that. The engine's
     * {@code create} and {@code send} each persist the same {@code workflow.requests} row, so holding
     * one transaction across both flushes it twice against a ROWVERSION column and fails with "Row was
     * updated or deleted by another transaction" — landmine #2, in a new place. The reads here are
     * single queries and need no wrapper.
     */
    public WorkflowPort.RequestRef raiseForChange(Long changeId, String requestType,
                                                  String titleEn, String titleAr, String description,
                                                  WorkflowPort.Addressee addressee, LocalDate dueDate,
                                                  Integer slaHours, String priority, String escalationRole) {
        ChangeRequest c = requireChange(changeId);
        WorkflowPort port = requirePort();
        WorkflowPort.RequestRef ref = port.raise(WorkflowPort.Subject.CHANGE, c.getId(), requestType,
                titleEn, titleAr, description, addressee, dueDate, slaHours, priority, escalationRole)
            .orElseThrow(() -> new IllegalStateException("The workflow engine did not accept the request."));
        audit.log("CHANGE_ENGINE_REQUEST_RAISED", "ChangeRequest", c.getId(),
            ref.code() + " · " + ref.requestType() + " → " + describe(addressee));
        return ref;
    }

    /** Raise an engine request about one implementation task — the CE-10 case G-10 describes.
     *  Not {@code @Transactional}, for the ROWVERSION reason given on {@link #raiseForChange}. */
    public WorkflowPort.RequestRef raiseForTask(Long taskId, String requestType,
                                                String titleEn, String titleAr, String description,
                                                WorkflowPort.Addressee addressee, LocalDate dueDate,
                                                Integer slaHours, String priority, String escalationRole) {
        ChangeTask t = requireTask(taskId);
        WorkflowPort port = requirePort();
        // The task's own due date is the default, so routing a task does not quietly drop the date
        // somebody already set on it.
        LocalDate due = dueDate != null ? dueDate
            : (t.getDueAt() != null ? t.getDueAt().toLocalDate() : null);
        String en = titleEn != null && !titleEn.isBlank() ? titleEn : t.getTitleEn();
        String ar = titleAr != null && !titleAr.isBlank() ? titleAr : t.getTitleAr();
        WorkflowPort.RequestRef ref = port.raise(WorkflowPort.Subject.TASK, t.getId(), requestType,
                en, ar, description, addressee, due, slaHours, priority, escalationRole)
            .orElseThrow(() -> new IllegalStateException("The workflow engine did not accept the request."));
        audit.log("CHANGE_TASK_ROUTED_TO_ENGINE", "ChangeTask", t.getId(),
            ref.code() + " · change #" + t.getChangeRequestId() + " → " + describe(addressee));
        return ref;
    }

    // ── reads ────────────────────────────────────────────────────────────────

    /** Every engine request about this change or any of its tasks, the change's own first. */
    @Transactional(readOnly = true)
    public List<WorkflowPort.RequestRef> requestsFor(Long changeId) {
        ChangeRequest c = requireChange(changeId);
        if (workflow.isEmpty()) return List.of();
        List<Long> taskIds = tasks.findByChangeRequestIdOrderBySortOrderAscIdAsc(c.getId())
            .stream().map(ChangeTask::getId).filter(Objects::nonNull).toList();
        return workflow.get().raisedForChange(c.getId(), taskIds);
    }

    /** Addressees in this tenant that currently reach at least one active user. */
    @Transactional(readOnly = true)
    public List<WorkflowPort.Candidate> candidates() {
        return workflow.map(WorkflowPort::candidates).orElseGet(List::of);
    }

    /**
     * The tenant's open change tasks, each with a verdict on whether its owner can be notified.
     *
     * @param scope        always {@code OPEN_CHANGE_TASK_OWNERS} — states what the counts are over,
     *                     so "0 unreachable" cannot be read as a claim about DONE tasks too
     * @param unnamed      open tasks with no owner label at all: a distinct failure from an
     *                     unresolvable one, and not fixable by appointing anybody
     * @param byReason     unreachable counts per reason, so an empty department and a label that
     *                     matches nothing are not summed into one number
     * @param engineAbsent {@code true} when no adapter answered — every verdict is then unknown
     *                     rather than clean, and the payload says so instead of reporting zeros
     */
    public record Addressability(String scope, long openTasks, long overdueOpen, long unnamed,
                                 long reachable, long unreachable, Map<String, Long> byReason,
                                 List<OwnerVerdict> owners, boolean engineAbsent) { }

    /** One distinct owner label and what it resolves to, with the open tasks it carries. */
    public record OwnerVerdict(String label, String matchedType, String matchedLabel,
                               boolean reachable, String reason, int users,
                               long openTasks, long overdueTasks) { }

    @Transactional(readOnly = true)
    public Addressability addressability() {
        Long org = TenantContext.getCurrentTenant();
        List<ChangeTask> open = tasks.findOpenByOrganisation(org, OPEN_TASK_STATUSES);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // Count per label first, so one directory pass covers every task.
        Map<String, long[]> perLabel = new LinkedHashMap<>();   // label → [open, overdue]
        long unnamed = 0;
        for (ChangeTask t : open) {
            boolean overdue = t.getDueAt() != null && t.getDueAt().isBefore(now);
            String label = t.getOwnerName() == null ? "" : t.getOwnerName().trim();
            if (label.isEmpty()) { unnamed++; continue; }
            long[] n = perLabel.computeIfAbsent(label, k -> new long[2]);
            n[0]++;
            if (overdue) n[1]++;
        }

        long overdueOpen = open.stream()
            .filter(t -> t.getDueAt() != null && t.getDueAt().isBefore(now)).count();

        if (workflow.isEmpty()) {
            // Absent engine: report the population and say the verdicts are unknown. Filling
            // `reachable` with 0 would look like a measurement; it would be an absence.
            List<OwnerVerdict> unknown = perLabel.entrySet().stream()
                .map(e -> new OwnerVerdict(e.getKey(), null, null, false, "ENGINE_UNAVAILABLE", 0,
                    e.getValue()[0], e.getValue()[1]))
                .toList();
            return new Addressability("OPEN_CHANGE_TASK_OWNERS", open.size(), overdueOpen, unnamed,
                0, 0, Map.of(), unknown, true);
        }

        List<WorkflowPort.LabelMatch> matches = workflow.get().resolveLabels(List.copyOf(perLabel.keySet()));
        Map<String, WorkflowPort.LabelMatch> byLabel = new LinkedHashMap<>();
        matches.forEach(m -> byLabel.put(m.label(), m));

        List<OwnerVerdict> owners = new ArrayList<>();
        Map<String, Long> byReason = new LinkedHashMap<>();
        long reachable = 0, unreachable = 0;
        for (Map.Entry<String, long[]> e : perLabel.entrySet()) {
            WorkflowPort.LabelMatch m = byLabel.get(e.getKey());
            boolean ok = m != null && m.reach().reachable();
            String reason = m == null ? WorkflowPort.UNRESOLVED : m.reach().reason();
            owners.add(new OwnerVerdict(e.getKey(), m == null ? null : m.type(),
                m == null ? null : m.matchedLabel(), ok, reason,
                m == null ? 0 : m.reach().users(), e.getValue()[0], e.getValue()[1]));
            // Counted in TASKS, not labels: one unreachable label carrying nine overdue tasks is
            // nine tasks nobody was told about, and counting labels would report it as one.
            if (ok) reachable += e.getValue()[0];
            else {
                unreachable += e.getValue()[0];
                byReason.merge(reason, e.getValue()[0], Long::sum);
            }
        }
        owners.sort((a, b) -> Long.compare(b.openTasks(), a.openTasks()));
        return new Addressability("OPEN_CHANGE_TASK_OWNERS", open.size(), overdueOpen, unnamed,
            reachable, unreachable, byReason, owners, false);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WorkflowPort requirePort() {
        return workflow.orElseThrow(() -> new IllegalStateException(
            "The platform workflow engine is not available, so this request cannot be routed, "
          + "given an SLA, or escalated. Nothing was created."));
    }

    private ChangeRequest requireChange(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return changes.findById(id)
            .filter(c -> org != null && org.equals(c.getOrganisationId()))
            .orElseThrow(() -> new IllegalArgumentException("Change not found: " + id));
    }

    /** {@code change_tasks} has no organisation_id — the tenant comes from the parent change. */
    private ChangeTask requireTask(Long id) {
        ChangeTask t = tasks.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Change task not found: " + id));
        requireChange(t.getChangeRequestId());
        return t;
    }

    private static String describe(WorkflowPort.Addressee a) {
        if (a == null) return "(none)";
        String who = a.label() != null && !a.label().isBlank() ? a.label()
            : (a.key() != null && !a.key().isBlank() ? a.key() : String.valueOf(a.id()));
        return a.type() + " " + who;
    }
}
