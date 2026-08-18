package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import com.tss.grc.module.admin.committee.repository.CommitteeActionItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Action-item lifecycle service.
 *
 * <p>Lifecycle: OPEN → IN_PROGRESS → COMPLETED. Side branches:
 * CANCELLED (no longer relevant, Chair approves closure), DEFERRED
 * (date moved with Chair approval), OVERDUE (sweep flips this when
 * dueDate &lt; today and status is OPEN/IN_PROGRESS).
 *
 * <p>F8 events on every transition so downstream automation (next-
 * meeting agenda auto-populator, owner-manager escalation on overdue,
 * cross-module reverse-link updates) can react.
 */
@Service
@RequiredArgsConstructor
public class CommitteeActionItemService {

    /** F2-219 — the one state an action item may be CREATED in. IN_PROGRESS, COMPLETED, CANCELLED,
     *  DEFERRED and OVERDUE each belong to a route that records who moved it and why. */
    private static final String INITIAL_STATUS = "OPEN";

    private final CommitteeActionItemRepository repo;
    private final AuditLogService               audit;
    private final ApplicationEventPublisher     publisher;

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public Page<CommitteeActionItem> list(Pageable pageable) {
        try {
            return repo.findByOrganisationIdOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), pageable);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public CommitteeActionItem get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeActionItem " + id));
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> listByMeeting(Long meetingId) {
        try {
            return repo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(
                TenantContext.getCurrentTenant(), meetingId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> listByCommittee(Long committeeId) {
        try {
            return repo.findByOrganisationIdAndCommitteeIdOrderByDueDateAsc(
                TenantContext.getCurrentTenant(), committeeId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    /** Open actions for a committee — drives the "matters arising"
     *  section of the agenda auto-populator. */
    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> openActionsForCommittee(Long committeeId) {
        try {
            return repo.findOpenByCommittee(
                TenantContext.getCurrentTenant(), committeeId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeActionItem> openActionsForOwner(Long ownerUserId) {
        try {
            return repo.findOpenByOwner(
                TenantContext.getCurrentTenant(), ownerUserId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional
    public CommitteeActionItem create(CommitteeActionItem in) {
        // Mass-assignment defense. The controller accepts CommitteeActionItem
        // directly via @RequestBody, so a malicious client could submit
        // {"id": 12345, ...} — JPA's save() then routes through merge()
        // instead of persist(), SELECTs row 12345, copies the (now
        // tenant-scoped) fields onto it, and UPDATEs WHERE id=12345.
        // If row 12345 belongs to ANOTHER tenant, that row gets silently
        // overwritten with this caller's content. Force id to null so
        // save() always becomes a fresh INSERT.
        in.setId(null);
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        // ⚠⚠ F2-219 — `if (status == null) OPEN` is a DEFAULT, not a guard. Probed live: a create
        // naming status=COMPLETED answered 200 and stored it, so the action item never passed
        // complete(), which stamps completed_at, records the completion notes, writes the
        // ACTION_COMPLETE audit row and publishes the transition event the committee pack reads.
        // A governance action closed on arrival looks identical to one that was done.
        in.setStatus(ServerOwnedState.requireInitial(
            in.getStatus(), INITIAL_STATUS, "a committee action item",
            "POST /{id}/start, /complete, /cancel or /defer"));
        // The completion record belongs to complete(). Nothing has been completed on arrival, so
        // these three describe an act that did not happen. (The gate that found the status could
        // not see these: complete() has no state check, so it is not a "guarded route" — the reason
        // they are closed here is that they are the same claim, not that a scan demanded it.)
        in.setCompletedAt(null);
        in.setCompletedBy(null);
        in.setCompletionNotesEn(null);
        CommitteeActionItem saved = repo.save(in);

        audit.log("ACTION_CREATE", "CommitteeActionItem", saved.getId(),
            saved.getActionCode() + " · owner=" + saved.getOwnerName()
                + " · due=" + saved.getDueDate());

        publisher.publishEvent(DomainEvent.of(
            org, "COMMITTEE_ACTION_CREATED", "CommitteeActionItem", saved.getId(),
            Map.of("actionCode",   saved.getActionCode(),
                   "committeeId",  saved.getCommitteeId(),
                   "meetingId",    saved.getMeetingId(),
                   "ownerName",    saved.getOwnerName() == null ? "" : saved.getOwnerName(),
                   "dueDate",      saved.getDueDate() == null ? "" : saved.getDueDate().toString(),
                   "priority",     saved.getPriority(),
                   "linkedModule", saved.getLinkedModule() == null ? "" : saved.getLinkedModule(),
                   "linkedEntityCode", saved.getLinkedEntityCode() == null ? "" : saved.getLinkedEntityCode())));

        return saved;
    }

    /** Move OPEN → IN_PROGRESS. */
    @Transactional
    public CommitteeActionItem startProgress(Long id) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        if (!"OPEN".equals(prev) && !"OVERDUE".equals(prev)) {
            throw new IllegalStateException("Cannot start progress from status " + prev);
        }
        a.setStatus("IN_PROGRESS");
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_START", "CommitteeActionItem", id,
            "Status " + prev + " → IN_PROGRESS");
        publishTransition(saved, prev, "IN_PROGRESS");
        return saved;
    }

    /** Mark COMPLETED. */
    @Transactional
    public CommitteeActionItem complete(Long id, String completionNotesEn) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        a.setStatus("COMPLETED");
        a.setCompletedAt(LocalDateTime.now());
        // F2-236 — completedBy is the principal, never the request. It arrived as
        // `req.completedBy()`, and a live probe stored user id 9999 (which does not exist) as who
        // completed a committee action item, beside this server clock.
        a.setCompletedBy(CurrentActor.requireUserId("a committee action-item completion"));
        if (completionNotesEn != null) a.setCompletionNotesEn(completionNotesEn);
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_COMPLETE", "CommitteeActionItem", id,
            "Status " + prev + " → COMPLETED · notes=" + (completionNotesEn == null ? "—" : completionNotesEn));
        publishTransition(saved, prev, "COMPLETED");
        return saved;
    }

    /** Cancel — closure without completion. Requires Chair authority
     *  in practice; the service trusts caller permission. */
    @Transactional
    public CommitteeActionItem cancel(Long id, String reason) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        a.setStatus("CANCELLED");
        a.setCompletionNotesEn(reason);
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_CANCEL", "CommitteeActionItem", id,
            "Status " + prev + " → CANCELLED · reason=" + (reason == null ? "—" : reason));
        publishTransition(saved, prev, "CANCELLED");
        return saved;
    }

    /** Defer due-date — requires Chair authority in practice. */
    @Transactional
    public CommitteeActionItem defer(Long id, LocalDate newDueDate, String reason) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        LocalDate oldDue = a.getDueDate();
        a.setStatus("DEFERRED");
        a.setDueDate(newDueDate);
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_DEFER", "CommitteeActionItem", id,
            "Status " + prev + " → DEFERRED · " + oldDue + " → " + newDueDate
                + " · reason=" + (reason == null ? "—" : reason));
        publishTransition(saved, prev, "DEFERRED");
        return saved;
    }

    /** Called by the scheduled sweep when due_date has passed and status
     *  is still OPEN/IN_PROGRESS. */
    @Transactional
    public CommitteeActionItem markOverdue(Long id) {
        CommitteeActionItem a = get(id);
        String prev = a.getStatus();
        if ("COMPLETED".equals(prev) || "CANCELLED".equals(prev) || "OVERDUE".equals(prev)) {
            return a;
        }
        a.setStatus("OVERDUE");
        CommitteeActionItem saved = repo.save(a);
        audit.log("ACTION_OVERDUE", "CommitteeActionItem", id,
            "Status " + prev + " → OVERDUE · was due " + a.getDueDate());
        publishTransition(saved, prev, "OVERDUE");
        return saved;
    }

    /**
     * The only emitter of {@code COMMITTEE_ACTION_*}.
     *
     * <p>⚠ F2-188: {@code CommitteeScheduledSweep} used to flip a row to OVERDUE in place and emit
     * its own {@code COMMITTEE_ACTION_OVERDUE} with a <b>different payload</b> — {@code ownerName}
     * where this method sends {@code owner}, no {@code fromStatus}/{@code toStatus}, plus
     * {@code dueDate}/{@code linkedModule}/{@code linkedEntityCode} which this one lacked. Since the
     * sweep is the only thing that ever fires OVERDUE in production, the shape below was the one that
     * never occurred, and a rule keyed on {@code owner} would have matched nothing —
     * {@code AutomationRuleEngine} has already paid for that exact bug once (its javadoc records a
     * condition that compared {@code "" == "HIGH"} and so could never match, for any payload).
     *
     * <p>The payload is now the <b>union</b> of the two, so routing the sweep through here removes a
     * duplicate transition without dropping a key any consumer might have been written against. Both
     * {@code owner} and {@code ownerName} are emitted deliberately: four triggers have only ever sent
     * the first and one has only ever sent the second, and silently retiring either would be a
     * behaviour change to a notification path dressed as a refactor.
     */
    private void publishTransition(CommitteeActionItem a, String fromStatus, String toStatus) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("actionCode",       a.getActionCode());
        payload.put("committeeId",      a.getCommitteeId());
        payload.put("meetingId",        a.getMeetingId());
        payload.put("owner",            a.getOwnerName() == null ? "" : a.getOwnerName());
        payload.put("ownerName",        a.getOwnerName() == null ? "" : a.getOwnerName());
        payload.put("fromStatus",       fromStatus);
        payload.put("toStatus",         toStatus);
        payload.put("priority",         a.getPriority());
        payload.put("dueDate",          a.getDueDate() == null ? "" : a.getDueDate().toString());
        payload.put("linkedModule",     a.getLinkedModule() == null ? "" : a.getLinkedModule());
        payload.put("linkedEntityCode", a.getLinkedEntityCode() == null ? "" : a.getLinkedEntityCode());
        publisher.publishEvent(DomainEvent.of(
            a.getOrganisationId(),
            "COMMITTEE_ACTION_" + toStatus,
            "CommitteeActionItem", a.getId(),
            payload));
    }
}
